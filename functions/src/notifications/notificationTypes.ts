/**
 * Push Notification System Type Definitions for Watching
 *
 * Covers Control Plane (data-only for child devices), Awareness Plane (display
 * alerts for parent phones), and Promotional / Announcement Campaigns (Super Admin).
 */

export type NotificationAudience = "all" | "parents" | "children" | "family";

export type NotificationIntent = "promo" | "product";

export const NOTIFICATION_CHANNELS = {
  SUPERVISION: "watching_supervision",
  PROMOTIONS: "watching_promotions",
  UPDATES: "watching_updates",
  LEARNING: "watching_learning",
} as const;

export type NotificationChannelId =
  (typeof NOTIFICATION_CHANNELS)[keyof typeof NOTIFICATION_CHANNELS];

/** Control-plane message types for child device kiosk / sync */
export const CHILD_CONTROL_TYPES = {
  POLICY_SYNC: "policy_sync",
  DEVICE_REVOKED: "device_revoked",
  PIN_SYNC: "pin_sync",
  FAMILY_DELETED: "family_deleted",
} as const;

/** Awareness-plane message types for parent notifications */
export const PARENT_AWARENESS_TYPES = {
  CHILD_PAIRED: "parent_child_paired",
  TIME_UP: "parent_time_up",
  FAIL_LOCK: "parent_fail_lock",
  QUIZ_FAIL_STREAK: "parent_quiz_fail_streak",
  QUIZ_PASSED: "parent_quiz_passed",
  DAILY_SUMMARY: "parent_daily_summary",
  DEVICE_OFFLINE: "parent_device_offline",
  LAUNCHER_LOST: "parent_launcher_lost",
} as const;

/** Admin broadcast message types */
export const ADMIN_MESSAGE_TYPES = {
  PROMO: "admin_promo",
  PRODUCT: "admin_product",
} as const;

export interface NotificationPayload {
  type: string;
  title?: string;
  body?: string;
  imageUrl?: string;
  route?: string;
  familyId?: string;
  childId?: string;
  campaignId?: string;
  channelId?: NotificationChannelId;
  [key: string]: string | undefined;
}

export interface ParentNotificationPrefs {
  dailySummary?: boolean;
  timeUp?: boolean;
  quizFailedRepeatedly?: boolean;
  quizPassedOptional?: boolean;
  deviceOffline?: boolean;
  pairingEvents?: boolean;
  launcherLost?: boolean;
  promotions?: boolean;
  productUpdates?: boolean;
}

export interface QueueJobDoc {
  status: "queued" | "processing" | "completed" | "failed";
  audience: NotificationAudience;
  intent: NotificationIntent;
  title: string;
  body: string;
  imageUrl?: string;
  route?: string;
  familyId?: string;
  channelId?: NotificationChannelId;
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  processedAt?: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  recipientCount?: number;
  successCount?: number;
  failureCount?: number;
  error?: string;
}

export interface AdminCampaignDoc {
  title: string;
  body: string;
  imageUrl?: string;
  audience: NotificationAudience;
  intent: NotificationIntent;
  route?: string;
  familyId?: string;
  adminUid: string;
  status: "queued" | "completed" | "failed";
  createdAt: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  completedAt?: FirebaseFirestore.FieldValue | FirebaseFirestore.Timestamp;
  recipientCount?: number;
  successCount?: number;
  failureCount?: number;
}
