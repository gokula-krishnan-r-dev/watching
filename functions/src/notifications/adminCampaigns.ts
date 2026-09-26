import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { assertSuperAdmin, writeAuditLog } from "../admin/adminAuth";
import {
  NOTIFICATION_CHANNELS,
  type NotificationAudience,
  type NotificationIntent,
} from "./notificationTypes";
import { enqueueNotificationJob } from "./notificationQueue";

const db = getFirestore();

/**
 * Super Admin callable function to schedule and send an announcement or promotional
 * push notification campaign across users, parents, children, or a specific family.
 */
export const adminSendCampaign = onCall(async (request) => {
  const admin = assertSuperAdmin(request.auth);
  const data = (request.data || {}) as Record<string, any>;

  const title = typeof data.title === "string" ? data.title.trim() : "";
  const body = typeof data.body === "string" ? data.body.trim() : "";

  if (!title || !body) {
    throw new HttpsError(
      "invalid-argument",
      "Both 'title' and 'body' are required to send a notification campaign.",
    );
  }

  const rawAudience = data.audience;
  const audience: NotificationAudience = ["all", "parents", "children", "family"].includes(
    rawAudience,
  )
    ? rawAudience
    : "all";

  const rawIntent = data.intent;
  const intent: NotificationIntent = ["promo", "product"].includes(rawIntent)
    ? rawIntent
    : "promo";

  const imageUrl =
    typeof data.imageUrl === "string" && data.imageUrl.trim().length > 0
      ? data.imageUrl.trim()
      : undefined;

  const route =
    typeof data.route === "string" && data.route.trim().length > 0
      ? data.route.trim()
      : undefined;

  const familyId =
    typeof data.familyId === "string" && data.familyId.trim().length > 0
      ? data.familyId.trim()
      : undefined;

  const campaignRef = db.collection("adminCampaigns").doc();
  const campaignId = campaignRef.id;

  const campaignDoc: Record<string, any> = {
    title,
    body,
    audience,
    intent,
    adminUid: admin.uid,
    status: "queued",
    createdAt: FieldValue.serverTimestamp(),
  };

  if (imageUrl) campaignDoc.imageUrl = imageUrl;
  if (route) campaignDoc.route = route;
  if (familyId) campaignDoc.familyId = familyId;

  await campaignRef.set(campaignDoc);

  // Enqueue job into notification queue
  await enqueueNotificationJob(
    {
      audience,
      intent,
      title,
      body,
      imageUrl,
      route,
      familyId,
      channelId:
        intent === "promo"
          ? NOTIFICATION_CHANNELS.PROMOTIONS
          : NOTIFICATION_CHANNELS.UPDATES,
    },
    campaignId,
  );

  await writeAuditLog(admin.uid, "send_campaign", "export", campaignId, {
    title,
    audience,
    intent,
    hasImage: !!imageUrl,
  });

  return {
    campaignId,
    status: "queued",
    title,
    audience,
    intent,
  };
});

/**
 * Super Admin callable function to list historical campaigns with their delivery stats.
 */
export const adminListCampaigns = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const data = (request.data || {}) as Record<string, any>;
  const limit = Math.min(100, Math.max(1, Number(data.limit) || 20));

  const snap = await db
    .collection("adminCampaigns")
    .orderBy("createdAt", "desc")
    .limit(limit)
    .get();

  const campaigns = snap.docs.map((doc) => {
    const d = doc.data();
    return {
      id: doc.id,
      ...d,
      createdAt: d.createdAt?.toDate ? d.createdAt.toDate().toISOString() : null,
      completedAt: d.completedAt?.toDate ? d.completedAt.toDate().toISOString() : null,
    };
  });

  return {
    campaigns,
    count: campaigns.length,
  };
});
