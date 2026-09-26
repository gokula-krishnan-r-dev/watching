import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { assertSuperAdmin, writeAuditLog } from "./adminAuth";

const db = getFirestore();
const auth = getAuth();

export const adminListUsers = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = request.data || {};
  const pageSize = Math.min(Math.max(Number(data.pageSize) || 25, 5), 100);
  const statusFilter = data.status as string | undefined;
  const searchQuery = (data.query as string | undefined)?.toLowerCase().trim();

  let query: FirebaseFirestore.Query = db.collection("users");

  if (statusFilter && ["active", "disabled", "inactive"].includes(statusFilter)) {
    query = query.where("status", "==", statusFilter);
  }

  // Basic ordering by createdAt if available
  query = query.limit(pageSize);

  const snapshot = await query.get();
  let users = snapshot.docs.map((doc) => {
    const d = doc.data();
    return {
      uid: doc.id,
      email: d.email || "No email",
      displayName: d.displayName || "Parent",
      familyId: d.familyId || null,
      status: d.status || "active",
      createdAt: d.createdAt ? (d.createdAt.toDate ? d.createdAt.toDate().toISOString() : d.createdAt) : null,
      lastSignInAt: d.lastSignInAt ? (d.lastSignInAt.toDate ? d.lastSignInAt.toDate().toISOString() : d.lastSignInAt) : null,
    };
  });

  if (searchQuery) {
    users = users.filter((u) => 
      u.email.toLowerCase().includes(searchQuery) || 
      u.uid.toLowerCase().includes(searchQuery) ||
      u.displayName.toLowerCase().includes(searchQuery)
    );
  }

  return {
    items: users,
    nextCursor: null,
    totalHint: users.length,
  };
});

export const adminGetUser = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const uid = request.data?.uid;
  if (!uid || typeof uid !== "string") {
    throw new HttpsError("invalid-argument", "Missing user uid.");
  }

  const [docSnap, authUser] = await Promise.all([
    db.collection("users").doc(uid).get(),
    auth.getUser(uid).catch(() => null),
  ]);

  if (!docSnap.exists && !authUser) {
    throw new HttpsError("not-found", "User not found.");
  }

  const d = docSnap.exists ? docSnap.data()! : {};
  return {
    uid,
    email: authUser?.email || d.email || "",
    displayName: authUser?.displayName || d.displayName || "Parent",
    familyId: d.familyId || null,
    status: d.status || (authUser?.disabled ? "disabled" : "active"),
    authDisabled: authUser?.disabled ?? false,
    emailVerified: authUser?.emailVerified ?? false,
    providers: authUser?.providerData?.map((p) => p.providerId) || [],
    createdAt: authUser?.metadata?.creationTime || d.createdAt || null,
    lastSignInAt: authUser?.metadata?.lastSignInTime || d.lastSignInAt || null,
  };
});

export const adminSetUserStatus = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const uid = request.data?.uid;
  const status = request.data?.status;

  if (!uid || typeof uid !== "string") {
    throw new HttpsError("invalid-argument", "Missing user uid.");
  }
  if (!["active", "disabled", "inactive"].includes(status)) {
    throw new HttpsError("invalid-argument", "Status must be active, disabled, or inactive.");
  }

  // Update Firebase Auth if disabling / enabling
  if (status === "disabled") {
    await auth.updateUser(uid, { disabled: true }).catch(() => null);
  } else if (status === "active") {
    await auth.updateUser(uid, { disabled: false }).catch(() => null);
  }

  // Update Firestore user document
  const userRef = db.collection("users").doc(uid);
  await userRef.set(
    {
      status,
      disabledAt: status === "disabled" ? FieldValue.serverTimestamp() : FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await writeAuditLog(admin.uid, "user.setStatus", "user", uid, { newStatus: status });

  return { ok: true, status };
});
