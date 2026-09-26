import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { assertSuperAdmin, writeAuditLog } from "./adminAuth";

const db = getFirestore();

export const adminListChildren = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = request.data || {};
  const pageSize = Math.min(Math.max(Number(data.pageSize) || 25, 5), 100);
  const searchQuery = (data.query as string | undefined)?.toLowerCase().trim();

  // Query across families using collectionGroup or parent scan
  const snap = await db.collectionGroup("children").limit(pageSize).get();
  let children = await Promise.all(
    snap.docs.map(async (doc) => {
      const d = doc.data();
      const familyRef = doc.ref.parent.parent;
      const devicesSnap = await doc.ref.collection("devices").count().get();
      return {
        childId: doc.id,
        familyId: familyRef ? familyRef.id : "unknown",
        displayName: d.displayName || "Child",
        ageBand: d.ageBand || "AGE_7_TO_9",
        avatarId: d.avatarId || "DEFAULT",
        language: d.language || "en",
        deviceCount: devicesSnap.data().count,
        createdAt: d.createdAt ? (d.createdAt.toDate ? d.createdAt.toDate().toISOString() : d.createdAt) : null,
      };
    })
  );

  if (searchQuery) {
    children = children.filter((c) =>
      c.displayName.toLowerCase().includes(searchQuery) ||
      c.childId.toLowerCase().includes(searchQuery) ||
      c.familyId.toLowerCase().includes(searchQuery)
    );
  }

  return { items: children };
});

export const adminDeleteChild = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const { familyId, childId, confirm } = request.data || {};

  if (!familyId || !childId) {
    throw new HttpsError("invalid-argument", "Missing familyId or childId.");
  }
  if (confirm !== "DELETE") {
    throw new HttpsError("invalid-argument", "Must confirm with 'DELETE'.");
  }

  const childRef = db.collection("families").doc(familyId).collection("children").doc(childId);
  const childSnap = await childRef.get();
  if (!childSnap.exists) {
    throw new HttpsError("not-found", "Child not found.");
  }

  // Delete devices under this child
  const devSnap = await childRef.collection("devices").get();
  for (const d of devSnap.docs) {
    await d.ref.delete().catch(() => null);
  }

  await childRef.delete();
  await writeAuditLog(admin.uid, "child.delete", "child", childId, { familyId });

  return { ok: true };
});

export const adminListDevices = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = request.data || {};
  const pageSize = Math.min(Math.max(Number(data.pageSize) || 25, 5), 100);
  const searchQuery = (data.query as string | undefined)?.toLowerCase().trim();

  const snap = await db.collectionGroup("devices").limit(pageSize).get();
  let devices = snap.docs.map((doc) => {
    const d = doc.data();
    const childRef = doc.ref.parent.parent;
    const familyRef = childRef ? childRef.parent.parent : null;
    return {
      deviceId: doc.id,
      childId: childRef ? childRef.id : "unknown",
      familyId: familyRef ? familyRef.id : "unknown",
      model: d.model || "Android Tablet/Phone",
      osVersion: d.osVersion || "Android 13+",
      appVersion: d.appVersion || "1.0.0",
      revoked: d.revoked ?? false,
      launcherDefault: d.launcherDefault ?? true,
      batteryPercent: d.batteryPercent ?? null,
      lastSeenAt: d.lastSeenAt ? (d.lastSeenAt.toDate ? d.lastSeenAt.toDate().toISOString() : d.lastSeenAt) : null,
    };
  });

  if (searchQuery) {
    devices = devices.filter((dev) =>
      dev.deviceId.toLowerCase().includes(searchQuery) ||
      dev.model.toLowerCase().includes(searchQuery) ||
      dev.childId.toLowerCase().includes(searchQuery) ||
      dev.familyId.toLowerCase().includes(searchQuery)
    );
  }

  return { items: devices };
});

export const adminRevokeDevice = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const { familyId, childId, deviceId } = request.data || {};

  if (!familyId || !childId || !deviceId) {
    throw new HttpsError("invalid-argument", "Missing familyId, childId, or deviceId.");
  }

  const devRef = db.collection("families").doc(familyId).collection("children").doc(childId).collection("devices").doc(deviceId);
  const devSnap = await devRef.get();
  if (!devSnap.exists) {
    throw new HttpsError("not-found", "Device not found.");
  }

  await devRef.update({
    revoked: true,
    revokedAt: FieldValue.serverTimestamp(),
    fcmToken: FieldValue.delete(),
  });

  await writeAuditLog(admin.uid, "device.revoke", "device", deviceId, { familyId, childId });

  return { ok: true };
});
