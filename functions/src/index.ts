import { createHash, randomBytes, randomInt, timingSafeEqual } from "crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore, Timestamp, type DocumentReference, type DocumentSnapshot } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { logger } from "firebase-functions/v2";
import { sendOtpEmail } from "./emailOtpMailer";
import {
  isTesterParentEmail,
  TESTER_PARENT_ACCOUNTS,
  TESTER_STATIC_OTP,
} from "./testerAccounts";

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

const db = getFirestore();
db.settings({ ignoreUndefinedProperties: true });
const auth = getAuth();
const messaging = getMessaging();

/** Bound only to sendEmailOtp — never logged, never committed to git. */
const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
/**
 * Production sender on the verified Resend domain (anajyo.com).
 * Override at deploy time with `--set-env-vars` / params if the domain changes.
 */
const RESEND_FROM_EMAIL = defineString("RESEND_FROM_EMAIL", {
  default: "MeritScreen <noreply@anajyo.com>",
  description: "Verified Resend From address for parent email OTP",
});

const PAIRING_TTL_MS = 10 * 60 * 1000;
const PAIRING_CODE_LENGTH = 6;
const MAX_CONSUME_ATTEMPTS = 8;
const CONSUME_RATE_WINDOW_MS = 10 * 60 * 1000;
const CONSUME_RATE_MAX = 20;
const PAIRING_MINT_RATE_WINDOW_MS = 10 * 60 * 1000;
const PAIRING_MINT_RATE_MAX = 10;
const DELETE_FAMILY_RATE_WINDOW_MS = 60 * 60 * 1000;
const DELETE_FAMILY_RATE_MAX = 1;
const DELETE_CHILD_RATE_WINDOW_MS = 10 * 60 * 1000;
const DELETE_CHILD_RATE_MAX = 10;
const CHILD_DEVICE_UID_PREFIX = "dev_";

const EMAIL_OTP_TTL_MS = 10 * 60 * 1000;
const EMAIL_OTP_LENGTH = 6;
const MAX_OTP_VERIFY_ATTEMPTS = 5;
const EMAIL_OTP_SEND_RATE_WINDOW_MS = 10 * 60 * 1000;
const EMAIL_OTP_SEND_RATE_MAX = 5;
const EMAIL_OTP_IP_RATE_MAX = 15;
/** Matches AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS on the client. */
const EMAIL_OTP_RESEND_COOLDOWN_MS = 42 * 1000;

/**
 * App Check enforcement is off until a release keystore SHA-256 is registered in the
 * Firebase console. Set Functions env `APP_CHECK_ENFORCE=true` (and enable enforce in
 * the console for Firestore/Storage) to turn it on without a code change.
 */
const ENFORCE_APP_CHECK = process.env.APP_CHECK_ENFORCE === "true";
const CALLABLE_OPTS = { enforceAppCheck: ENFORCE_APP_CHECK } as const;

type CallableRequest = {
  auth?: { uid: string; token: Record<string, unknown> };
  data: unknown;
  rawRequest?: { ip?: string };
};

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  return value.trim();
}

function hashSecret(secret: string): string {
  return createHash("sha256").update(secret).digest("hex");
}

function secretsEqual(leftHex: string, rightHex: string): boolean {
  const left = Buffer.from(leftHex, "hex");
  const right = Buffer.from(rightHex, "hex");
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

function generateCode(): string {
  return randomInt(0, 10 ** PAIRING_CODE_LENGTH)
    .toString()
    .padStart(PAIRING_CODE_LENGTH, "0");
}

function isChildDevice(request: CallableRequest): boolean {
  return request.auth?.token?.role === "child_device";
}

async function assertParentOfChild(uid: string, childId: string): Promise<{ familyId: string }> {
  const userSnap = await db.collection("users").doc(uid).get();
  const familyId = userSnap.get("familyId") as string | undefined;
  if (!familyId) {
    throw new HttpsError("failed-precondition", "Create a family before pairing a device.");
  }
  const memberSnap = await db.collection("families").doc(familyId).collection("members").doc(uid).get();
  if (!memberSnap.exists) {
    throw new HttpsError("permission-denied", "You can only pair devices for your own family.");
  }
  const childSnap = await db.collection("families").doc(familyId).collection("children").doc(childId).get();
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that child profile.");
  }
  return { familyId };
}

/**
 * Parent-authenticated. Mints a 10-minute, one-time 6-digit code plus a high-entropy
 * secret for the QR payload. Previous unused codes for the same child are replaced.
 */
export const createPairingToken = onCall(CALLABLE_OPTS, async (request) => {
  const typed = request as CallableRequest;
  if (!typed.auth) {
    throw new HttpsError("unauthenticated", "Sign in as a parent to create a pairing code.");
  }
  if (isChildDevice(typed)) {
    throw new HttpsError("permission-denied", "Child devices cannot mint pairing codes.");
  }
  await enforcePairingMintRate(typed.auth.uid);
  const childId = requireString(asRecord(typed.data).childId, "childId");
  const { familyId } = await assertParentOfChild(typed.auth.uid, childId);

  const childRef = db.collection("families").doc(familyId).collection("children").doc(childId);
  const previousCode = (await childRef.get()).get("activePairingCode") as string | undefined;
  if (previousCode) {
    await db.collection("pairingCodes").doc(previousCode).delete().catch(() => undefined);
  }

  let code = generateCode();
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const existing = await db.collection("pairingCodes").doc(code).get();
    if (!existing.exists) break;
    const expiresAt = existing.get("expiresAt") as Timestamp | undefined;
    const usedAt = existing.get("usedAt") as Timestamp | undefined;
    if (usedAt || (expiresAt && expiresAt.toMillis() < Date.now())) {
      break;
    }
    code = generateCode();
  }

  const secret = randomBytes(32).toString("hex");
  const expiresAt = Timestamp.fromMillis(Date.now() + PAIRING_TTL_MS);
  await db.collection("pairingCodes").doc(code).set({
    secretHash: hashSecret(secret),
    familyId,
    childId,
    createdByUid: typed.auth.uid,
    expiresAt,
    usedAt: null,
    failedAttempts: 0,
    createdAt: FieldValue.serverTimestamp(),
  });
  await childRef.set({ activePairingCode: code, pairingUpdatedAt: FieldValue.serverTimestamp() }, { merge: true });

  const qrPayload = `meritscreen://pair?c=${code}&s=${secret}`;
  return {
    code,
    secret,
    expiresAtEpochMs: expiresAt.toMillis(),
    qrPayload,
    childId,
    familyId,
  };
});

async function enforceConsumeRate(ip: string): Promise<void> {
  await enforceWindowRate("consumeRate", ip.replace(/\//g, "_") || "unknown", CONSUME_RATE_WINDOW_MS, CONSUME_RATE_MAX);
}

async function enforcePairingMintRate(uid: string): Promise<void> {
  await enforceWindowRate("pairingMintRate", uid, PAIRING_MINT_RATE_WINDOW_MS, PAIRING_MINT_RATE_MAX);
}

async function enforceDeleteFamilyRate(uid: string): Promise<void> {
  await enforceWindowRate("deleteRate", uid, DELETE_FAMILY_RATE_WINDOW_MS, DELETE_FAMILY_RATE_MAX);
}

async function enforceDeleteChildRate(uid: string): Promise<void> {
  await enforceWindowRate("deleteChildRate", uid, DELETE_CHILD_RATE_WINDOW_MS, DELETE_CHILD_RATE_MAX);
}

/** Wipe one child's Firestore subtree + Auth device users + pairing code; optionally FCM-notify. */
async function wipeChildTree(
  familyId: string,
  childRef: DocumentReference,
  options: { notifyDevices: boolean },
): Promise<void> {
  if (options.notifyDevices) {
    await pushToChildDevices(familyId, childRef.id, "family_deleted").catch((error) =>
      logger.warn("child_deleted push failed", error),
    );
  }

  const childSnap = await childRef.get();
  const activePairingCode = childSnap.get("activePairingCode") as string | undefined;
  if (activePairingCode) {
    await db.collection("pairingCodes").doc(activePairingCode).delete().catch(() => undefined);
  }

  const devicesSnap = await childRef.collection("devices").get();
  for (const deviceDoc of devicesSnap.docs) {
    const authUid = `${CHILD_DEVICE_UID_PREFIX}${deviceDoc.id}`.slice(0, 128);
    await auth.deleteUser(authUid).catch(() => undefined);
  }

  const subcollections = [
    "policy",
    "appRules",
    "devices",
    "usageDays",
    "quizAttempts",
    "skillState",
    "quizPacks",
  ];
  for (const name of subcollections) {
    const docs = await childRef.collection(name).listDocuments();
    const batchSize = 400;
    for (let i = 0; i < docs.length; i += batchSize) {
      const batch = db.batch();
      docs.slice(i, i + batchSize).forEach((doc) => batch.delete(doc));
      await batch.commit();
    }
  }
  await childRef.delete();
}

/**
 * Owner-authenticated. Deletes one child profile and all nested data (devices, policy,
 * usage, quiz). Pushes `family_deleted` so that child's paired devices wipe local Room.
 */
export const deleteChild = onCall(CALLABLE_OPTS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in as a parent to delete a child.");
  }
  if (isChildDevice(request as CallableRequest)) {
    throw new HttpsError("permission-denied", "Child devices cannot delete a child.");
  }
  const data = asRecord(request.data);
  const familyId = requireString(data.familyId, "familyId");
  const childId = requireString(data.childId, "childId");
  const uid = request.auth.uid;
  await enforceDeleteChildRate(uid);

  const familyRef = db.collection("families").doc(familyId);
  const familySnap = await familyRef.get();
  if (!familySnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that family.");
  }
  if (familySnap.get("ownerUid") !== uid) {
    throw new HttpsError("permission-denied", "Only the family owner can delete a child.");
  }

  const childRef = familyRef.collection("children").doc(childId);
  const childSnap = await childRef.get();
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that child.");
  }

  await wipeChildTree(familyId, childRef, { notifyDevices: true });
  return { ok: true };
});

/**
 * Owner-authenticated. Recursively deletes the family tree (children, devices, policy,
 * appRules, usage, quiz data), pairing codes, child Auth users, and clears
 * users/{uid}.familyId for members. Pushes `family_deleted` so paired devices wipe local Room.
 */
export const deleteFamily = onCall(CALLABLE_OPTS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in as a parent to delete a family.");
  }
  if (isChildDevice(request as CallableRequest)) {
    throw new HttpsError("permission-denied", "Child devices cannot delete a family.");
  }
  const data = asRecord(request.data);
  const familyId = requireString(data.familyId, "familyId");
  const uid = request.auth.uid;
  await enforceDeleteFamilyRate(uid);

  const familyRef = db.collection("families").doc(familyId);
  const familySnap = await familyRef.get();
  if (!familySnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that family.");
  }
  if (familySnap.get("ownerUid") !== uid) {
    throw new HttpsError("permission-denied", "Only the family owner can delete it.");
  }

  const membersSnap = await familyRef.collection("members").get();
  const childrenSnap = await familyRef.collection("children").get();

  for (const childDoc of childrenSnap.docs) {
    await wipeChildTree(familyId, childDoc.ref, { notifyDevices: true });
  }

  const memberBatch = db.batch();
  for (const member of membersSnap.docs) {
    memberBatch.delete(member.ref);
    const userRef = db.collection("users").doc(member.id);
    memberBatch.set(userRef, { familyId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
  await memberBatch.commit();
  await familyRef.delete();

  return { ok: true };
});


async function enforceWindowRate(
  collection: string,
  docId: string,
  windowMs: number,
  max: number,
): Promise<void> {
  const ref = db.collection(collection).doc(docId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const now = Date.now();
    const windowStart = (snap.get("windowStartMs") as number | undefined) ?? now;
    const count = (snap.get("count") as number | undefined) ?? 0;
    if (now - windowStart > windowMs) {
      tx.set(ref, { windowStartMs: now, count: 1, updatedAt: FieldValue.serverTimestamp() });
      return;
    }
    if (count >= max) {
      throw new HttpsError("resource-exhausted", "Too many attempts. Please wait a minute and try again.");
    }
    tx.set(ref, { windowStartMs: windowStart, count: count + 1, updatedAt: FieldValue.serverTimestamp() });
  });
}

/**
 * Unauthenticated (child device). Verifies a one-time code, mints a custom token, then
 * binds the device. Token creation runs **before** marking the code used / writing the
 * device doc so a missing IAM `signBlob` permission cannot leave the parent UI showing
 * "paired" while the child still has no Auth session.
 *
 * If a previous attempt already wrote `devices/{deviceId}` but failed before returning
 * the token, the same device may resume by code (or by already-bound device id).
 */
export const consumePairingToken = onCall(CALLABLE_OPTS, async (request) => {
  const typed = request as CallableRequest;
  const data = asRecord(typed.data);
  const code = requireString(data.code, "code").replace(/\s/g, "");
  if (!/^\d{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Enter the 6-digit pairing code.");
  }
  const deviceId = requireString(data.deviceId, "deviceId").replace(/[^a-zA-Z0-9]/g, "");
  if (deviceId.length < 8) {
    throw new HttpsError("invalid-argument", "Device id is invalid.");
  }
  const secret = typeof data.secret === "string" && data.secret.length > 0 ? data.secret : null;
  const ip = typed.rawRequest?.ip ?? "unknown";
  await enforceConsumeRate(ip);

  const codeRef = db.collection("pairingCodes").doc(code);
  const codeSnap = await codeRef.get();
  if (!codeSnap.exists) {
    throw new HttpsError("not-found", "That pairing code is incorrect or has expired.");
  }

  const familyId = codeSnap.get("familyId") as string;
  const childId = codeSnap.get("childId") as string;
  const childRef = db.collection("families").doc(familyId).collection("children").doc(childId);
  const deviceRef = childRef.collection("devices").doc(deviceId);
  const existingDevice = await deviceRef.get();
  const usedAt = codeSnap.get("usedAt") as Timestamp | null;

  // Resume: code already consumed but this device was written — remint Auth without
  // requiring a fresh parent code (recovers from createCustomToken IAM failures).
  if (usedAt && existingDevice.exists && existingDevice.get("revoked") !== true) {
    return mintChildPairingResponse({ familyId, childId, deviceId, childRef });
  }

  if (usedAt) {
    throw new HttpsError("failed-precondition", "That pairing code was already used. Ask a parent for a new one.");
  }
  const expiresAt = codeSnap.get("expiresAt") as Timestamp | undefined;
  if (!expiresAt || expiresAt.toMillis() < Date.now()) {
    throw new HttpsError("failed-precondition", "That pairing code has expired. Ask a parent to refresh it.");
  }
  const failedAttempts = (codeSnap.get("failedAttempts") as number | undefined) ?? 0;
  if (failedAttempts >= MAX_CONSUME_ATTEMPTS) {
    throw new HttpsError("resource-exhausted", "Too many attempts. Ask a parent to refresh the code.");
  }
  const storedHash = codeSnap.get("secretHash") as string | undefined;
  if (secret && storedHash && !secretsEqual(storedHash, hashSecret(secret))) {
    await codeRef.update({ failedAttempts: failedAttempts + 1 });
    throw new HttpsError("not-found", "That pairing code is incorrect or has expired.");
  }

  const childSnap = await childRef.get();
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that child profile.");
  }

  // Mint Auth first — any IAM failure must happen before the code is burned.
  const response = await mintChildPairingResponse({
    familyId,
    childId,
    deviceId,
    childRef,
    childSnap,
  });

  await db.runTransaction(async (tx) => {
    const fresh = await tx.get(codeRef);
    if (!fresh.exists) {
      throw new HttpsError("not-found", "That pairing code is incorrect or has expired.");
    }
    const freshUsed = fresh.get("usedAt") as Timestamp | null;
    if (freshUsed) {
      // Another consumer won the race; only OK if it was this device.
      return;
    }
    tx.update(codeRef, { usedAt: FieldValue.serverTimestamp(), failedAttempts: 0 });
    tx.set(
      deviceRef,
      {
        platform: "android",
        revoked: false,
        pairedAt: FieldValue.serverTimestamp(),
        lastSeenAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    tx.set(childRef, { activePairingCode: FieldValue.delete() }, { merge: true });
  });

  // Ensure device doc exists even if the transaction saw a concurrent consume.
  await deviceRef.set(
    {
      platform: "android",
      revoked: false,
      pairedAt: FieldValue.serverTimestamp(),
      lastSeenAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );

  return response;
});

async function mintChildPairingResponse(args: {
  familyId: string;
  childId: string;
  deviceId: string;
  childRef: DocumentReference;
  childSnap?: DocumentSnapshot;
}): Promise<Record<string, string>> {
  const childSnap = args.childSnap ?? (await args.childRef.get());
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "We couldn't find that child profile.");
  }
  const familySnap = await db.collection("families").doc(args.familyId).get();
  const parentPinHash = (familySnap.get("parentPinHash") as string | undefined) ?? "";
  const uid = `${CHILD_DEVICE_UID_PREFIX}${args.deviceId}`.slice(0, 128);
  let customToken: string;
  try {
    customToken = await auth.createCustomToken(uid, {
      role: "child_device",
      familyId: args.familyId,
      childId: args.childId,
      deviceId: args.deviceId,
    });
  } catch (error) {
    logger.error("createCustomToken failed", error);
    throw new HttpsError(
      "internal",
      "Could not finish pairing on this device. Ask a parent for a new code, or try again in a minute.",
    );
  }
  return {
    customToken,
    familyId: args.familyId,
    childId: args.childId,
    deviceId: args.deviceId,
    parentPinHash,
    displayName: (childSnap.get("displayName") as string | undefined) ?? "",
    ageBand: (childSnap.get("ageBand") as string | undefined) ?? "",
    avatarId: (childSnap.get("avatarId") as string | undefined) ?? "",
  };
}

/**
 * Sends a data-only (no `notification` payload) high-priority FCM message to every
 * non-revoked device with a registered token for this child. Data-only messages reach
 * `onMessageReceived` on Android even while the app is backgrounded — the client hands off
 * to a bounded WorkManager job rather than doing any work on the message thread (Phase 7).
 * Invalid/unregistered tokens are pruned so the device list does not accumulate dead tokens.
 */
async function pushToChildDevices(
  familyId: string,
  childId: string,
  type: string,
  onlyDeviceId?: string,
): Promise<void> {
  const devicesSnap = await db
    .collection("families")
    .doc(familyId)
    .collection("children")
    .doc(childId)
    .collection("devices")
    .get();

  const targets = devicesSnap.docs.filter((doc) => {
    if (doc.get("revoked") === true) return false;
    if (onlyDeviceId && doc.id !== onlyDeviceId) return false;
    return typeof doc.get("fcmToken") === "string" && doc.get("fcmToken").length > 0;
  });
  if (targets.length === 0) return;

  const response = await messaging.sendEachForMulticast({
    tokens: targets.map((doc) => doc.get("fcmToken") as string),
    data: { type, familyId, childId },
    android: { priority: "high" },
  });

  const staleTokenDocs = response.responses
    .map((result, i) => ({ result, doc: targets[i] }))
    .filter(({ result }) => {
      const code = (result.error as { code?: string } | undefined)?.code;
      return code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token";
    })
    .map(({ doc }) => doc.ref);
  await Promise.all(staleTokenDocs.map((ref) => ref.update({ fcmToken: FieldValue.delete() })));
}

/**
 * Unauthenticated (parent onboarding / sign-in).
 * Mints a 6-digit code, stores only the SHA-256 hash, and delivers via Resend.
 * Allowlisted closed-tester emails plant a fixed OTP and skip Resend (see testerAccounts.ts).
 */
export const sendEmailOtp = onCall(
  { ...CALLABLE_OPTS, secrets: [RESEND_API_KEY] },
  async (request) => {
    const typed = request as CallableRequest;
    const data = asRecord(typed.data);
    const rawEmail = requireString(data.email, "email");
    const email = normalizeEmail(rawEmail);
    if (!isValidEmail(email)) {
      throw new HttpsError("invalid-argument", "Please enter a valid email address.");
    }

    const isTester = isTesterParentEmail(email);
    const ip = typed.rawRequest?.ip ?? "unknown";
    const emailRateId = email.replace(/[^a-zA-Z0-9_]/g, "_");

    // Testers share devices / Wi-Fi — skip aggressive rate limits so QA is not blocked.
    if (!isTester) {
      await Promise.all([
        enforceWindowRate(
          "emailOtpIpRate",
          ip.replace(/\//g, "_") || "unknown",
          EMAIL_OTP_SEND_RATE_WINDOW_MS,
          EMAIL_OTP_IP_RATE_MAX,
        ),
        enforceWindowRate(
          "emailOtpSendRate",
          emailRateId,
          EMAIL_OTP_SEND_RATE_WINDOW_MS,
          EMAIL_OTP_SEND_RATE_MAX,
        ),
      ]);
    }

    const otpRef = db.collection("emailOtpCodes").doc(email);
    if (!isTester) {
      await enforceOtpResendCooldown(otpRef);
    }

    const code = isTester
      ? TESTER_STATIC_OTP
      : randomInt(0, 10 ** EMAIL_OTP_LENGTH)
          .toString()
          .padStart(EMAIL_OTP_LENGTH, "0");
    const codeHash = hashSecret(code);
    const nowMs = Date.now();
    // Long TTL for testers so a demo can pause without re-requesting.
    const ttlMs = isTester ? 24 * 60 * 60 * 1000 : EMAIL_OTP_TTL_MS;
    const expiresAt = Timestamp.fromMillis(nowMs + ttlMs);

    // Persist hash before send so a crash mid-flight cannot leave an email without a
    // verifiable code. On Resend failure we delete so the parent can retry cleanly.
    await otpRef.set({
      email,
      codeHash,
      expiresAt,
      attempts: 0,
      lastSentAtMs: nowMs,
      createdAt: FieldValue.serverTimestamp(),
      ...(isTester ? { testerStaticOtp: true } : {}),
    });

    if (isTester) {
      logger.info("[Email OTP] Tester static OTP planted (Resend skipped)", {
        toDomain: email.includes("@") ? email.split("@")[1] : "unknown",
      });
      return { success: true, expiresInSeconds: Math.floor(ttlMs / 1000) };
    }

    try {
      await sendOtpEmail({
        apiKey: RESEND_API_KEY.value(),
        from: RESEND_FROM_EMAIL.value(),
        to: email,
        code,
        idempotencyKey: `email-otp/${email}/${codeHash.slice(0, 24)}`,
      });
    } catch (error) {
      await otpRef.delete().catch(() => undefined);
      throw error;
    }

    return { success: true, expiresInSeconds: Math.floor(EMAIL_OTP_TTL_MS / 1000) };
  },
);

function normalizeEmail(raw: string): string {
  return raw.toLowerCase().trim();
}

function isValidEmail(email: string): boolean {
  return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email);
}

async function enforceOtpResendCooldown(otpRef: DocumentReference): Promise<void> {
  const existing = await otpRef.get();
  if (!existing.exists) return;
  const lastSentAtMs = existing.get("lastSentAtMs") as number | undefined;
  if (typeof lastSentAtMs !== "number") return;
  const elapsed = Date.now() - lastSentAtMs;
  if (elapsed < EMAIL_OTP_RESEND_COOLDOWN_MS) {
    const retryAfterSeconds = Math.ceil((EMAIL_OTP_RESEND_COOLDOWN_MS - elapsed) / 1000);
    throw new HttpsError(
      "resource-exhausted",
      `Please wait ${retryAfterSeconds}s before requesting another code.`,
    );
  }
}

/**
 * Unauthenticated (parent onboarding / sign-in).
 * Verifies the 6-digit OTP code sent to the email address.
 * On success, finds or creates the user in Firebase Auth and returns a custom token with role "parent".
 * Allowlisted testers may use the fixed OTP even if "Send code" was skipped / expired.
 */
export const verifyEmailOtp = onCall(CALLABLE_OPTS, async (request) => {
  const typed = request as CallableRequest;
  const data = asRecord(typed.data);
  const rawEmail = requireString(data.email, "email");
  const email = normalizeEmail(rawEmail);
  const rawCode = requireString(data.code, "code").replace(/\s/g, "");

  if (!new RegExp(`^\\d{${EMAIL_OTP_LENGTH}}$`).test(rawCode)) {
    throw new HttpsError("invalid-argument", "Enter the 6-digit verification code.");
  }

  const isTester = isTesterParentEmail(email);
  const testerCodeOk = isTester && secretsEqual(hashSecret(rawCode), hashSecret(TESTER_STATIC_OTP));

  const otpRef = db.collection("emailOtpCodes").doc(email);
  const snap = await otpRef.get();

  if (!testerCodeOk) {
    if (!snap.exists) {
      throw new HttpsError("not-found", "No verification code was requested for this email, or it has expired.");
    }

    const expiresAt = snap.get("expiresAt") as Timestamp | undefined;
    if (!expiresAt || expiresAt.toMillis() < Date.now()) {
      throw new HttpsError("failed-precondition", "The verification code has expired. Please request a new code.");
    }

    const attempts = (snap.get("attempts") as number | undefined) ?? 0;
    if (attempts >= MAX_OTP_VERIFY_ATTEMPTS) {
      throw new HttpsError("resource-exhausted", "Too many incorrect attempts. Please request a new verification code.");
    }

    const storedHash = snap.get("codeHash") as string | undefined;
    if (!storedHash || !secretsEqual(storedHash, hashSecret(rawCode))) {
      await otpRef.update({ attempts: attempts + 1 });
      throw new HttpsError("invalid-argument", "That verification code is incorrect. Check your email and try again.");
    }
  }

  // Code verified — delete OTP so a non-tester code cannot be reused.
  await otpRef.delete().catch(() => undefined);

  // Retrieve or create Firebase Auth user
  let user;
  try {
    user = await auth.getUserByEmail(email);
    if (!user.emailVerified) {
      await auth.updateUser(user.uid, { emailVerified: true });
    }
  } catch (err: any) {
    if (err?.code === "auth/user-not-found") {
      const displayName = isTester
        ? TESTER_PARENT_ACCOUNTS.find((a) => a.email === email)?.displayName
        : undefined;
      user = await auth.createUser({
        email,
        emailVerified: true,
        ...(displayName ? { displayName } : {}),
      });
    } else {
      logger.error("Failed to query/create user in verifyEmailOtp", err);
      throw new HttpsError("internal", "Could not complete sign-in. Please try again.");
    }
  }

  // Ensure parent claim
  const currentClaims = user.customClaims || {};
  if (currentClaims.role !== "parent") {
    await auth.setCustomUserClaims(user.uid, { ...currentClaims, role: "parent" });
  }

  // Lightweight users/{uid} profile for admin / analytics (merge — never wipe familyId).
  const userRef = db.collection("users").doc(user.uid);
  const existingUser = await userRef.get().catch(() => null);
  await userRef
    .set(
      {
        email,
        role: "parent",
        status: "active",
        emailVerified: true,
        ...(isTester ? { isTesterAccount: true } : {}),
        updatedAt: FieldValue.serverTimestamp(),
        ...(!existingUser?.exists ? { createdAt: FieldValue.serverTimestamp() } : {}),
      },
      { merge: true },
    )
    .catch((error) => {
      logger.warn("users profile merge failed after OTP verify", error);
    });

  const customToken = await auth.createCustomToken(user.uid, { role: "parent" });
  return {
    customToken,
    email,
  };
});

export {
  onPolicyChanged,
  onAppRuleChanged,
  onDeviceRevoked,
  onParentPinChanged,
} from "./firestoreTriggers";

// Super Admin Panel Functions
export {
  adminGetDashboardStats,
  adminGetAnalyticsSeries,
} from "./admin/adminStats";

export {
  adminListUsers,
  adminGetUser,
  adminSetUserStatus,
} from "./admin/adminUsers";

export {
  adminListFamilies,
  adminGetFamilyTree,
  adminSetFamilyStatus,
  adminDeleteFamily,
} from "./admin/adminFamilies";

export {
  adminListChildren,
  adminDeleteChild,
  adminListDevices,
  adminRevokeDevice,
} from "./admin/adminChildrenDevices";

export {
  adminStartExport,
  adminListAuditLogs,
  adminListOperators,
  adminSetOperator,
} from "./admin/adminExportsAudit";

// Push Notification Engine & Campaigns
export { onNotificationQueued } from "./notifications/notificationQueue";
export {
  adminSendCampaign,
  adminListCampaigns,
} from "./notifications/adminCampaigns";
export {
  onChildDevicePaired,
  onQuizAttemptLogged,
  onChildDailyUsageLogged,
} from "./notifications/familyNotificationTriggers";


