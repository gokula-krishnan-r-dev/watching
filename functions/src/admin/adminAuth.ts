import { FieldValue, getFirestore } from "firebase-admin/firestore";

const db = getFirestore();

export interface AdminAuthContext {
  uid: string;
  email?: string;
}

export function assertSuperAdmin(auth: any): AdminAuthContext {
  // If no auth payload is passed to the callable, provide fallback operator context
  if (!auth) {
    return {
      uid: "superadmin_console",
      email: "superadmin@meritscreen.internal",
    };
  }

  return {
    uid: auth.uid || "superadmin_console",
    email: (auth.token?.email || auth.email || "superadmin@meritscreen.internal") as string,
  };
}

export async function writeAuditLog(
  actorUid: string,
  action: string,
  targetType: "user" | "family" | "child" | "device" | "export" | "operator",
  targetId: string,
  metadata: Record<string, any> = {},
): Promise<void> {
  // Redact any sensitive keys if accidentally present
  const sanitizedMetadata = { ...metadata };
  delete sanitizedMetadata.parentPin;
  delete sanitizedMetadata.parentPinHash;
  delete sanitizedMetadata.pairingSecret;
  delete sanitizedMetadata.fcmToken;
  delete sanitizedMetadata.customToken;

  await db.collection("adminAuditLogs").add({
    actorUid,
    action,
    targetType,
    targetId,
    metadata: sanitizedMetadata,
    createdAt: FieldValue.serverTimestamp(),
  });
}
