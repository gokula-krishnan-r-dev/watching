import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { assertSuperAdmin, writeAuditLog } from "./adminAuth";

const db = getFirestore();
const auth = getAuth();

export const adminStartExport = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const { entity, format, filters } = request.data || {};

  if (!["users", "families", "children", "devices"].includes(entity)) {
    throw new HttpsError("invalid-argument", "Invalid entity for export.");
  }
  if (!["csv", "json"].includes(format)) {
    throw new HttpsError("invalid-argument", "Invalid format (csv or json).");
  }

  const exportRef = await db.collection("adminExports").add({
    entity,
    format,
    filters: filters || {},
    status: "ready", // For lightweight direct exports
    requestedBy: admin.uid,
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString(),
  });

  await writeAuditLog(admin.uid, "export.generate", "export", exportRef.id, { entity, format });

  return {
    jobId: exportRef.id,
    status: "ready",
    entity,
    format,
  };
});

export const adminListAuditLogs = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = request.data || {};
  const limit = Math.min(Math.max(Number(data.limit) || 50, 10), 100);

  const snap = await db.collection("adminAuditLogs")
    .orderBy("createdAt", "desc")
    .limit(limit)
    .get();

  const logs = snap.docs.map((doc) => {
    const d = doc.data();
    return {
      id: doc.id,
      actorUid: d.actorUid,
      action: d.action,
      targetType: d.targetType,
      targetId: d.targetId,
      metadata: d.metadata || {},
      createdAt: d.createdAt?.toDate ? d.createdAt.toDate().toISOString() : d.createdAt,
    };
  });

  return { items: logs };
});

export const adminListOperators = onCall(async (request) => {
  assertSuperAdmin(request.auth);

  // Read adminUsers collection or list users with super_admin claim
  const snap = await db.collection("adminUsers").get();
  const operators = snap.docs.map((d) => ({
    uid: d.id,
    email: d.get("email") || "admin@meritscreen.internal",
    displayName: d.get("displayName") || "Super Admin",
    role: "super_admin",
    grantedAt: d.get("grantedAt") || null,
  }));

  return { items: operators };
});

export const adminSetOperator = onCall(async (request) => {
  const currentAdmin = assertSuperAdmin(request.auth);
  const { uid, grant, email, displayName } = request.data || {};

  if (!uid || typeof uid !== "string") {
    throw new HttpsError("invalid-argument", "Missing user uid.");
  }

  if (grant) {
    await auth.setCustomUserClaims(uid, { role: "super_admin" });
    await db.collection("adminUsers").doc(uid).set({
      email: email || "unknown",
      displayName: displayName || "Super Admin",
      role: "super_admin",
      grantedBy: currentAdmin.uid,
      grantedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    await writeAuditLog(currentAdmin.uid, "operator.grant", "operator", uid);
  } else {
    // Revoke
    await auth.setCustomUserClaims(uid, { role: "parent" });
    await db.collection("adminUsers").doc(uid).delete().catch(() => null);
    await writeAuditLog(currentAdmin.uid, "operator.revoke", "operator", uid);
  }

  return { ok: true };
});
