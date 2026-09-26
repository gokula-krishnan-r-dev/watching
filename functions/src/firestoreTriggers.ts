import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";

const db = getFirestore();
const messaging = getMessaging();

/**
 * Sends a data-only (no `notification` payload) high-priority FCM message to every
 * non-revoked device with a registered token for this child.
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

/** Parent changed the child-level policy → wake the paired device(s) to pull it. */
export const onPolicyChanged = onDocumentWritten(
  "families/{familyId}/children/{childId}/policy/{policyId}",
  async (event) => {
    const { familyId, childId } = event.params;
    await pushToChildDevices(familyId, childId, "policy_sync").catch((error) =>
      logger.warn("onPolicyChanged push failed", error),
    );
  },
);

/** Parent added/edited/removed an allowed app → wake the paired device(s) to pull it. */
export const onAppRuleChanged = onDocumentWritten(
  "families/{familyId}/children/{childId}/appRules/{appId}",
  async (event) => {
    const { familyId, childId } = event.params;
    await pushToChildDevices(familyId, childId, "policy_sync").catch((error) =>
      logger.warn("onAppRuleChanged push failed", error),
    );
  },
);

/**
 * Parent revoked a device → near-instant sign-out on that device.
 *
 * IMPORTANT: the parent write sets `revoked: true` and often deletes `fcmToken` in the same
 * update. We must push using the **before** token and must not filter out the revoked doc,
 * otherwise the kill-switch FCM never reaches the device being disconnected.
 */
export const onDeviceRevoked = onDocumentUpdated(
  "families/{familyId}/children/{childId}/devices/{deviceId}",
  async (event) => {
    const beforeRevoked = event.data?.before.get("revoked") === true;
    const afterRevoked = event.data?.after.get("revoked") === true;
    if (beforeRevoked || !afterRevoked) return;

    const { familyId, childId, deviceId } = event.params;
    const token =
      (event.data?.before.get("fcmToken") as string | undefined) ||
      (event.data?.after.get("fcmToken") as string | undefined);
    if (!token || token.length === 0) {
      logger.warn("onDeviceRevoked: no fcmToken available; child will catch via listener/heartbeat", {
        familyId,
        childId,
        deviceId,
      });
      return;
    }

    try {
      await messaging.send({
        token,
        data: { type: "device_revoked", familyId, childId, deviceId },
        android: { priority: "high" },
      });
    } catch (error) {
      const code = (error as { code?: string } | undefined)?.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        // Best-effort cleanup; doc may already have deleted the token.
        await event.data?.after.ref.update({ fcmToken: FieldValue.delete() }).catch(() => undefined);
      }
      logger.warn("onDeviceRevoked push failed", error);
    }
  },
);

/**
 * Parent reset the Parent PIN hash on the family root → wake every paired child device so
 * policy sync (which also pulls parentPinHash) refreshes the offline PIN verifier.
 */
export const onParentPinChanged = onDocumentUpdated("families/{familyId}", async (event) => {
  const before = event.data?.before.get("parentPinHash") as string | undefined;
  const after = event.data?.after.get("parentPinHash") as string | undefined;
  if (!after || before === after) return;
  const { familyId } = event.params;
  const childrenSnap = await db.collection("families").doc(familyId).collection("children").get();
  await Promise.all(
    childrenSnap.docs.map((child) =>
      pushToChildDevices(familyId, child.id, "pin_sync").catch((error) =>
        logger.warn("onParentPinChanged push failed", { childId: child.id, error }),
      ),
    ),
  );
});
