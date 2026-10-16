/** DTOs mirrored from the Rust Tauri commands (camelCase serde). */
export interface ChildCard {
  childId: string;
  displayName: string;
  ageBand: string;
  minutesUsedToday: number;
  minutesRemainingToday: number | null;
  pairedDeviceCount: number;
  platformHint: string | null;
  quizMode: string;
}
export interface ChildPolicy {
  quizMode: string;
  quizIntervalMinutes: number;
  allowRetryDuringCooldown: boolean;
  dailyCeilingMinutes: number | null;
  questionsPerQuiz: number;
  passScorePercent: number;
  rewardsEnabled: boolean;
  weekendBonusEnabled: boolean;
  extraMinutesOnPass: number;
  defaultBlockMinutes: number;
  defaultCooldownMinutes: number;
  emergencyApps: string[];
  paused: boolean;
}
export interface InventoryApp {
  appId: string;
  label: string;
  allowed: boolean;
}
export interface DeviceEnforcementStatus {
  guardianState: string;
  enforcementTier: string;
  tamperFlags: string[];
  degradedMessage: string | null;
  strictAvailable: boolean;
  installedApps: InventoryApp[];
}
export interface AppRule {
  appId: string;
  packageOrBundleId: string;
  displayName: string;
  allowed: boolean;
  blockMinutes: number;
  grantOnPassMinutes: number;
  cooldownMinutes: number;
  isEmergency: boolean;
}
export interface ChildDetail {
  card: ChildCard;
  policy: ChildPolicy;
  appRules: AppRule[];
  failLockScope: string;
  enforcement: DeviceEnforcementStatus | null;
}
export interface PairingOffer {
  childId: string;
  code: string;
  secret: string;
  qrPayload: string;
  expiresAtEpochMs: number;
}
export interface UiStateDto<T> {
  state: "loading" | "empty" | "success" | "error";
  data: T | null;
  error: string | null;
}
export interface ParentProfile {
  uid: string;
  email: string;
  familyId: string | null;
}
export interface DashboardSnapshot {
  profile: ParentProfile;
  children: ChildCard[];
  maxChildren: number;
}
export interface NotificationPrefs {
  quizResults: boolean;
  failLockAlerts: boolean;
  tamperAlerts: boolean;
}
export interface AppMinutesRollup {
  appId: string;
  displayName: string;
  minutes: number;
  percentage: number;
}
export interface DailyMinutesPoint {
  date: string;
  totalMinutes: number;
  quizMinutes: number;
}
export interface ChildReportsSnapshot {
  childId: string;
  childName: string;
  todayMinutes: number;
  todayQuizMinutes: number;
  weeklyTotalMinutes: number;
  weeklyQuizMinutes: number;
  dailyAverageMinutes: number;
  topApps: AppMinutesRollup[];
  dailyTrend: DailyMinutesPoint[];
  quizStats: { totalAttempts: number; passes: number; passRatePercent: number; totalQuizMinutes: number };
  syncStatus: string;
}
