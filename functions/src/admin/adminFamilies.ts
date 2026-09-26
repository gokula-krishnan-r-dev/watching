import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { assertSuperAdmin, writeAuditLog } from "./adminAuth";

const db = getFirestore();

export const adminListFamilies = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = request.data || {};
  const pageSize = Math.min(Math.max(Number(data.pageSize) || 25, 5), 100);
  const statusFilter = data.status as string | undefined;
  const searchQuery = (data.query as string | undefined)?.toLowerCase().trim();

  let query: FirebaseFirestore.Query = db.collection("families");
  if (statusFilter && ["active", "suspended"].includes(statusFilter)) {
    query = query.where("status", "==", statusFilter);
  }
  query = query.limit(pageSize);

  const snapshot = await query.get();
  let families = await Promise.all(
    snapshot.docs.map(async (doc) => {
      const d = doc.data();
      const childrenSnap = await doc.ref.collection("children").count().get();
      return {
        familyId: doc.id,
        name: d.name || "Unnamed Family",
        ownerUid: d.ownerUid || "unknown",
        ownerEmail: d.ownerEmail || null,
        status: d.status || "active",
        childCount: childrenSnap.data().count,
        deviceCount: d.deviceCount ?? childrenSnap.data().count,
        createdAt: d.createdAt ? (d.createdAt.toDate ? d.createdAt.toDate().toISOString() : d.createdAt) : null,
      };
    })
  );

  if (searchQuery) {
    families = families.filter((f) =>
      f.name.toLowerCase().includes(searchQuery) ||
      f.familyId.toLowerCase().includes(searchQuery) ||
      (f.ownerEmail && f.ownerEmail.toLowerCase().includes(searchQuery))
    );
  }

  return {
    items: families,
    nextCursor: null,
  };
});

export const adminGetFamilyTree = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const familyId = request.data?.familyId;
  if (!familyId || typeof familyId !== "string") {
    throw new HttpsError("invalid-argument", "Missing familyId.");
  }

  const familyRef = db.collection("families").doc(familyId);
  const familySnap = await familyRef.get();
  if (!familySnap.exists) {
    throw new HttpsError("not-found", "Family not found.");
  }

  const familyData = familySnap.data()!;
  // Redact secrets
  delete familyData.parentPinHash;
  delete familyData.parentPin;

  // Fetch members
  const membersSnap = await familyRef.collection("members").get();
  const members = membersSnap.docs.map((m) => ({
    uid: m.id,
    role: m.get("role") || "member",
    createdAt: m.get("createdAt") || null,
  }));

  // Fetch children
  const childrenSnap = await familyRef.collection("children").get();
  const children = await Promise.all(
    childrenSnap.docs.map(async (childDoc) => {
      const c = childDoc.data();
      const devicesSnap = await childDoc.ref.collection("devices").get();
      const devices = devicesSnap.docs.map((dev) => {
        const dd = dev.data();
        return {
          deviceId: dev.id,
          model: dd.model || "Generic Android",
          osVersion: dd.osVersion || "Android",
          appVersion: dd.appVersion || "1.0.0",
          launcherDefault: dd.launcherDefault ?? true,
          revoked: dd.revoked ?? false,
          batteryPercent: dd.batteryPercent ?? null,
          lastSeenAt: dd.lastSeenAt ? (dd.lastSeenAt.toDate ? dd.lastSeenAt.toDate().toISOString() : dd.lastSeenAt) : null,
        };
      });

      const policySnap = await childDoc.ref.collection("policy").doc("current").get();
      const p = policySnap.exists ? policySnap.data()! : {};

      return {
        childId: childDoc.id,
        displayName: c.displayName || "Child",
        ageBand: c.ageBand || "AGE_7_TO_9",
        avatarId: c.avatarId || "DEFAULT",
        language: c.language || "en",
        devices,
        policySummary: {
          quizMode: p.quizMode || "app_block",
          dailyCeilingMinutes: p.dailyCeilingMinutes || 120,
          aiQuizzesEnabled: p.aiQuizzesEnabled ?? true,
          bonusMinutesPerQuiz: p.bonusMinutesPerQuiz || 15,
        },
      };
    })
  );

  return {
    family: {
      familyId,
      name: familyData.name || "Family",
      ownerUid: familyData.ownerUid,
      status: familyData.status || "active",
      createdAt: familyData.createdAt || null,
    },
    members,
    children,
  };
});

export const adminSetFamilyStatus = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const familyId = request.data?.familyId;
  const status = request.data?.status;

  if (!familyId || typeof familyId !== "string") {
    throw new HttpsError("invalid-argument", "Missing familyId.");
  }
  if (!["active", "suspended"].includes(status)) {
    throw new HttpsError("invalid-argument", "Status must be 'active' or 'suspended'.");
  }

  await db.collection("families").doc(familyId).set(
    {
      status,
      suspendedAt: status === "suspended" ? FieldValue.serverTimestamp() : FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await writeAuditLog(admin.uid, "family.setStatus", "family", familyId, { newStatus: status });

  return { ok: true, status };
});

export const adminDeleteFamily = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const familyId = request.data?.familyId;
  const confirm = request.data?.confirm;

  if (!familyId || typeof familyId !== "string") {
    throw new HttpsError("invalid-argument", "Missing familyId.");
  }
  if (confirm !== "DELETE") {
    throw new HttpsError("invalid-argument", "Must provide confirmation string 'DELETE'.");
  }

  const familyRef = db.collection("families").doc(familyId);
  const familySnap = await familyRef.get();
  if (!familySnap.exists) {
    throw new HttpsError("not-found", "Family not found.");
  }

  const childrenSnap = await familyRef.collection("children").get();
  for (const childDoc of childrenSnap.docs) {
    const devicesSnap = await childDoc.ref.collection("devices").get();
    for (const d of devicesSnap.docs) {
      await d.ref.delete().catch(() => null);
    }
    await childDoc.ref.delete().catch(() => null);
  }

  const membersSnap = await familyRef.collection("members").get();
  for (const m of membersSnap.docs) {
    await db.collection("users").doc(m.id).set({ familyId: FieldValue.delete() }, { merge: true }).catch(() => null);
    await m.ref.delete().catch(() => null);
  }

  await familyRef.delete();
  await writeAuditLog(admin.uid, "family.delete", "family", familyId);

  return { ok: true };
});
