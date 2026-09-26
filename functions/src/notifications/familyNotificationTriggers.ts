import { getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import {
  NOTIFICATION_CHANNELS,
  PARENT_AWARENESS_TYPES,
  type NotificationPayload,
} from "./notificationTypes";
import { pushToParentDevices } from "./notificationDispatcher";

const db = getFirestore();

/**
 * Trigger: When a new child device document is created upon successful pairing,
 * send an awareness alert to all registered parent devices in that family.
 */
export const onChildDevicePaired = onDocumentCreated(
  "families/{familyId}/children/{childId}/devices/{deviceId}",
  async (event) => {
    const { familyId, childId, deviceId } = event.params;
    try {
      const childSnap = await db
        .collection("families")
        .doc(familyId)
        .collection("children")
        .doc(childId)
        .get();

      const childName = (childSnap.get("name") as string | undefined) || "Your child";

      const payload: NotificationPayload = {
        type: PARENT_AWARENESS_TYPES.CHILD_PAIRED,
        title: "Device Paired",
        body: `${childName}'s device is now connected to Watching.`,
        familyId,
        childId,
        deviceId,
        route: `child/${childId}`,
        channelId: NOTIFICATION_CHANNELS.SUPERVISION,
      };

      await pushToParentDevices(
        familyId,
        payload,
        "pairingEvents",
        NOTIFICATION_CHANNELS.SUPERVISION,
      );
    } catch (err) {
      logger.error("Error sending child paired notification to parents", {
        familyId,
        childId,
        deviceId,
        error: err,
      });
    }
  },
);

/**
 * Trigger: When a child completes a quiz attempt, notify parents if:
 * 1. The child passed (optional celebration notification if enabled in parent prefs).
 * 2. The child failed 3 times consecutively (streak alert so parent can assist).
 */
export const onQuizAttemptLogged = onDocumentCreated(
  "families/{familyId}/children/{childId}/quizAttempts/{attemptId}",
  async (event) => {
    const snap = event.data;
    if (!snap || !snap.exists) return;

    const { familyId, childId, attemptId } = event.params;
    const passed = snap.get("passed") === true;

    try {
      const childSnap = await db
        .collection("families")
        .doc(familyId)
        .collection("children")
        .doc(childId)
        .get();

      const childName = (childSnap.get("name") as string | undefined) || "Your child";

      if (passed) {
        const payload: NotificationPayload = {
          type: PARENT_AWARENESS_TYPES.QUIZ_PASSED,
          title: "Quiz Passed! 🌟",
          body: `${childName} passed a learning quiz and unlocked extra screen time!`,
          familyId,
          childId,
          attemptId,
          route: `child/${childId}`,
          channelId: NOTIFICATION_CHANNELS.SUPERVISION,
        };

        await pushToParentDevices(
          familyId,
          payload,
          "quizPassedOptional",
          NOTIFICATION_CHANNELS.SUPERVISION,
        );
      } else {
        // Check if there is a 3+ fail streak
        const recentAttemptsSnap = await db
          .collection("families")
          .doc(familyId)
          .collection("children")
          .doc(childId)
          .collection("quizAttempts")
          .orderBy("timestamp", "desc")
          .limit(3)
          .get();

        const allFailed =
          recentAttemptsSnap.docs.length >= 3 &&
          recentAttemptsSnap.docs.every((d) => d.get("passed") === false);

        if (allFailed) {
          const payload: NotificationPayload = {
            type: PARENT_AWARENESS_TYPES.QUIZ_FAIL_STREAK,
            title: "Quiz Challenge Alert",
            body: `${childName} has missed multiple quiz questions in a row. You may want to review learning topics together.`,
            familyId,
            childId,
            attemptId,
            route: `child/${childId}`,
            channelId: NOTIFICATION_CHANNELS.SUPERVISION,
          };

          await pushToParentDevices(
            familyId,
            payload,
            "quizFailedRepeatedly",
            NOTIFICATION_CHANNELS.SUPERVISION,
          );
        }
      }
    } catch (err) {
      logger.error("Error processing quiz attempt notification", {
        familyId,
        childId,
        attemptId,
        error: err,
      });
    }
  },
);

/**
 * Trigger: When a child's daily usage is updated and hits their configured daily limit.
 */
export const onChildDailyUsageLogged = onDocumentWritten(
  "families/{familyId}/children/{childId}/usageDays/{dayDoc}",
  async (event) => {
    const after = event.data?.after;
    if (!after || !after.exists) return;

    const before = event.data?.before;
    const afterMinutes = (after.get("minutesUsed") as number) || 0;
    const beforeMinutes =
      before && before.exists ? ((before.get("minutesUsed") as number) || 0) : 0;

    // Only trigger if minutes increased
    if (afterMinutes <= beforeMinutes) return;

    const { familyId, childId } = event.params;

    try {
      const policySnap = await db
        .collection("families")
        .doc(familyId)
        .collection("children")
        .doc(childId)
        .collection("policy")
        .doc("current")
        .get();

      const dailyLimit = policySnap.get("dailyLimitMinutes") as number | undefined;

      if (typeof dailyLimit === "number" && dailyLimit > 0) {
        if (beforeMinutes < dailyLimit && afterMinutes >= dailyLimit) {
          const childSnap = await db
            .collection("families")
            .doc(familyId)
            .collection("children")
            .doc(childId)
            .get();

          const childName = (childSnap.get("name") as string | undefined) || "Your child";

          const payload: NotificationPayload = {
            type: PARENT_AWARENESS_TYPES.TIME_UP,
            title: "Daily Limit Reached",
            body: `${childName} has reached their daily screen time limit (${dailyLimit} mins).`,
            familyId,
            childId,
            route: `child/${childId}`,
            channelId: NOTIFICATION_CHANNELS.SUPERVISION,
          };

          await pushToParentDevices(
            familyId,
            payload,
            "timeUp",
            NOTIFICATION_CHANNELS.SUPERVISION,
          );
        }
      }
    } catch (err) {
      logger.error("Error sending daily limit reached notification", {
        familyId,
        childId,
        error: err,
      });
    }
  },
);
