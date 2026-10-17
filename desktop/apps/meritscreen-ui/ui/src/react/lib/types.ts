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
  allowEarnBeyondDailyLimit?: boolean;
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
  id: string; prompt: string; choices: QuizChoice[]; index: number; total: number;
  teachTitle: string | null; teachBody: string[];
  interactionType?: string; promptTag?: string | null; promptCount?: number | null;
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
  /** Parent's daily limit incl. bonus; null/absent = no daily limit set. */
  dailyLimitMinutes?: number | null;
  quizIntervalMinutes?: number;
  ceilingHit: boolean;
  bonusMinutesToday?: number;
  quiz: QuizPrompt | null;
  quizLock: QuizLock | null;
  quizError?: string | null;
  showExplanations?: boolean;
  earlyLearner?: boolean;
  explorer?: ExplorerProgress;
  stickers?: StickerUnlock[];
  lastAward?: LastAward | null;
  nursery?: NurseryState | null;
  canRetryDuringCooldown?: boolean;
  paused?: boolean;
  canEarnBeyondCeiling?: boolean;
  lastFeedback: { correct: boolean; resultLine: string; whyLine: string; conceptLine: string } | null;
  lastResult: { passed: boolean; correctCount: number; total: number; percent: number } | null;
  packSource: string;
  elapsedMs: number;
  activeAppId?: string | null;
  activeAppName?: string | null;
}

export interface QuizChoice { id: string; text: string; imageTag?: string | null; audioKey?: string | null }
export interface QuizLock { remainingSeconds: number; conceptLine: string; whyLine?: string; correctChoiceText?: string; lessonTitle?: string; lessonBody?: string[] }
export interface ExplorerProgress { xp: number; explorerLevel: number; passStreakDays: number; lastPassDayKey?: string | null }
export interface StickerUnlock { unlockId: string; stickerId: string; stage: string; title: string; emoji: string; source: string; attemptId?: string | null; unlockedAtEpochMs: number; xpAtUnlock: number }
export interface LastAward { stickerTitle: string | null; stickerEmoji: string | null; stickerStageLabel: string | null; xpGained: number; leveledUp: boolean; weekendBonusApplied: boolean }
export interface NurseryVideo { videoId: string; title: string; maxSeconds: number; channel: string }
export interface NurseryTeachItem { id: string; tag: string; title: string; subtitle: string; emoji: string; speechText: string; colorHex: string; count?: number | null }
export interface NurseryState { video: NurseryVideo | null; videoCategory: string | null; items: NurseryTeachItem[] }

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
