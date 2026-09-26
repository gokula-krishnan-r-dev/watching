import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import {
  NOTIFICATION_CHANNELS,
  type NotificationChannelId,
  type NotificationPayload,
  type ParentNotificationPrefs,
} from "./notificationTypes";

const db = getFirestore();
const messaging = getMessaging();

/** Max tokens allowed per sendEachForMulticast call by FCM */
const FCM_MAX_BATCH_SIZE = 500;

/** Default primary accent brand color (#0F6B66) for Watching */
const WATCHING_BRAND_COLOR = "#0F6B66";

export interface BatchSendResult {
  totalSent: number;
  successCount: number;
  failureCount: number;
  invalidTokens: string[];
}

/**
 * Sends a notification payload to an array of FCM registration tokens in
 * batches of up to 500, pruning invalid/unregistered tokens automatically.
 */
export async function sendMulticastChunks(
  tokens: string[],
  payload: NotificationPayload,
  channelId: NotificationChannelId = NOTIFICATION_CHANNELS.PROMOTIONS,
): Promise<BatchSendResult> {
  const uniqueTokens = Array.from(new Set(tokens.filter((t) => typeof t === "string" && t.trim().length > 0)));
  if (uniqueTokens.length === 0) {
    return { totalSent: 0, successCount: 0, failureCount: 0, invalidTokens: [] };
  }

  let totalSuccess = 0;
  let totalFailure = 0;
  const invalidTokens: string[] = [];

  for (let i = 0; i < uniqueTokens.length; i += FCM_MAX_BATCH_SIZE) {
    const chunk = uniqueTokens.slice(i, i + FCM_MAX_BATCH_SIZE);

    // Build data dictionary (all values must be strings)
    const dataMap: Record<string, string> = {};
    for (const [k, v] of Object.entries(payload)) {
      if (v !== undefined && v !== null) {
        dataMap[k] = String(v);
      }
    }

    const message: MulticastMessage = {
      tokens: chunk,
      data: dataMap,
      android: {
        priority: payload.title ? "high" : "high",
        notification: payload.title
          ? {
              channelId,
              color: WATCHING_BRAND_COLOR,
              defaultSound: true,
              defaultVibrateTimings: true,
              imageUrl: payload.imageUrl || undefined,
            }
          : undefined,
      },
    };

    if (payload.title || payload.body) {
      message.notification = {
        title: payload.title || "Watching",
        body: payload.body || "",
        imageUrl: payload.imageUrl || undefined,
      };
    }

    try {
      const response = await messaging.sendEachForMulticast(message);
      totalSuccess += response.successCount;
      totalFailure += response.failureCount;

      response.responses.forEach((res, index) => {
        if (!res.success && res.error) {
          const code = (res.error as { code?: string }).code;
          if (
            code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token"
          ) {
            invalidTokens.push(chunk[index]);
          }
        }
      });
    } catch (err) {
      logger.error("Error sending FCM multicast chunk", { error: err, chunkIndex: i });
      totalFailure += chunk.length;
    }
  }

  // Prune any discovered dead tokens in the background
  if (invalidTokens.length > 0) {
    pruneInvalidTokens(invalidTokens).catch((err) =>
      logger.warn("Failed to prune invalid tokens", { error: err, count: invalidTokens.length }),
    );
  }

  return {
    totalSent: uniqueTokens.length,
    successCount: totalSuccess,
    failureCount: totalFailure,
    invalidTokens,
  };
}

/**
 * Deletes invalid/unregistered tokens from fcmTokens, parentDevices, and child devices.
 */
export async function pruneInvalidTokens(tokens: string[]): Promise<void> {
  if (tokens.length === 0) return;

  // 1. Prune from global fcmTokens collection
  const fcmSnap = await db
    .collection("fcmTokens")
    .where("token", "in", tokens.slice(0, 30))
    .get();
  const batch = db.batch();
  fcmSnap.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit().catch(() => undefined);

  // 2. Query devices / parentDevices that match these tokens (batched limit)
  for (const token of tokens) {
    try {
      const parentDevicesSnap = await db
        .collectionGroup("parentDevices")
        .where("fcmToken", "==", token)
        .limit(10)
        .get();
      for (const d of parentDevicesSnap.docs) {
        await d.ref.update({ fcmToken: FieldValue.delete() }).catch(() => undefined);
      }

      const childDevicesSnap = await db
        .collectionGroup("devices")
        .where("fcmToken", "==", token)
        .limit(10)
        .get();
      for (const d of childDevicesSnap.docs) {
        await d.ref.update({ fcmToken: FieldValue.delete() }).catch(() => undefined);
      }
    } catch {
      // Continue pruning best-effort
    }
  }
}

/**
 * Sends a display + data notification to parent devices of a specific family,
 * respecting each parent device's notification preferences.
 */
export async function pushToParentDevices(
  familyId: string,
  payload: NotificationPayload,
  prefKey?: keyof ParentNotificationPrefs,
  channelId: NotificationChannelId = NOTIFICATION_CHANNELS.SUPERVISION,
): Promise<BatchSendResult> {
  const parentDevicesSnap = await db
    .collection("families")
    .doc(familyId)
    .collection("parentDevices")
    .get();

  const eligibleTokens: string[] = [];

  for (const doc of parentDevicesSnap.docs) {
    const token = doc.get("fcmToken") as string | undefined;
    if (!token || typeof token !== "string" || token.trim().length === 0) continue;

    if (prefKey) {
      const prefs = (doc.get("notificationPrefs") as ParentNotificationPrefs) || {};
      // If preference is explicitly false, skip this parent device
      if (prefs[prefKey] === false) continue;
    }

    eligibleTokens.push(token);
  }

  if (eligibleTokens.length === 0) {
    return { totalSent: 0, successCount: 0, failureCount: 0, invalidTokens: [] };
  }

  return sendMulticastChunks(eligibleTokens, payload, channelId);
}

/**
 * Sends a data-only (no `notification` payload) high-priority FCM message to every
 * non-revoked device with a registered token for this child.
 */
export async function pushToChildDevices(
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
      return (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      );
    })
    .map(({ doc }) => doc.ref);

  await Promise.all(staleTokenDocs.map((ref) => ref.update({ fcmToken: FieldValue.delete() })));
}
