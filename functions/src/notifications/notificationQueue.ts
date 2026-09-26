import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import {
  NOTIFICATION_CHANNELS,
  type NotificationAudience,
  type NotificationChannelId,
  type NotificationPayload,
  type QueueJobDoc,
} from "./notificationTypes";
import { sendMulticastChunks } from "./notificationDispatcher";

const db = getFirestore();

/**
 * Resolves audience tokens efficiently from Firestore.
 */
async function resolveAudienceTokens(
  audience: NotificationAudience,
  familyId?: string,
): Promise<string[]> {
  const tokens = new Set<string>();

  if (familyId) {
    // 1. Specific family: load parentDevices and child devices
    const parentSnap = await db
      .collection("families")
      .doc(familyId)
      .collection("parentDevices")
      .get();
    parentSnap.docs.forEach((d) => {
      const t = d.get("fcmToken");
      if (typeof t === "string" && t.length > 0) tokens.add(t);
    });

    if (audience === "all" || audience === "children") {
      const childrenSnap = await db
        .collection("families")
        .doc(familyId)
        .collection("children")
        .get();
      for (const childDoc of childrenSnap.docs) {
        const devicesSnap = await childDoc.ref.collection("devices").get();
        devicesSnap.docs.forEach((d) => {
          if (d.get("revoked") !== true) {
            const t = d.get("fcmToken");
            if (typeof t === "string" && t.length > 0) tokens.add(t);
          }
        });
      }
    }
    return Array.from(tokens);
  }

  // 2. Broadcast across global fcmTokens registry
  let query: FirebaseFirestore.Query = db.collection("fcmTokens");
  if (audience === "parents") {
    query = query.where("role", "==", "parent");
  } else if (audience === "children") {
    query = query.where("role", "==", "child");
  }

  const fcmSnap = await query.limit(5000).get();
  fcmSnap.docs.forEach((d) => {
    const t = d.get("token") || d.id;
    if (typeof t === "string" && t.length > 0) tokens.add(t);
  });

  // Fallback / supplement: also scan parentDevices collectionGroup if fcmTokens has fewer items
  if (audience === "all" || audience === "parents") {
    const parentGroupSnap = await db.collectionGroup("parentDevices").limit(2000).get();
    parentGroupSnap.docs.forEach((d) => {
      const t = d.get("fcmToken");
      if (typeof t === "string" && t.length > 0) tokens.add(t);
    });
  }

  if (audience === "all" || audience === "children") {
    const childGroupSnap = await db.collectionGroup("devices").limit(2000).get();
    childGroupSnap.docs.forEach((d) => {
      if (d.get("revoked") !== true) {
        const t = d.get("fcmToken");
        if (typeof t === "string" && t.length > 0) tokens.add(t);
      }
    });
  }

  return Array.from(tokens);
}

/**
 * Enqueue a new notification job into the Firestore queue.
 */
export async function enqueueNotificationJob(
  jobData: Omit<QueueJobDoc, "status" | "createdAt">,
  customJobId?: string,
): Promise<string> {
  const ref = customJobId
    ? db.collection("notification_queue").doc(customJobId)
    : db.collection("notification_queue").doc();

  const doc: Record<string, any> = {
    status: "queued",
    createdAt: FieldValue.serverTimestamp(),
  };
  for (const [k, v] of Object.entries(jobData)) {
    if (v !== undefined) {
      doc[k] = v;
    }
  }

  await ref.set(doc);
  return ref.id;
}

/**
 * Firestore trigger that automatically picks up queued jobs in notification_queue.
 */
export const onNotificationQueued = onDocumentWritten(
  "notification_queue/{jobId}",
  async (event) => {
    const after = event.data?.after;
    if (!after || !after.exists) return;

    const data = after.data() as QueueJobDoc;
    if (data.status !== "queued") return;

    const jobId = event.params.jobId;

    // Atomically mark as processing to prevent duplicate execution
    const jobRef = after.ref;
    try {
      await db.runTransaction(async (t) => {
        const snap = await t.get(jobRef);
        if (snap.get("status") !== "queued") {
          throw new Error("ALREADY_PROCESSING");
        }
        t.update(jobRef, {
          status: "processing",
          processedAt: FieldValue.serverTimestamp(),
        });
      });
    } catch (e: any) {
      if (e?.message === "ALREADY_PROCESSING") return;
      logger.error("Failed to mark notification job as processing", { jobId, error: e });
      return;
    }

    try {
      const tokens = await resolveAudienceTokens(data.audience, data.familyId);
      logger.info(`Resolved ${tokens.length} tokens for notification job ${jobId}`, {
        audience: data.audience,
        intent: data.intent,
      });

      const payload: NotificationPayload = {
        type: data.intent === "promo" ? "admin_promo" : "admin_product",
        title: data.title,
        body: data.body,
        imageUrl: data.imageUrl,
        route: data.route,
        campaignId: jobId,
        channelId: data.channelId || (data.intent === "promo" ? NOTIFICATION_CHANNELS.PROMOTIONS : NOTIFICATION_CHANNELS.UPDATES),
      };

      const result = await sendMulticastChunks(
        tokens,
        payload,
        payload.channelId as NotificationChannelId,
      );

      await jobRef.update({
        status: "completed",
        recipientCount: tokens.length,
        successCount: result.successCount,
        failureCount: result.failureCount,
        completedAt: FieldValue.serverTimestamp(),
      });

      // Mirror status update to adminCampaigns if campaign exists
      await db
        .collection("adminCampaigns")
        .doc(jobId)
        .update({
          status: "completed",
          recipientCount: tokens.length,
          successCount: result.successCount,
          failureCount: result.failureCount,
          completedAt: FieldValue.serverTimestamp(),
        })
        .catch(() => undefined);

      logger.info(`Notification job ${jobId} completed successfully`, {
        sent: result.totalSent,
        success: result.successCount,
        failure: result.failureCount,
      });
    } catch (err: any) {
      logger.error(`Notification job ${jobId} failed`, { error: err });
      await jobRef.update({
        status: "failed",
        error: err?.message || String(err),
        completedAt: FieldValue.serverTimestamp(),
      });

      await db
        .collection("adminCampaigns")
        .doc(jobId)
        .update({
          status: "failed",
          error: err?.message || String(err),
          completedAt: FieldValue.serverTimestamp(),
        })
        .catch(() => undefined);
    }
  },
);
