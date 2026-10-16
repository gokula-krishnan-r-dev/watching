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
  /** Parent bonus for `bonusDay` only (child-local YYYY-MM-DD); never changes the daily limit. */
  bonusMinutesToday?: number;
  bonusDay?: string | null;
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
export interface ChildLauncherApp {
  appId: string;
  displayName: string;
  packageOrBundleId: string;
  isEmergency: boolean;
  iconTone: string;
}
export interface ChildChecklistItem {
  id: string;
  label: string;
  done: boolean;
  recommended: boolean;
}
export interface QuizPrompt {
  id: string;
  prompt: string;
  choices: { id: string; text: string }[];
  index: number;
  total: number;
  teachTitle: string | null;
  teachBody: string[];
}
export interface ChildUiState {
  paired: boolean;
  screen: string;
  phase: string;
  childName: string;
  apps: ChildLauncherApp[];
  checklist: ChildChecklistItem[];
  checklistComplete: boolean;
  minutesUsedToday: number;
  minutesRemainingBlock: number;
  secondsRemainingBlock?: number;
  blockDurationMinutes?: number;
  cooldownSeconds: number;
  dailyRemainingMinutes: number;
  ceilingHit: boolean;
  bonusMinutesToday?: number;
  quiz: QuizPrompt | null;
  quizLock: { remainingSeconds: number; conceptLine: string } | null;
  lastFeedback: { correct: boolean; resultLine: string; whyLine: string; conceptLine: string } | null;
  lastResult: { passed: boolean; correctCount: number; total: number; percent: number } | null;
  packSource: string;
  elapsedMs: number;
  activeAppId?: string | null;
  activeAppName?: string | null;
}

/* ── Dashboard analytics (parent_family_insights) ── */
export interface DayPoint { day: string; minutes: number }
export interface AppShare { appId: string; label: string; minutes: number; percent: number }
export interface RecentAttempt { atMs: number; passed: boolean; score: number; total: number; topic: string }
export interface QuizSummary {
  attemptsWeek: number; passesWeek: number; passRatePercent: number | null;
  attemptsToday: number; passesToday: number; minutesEarnedWeek: number;
  passStreakDays: number; lastAttemptAtMs: number | null; recent: RecentAttempt[];
}
export interface SkillLine { topic: string; label: string; level: number; tier: string; concepts: string[] }
export interface StickerLine { emoji: string; title: string; unlockedAtMs: number }
export interface DeviceLine { deviceId: string; platform: string; model: string | null; lastSeenAtMs: number | null; online: boolean; batteryPercent: number | null; appVersion: string | null }
export interface InsightAlert { severity: "info" | "warning" | "critical"; kind: string; message: string }
export interface ChildInsights {
  childId: string; displayName: string; ageBand: string; paused: boolean;
  dailyLimitMinutes: number | null; bonusMinutesToday: number; usedTodayMinutes: number; remainingTodayMinutes: number | null;
  weekTotalMinutes: number; dailyAverageMinutes: number; weekDeltaPercent: number | null;
  trend: DayPoint[]; topApps: AppShare[]; quiz: QuizSummary;
  skills: { strengths: SkillLine[]; needsPractice: SkillLine[] };
  rewards: { xp: number; level: number; stickerCount: number; recentStickers: StickerLine[] };
  devices: DeviceLine[]; alerts: InsightAlert[]; error: string | null;
}
export interface FamilyInsights { source: "cloud" | "local"; generatedAtMs: number; children: ChildInsights[] }
