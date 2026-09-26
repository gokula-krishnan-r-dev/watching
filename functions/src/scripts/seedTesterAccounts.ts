/**
 * Seeds closed-tester parent Auth users + users/{uid} docs for managing-screen-time.
 *
 * Usage (from repo root, with Firebase CLI logged in):
 *   cd functions && npm run build && node lib/scripts/seedTesterAccounts.js
 *
 * Or via ts-node / compile then node. Prefer Application Default Credentials
 * from `firebase login` + `gcloud auth application-default login`, or set
 * GOOGLE_APPLICATION_CREDENTIALS to a service-account JSON.
 */
import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { TESTER_PARENT_ACCOUNTS, TESTER_STATIC_OTP } from "../testerAccounts";

if (getApps().length === 0) {
  initializeApp({ projectId: "managing-screen-time" });
}

const auth = getAuth();
const db = getFirestore();

async function seedOne(email: string, displayName: string): Promise<void> {
  let user;
  try {
    user = await auth.getUserByEmail(email);
    await auth.updateUser(user.uid, {
      emailVerified: true,
      displayName,
      disabled: false,
    });
    console.log(`updated Auth  ${email}  uid=${user.uid}`);
  } catch (err: unknown) {
    const code = (err as { code?: string })?.code;
    if (code !== "auth/user-not-found") throw err;
    user = await auth.createUser({
      email,
      emailVerified: true,
      displayName,
    });
    console.log(`created Auth  ${email}  uid=${user.uid}`);
  }

  const claims = user.customClaims ?? {};
  if (claims.role !== "parent") {
    await auth.setCustomUserClaims(user.uid, { ...claims, role: "parent" });
    console.log(`  set claim role=parent`);
  }

  const userRef = db.collection("users").doc(user.uid);
  const existing = await userRef.get();
  await userRef.set(
    {
      email,
      displayName,
      role: "parent",
      status: "active",
      emailVerified: true,
      isTesterAccount: true,
      updatedAt: FieldValue.serverTimestamp(),
      ...(!existing.exists ? { createdAt: FieldValue.serverTimestamp() } : {}),
    },
    { merge: true },
  );
  console.log(`  users/${user.uid} merged`);
}

async function main(): Promise<void> {
  console.log(`Seeding ${TESTER_PARENT_ACCOUNTS.length} tester parents (OTP=${TESTER_STATIC_OTP})…`);
  for (const account of TESTER_PARENT_ACCOUNTS) {
    await seedOne(account.email, account.displayName);
  }
  console.log("Done.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
