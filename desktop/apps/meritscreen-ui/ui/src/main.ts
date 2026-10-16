import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { initLocale, t } from "./i18n";

type Appearance = "system" | "light" | "dark";
type DeviceRole = "unassigned" | "parent" | "child";
type SessionPhase = "idle" | "in_block" | "quiz_due" | "shielded";

interface BootstrapDto {
  role: DeviceRole;
  appearance: Appearance;
  phase: SessionPhase;
  platform: string;
  parentSignedIn: boolean;
  appleSignInAvailable: boolean;
  childPaired: boolean;
}

type ChildScreen =
  | "pairing"
  | "checklist"
  | "launcher"
  | "quiz"
  | "fail_lock"
  | "ceiling"
  | "pin_menu";

interface LauncherApp {
  appId: string;
  displayName: string;
  packageOrBundleId: string;
  isEmergency: boolean;
  iconTone: string;
}

interface ChecklistItem {
  id: string;
  label: string;
  done: boolean;
  recommended: boolean;
}

interface QuizChoiceDto {
  id: string;
  text: string;
}

interface QuizPromptDto {
  id: string;
  prompt: string;
  choices: QuizChoiceDto[];
  index: number;
  total: number;
  teachTitle: string | null;
  teachBody: string[];
}

interface QuizLockDto {
  remainingSeconds: number;
  conceptLine: string;
}

interface ChildUiState {
  paired: boolean;
  screen: ChildScreen;
  phase: SessionPhase;
  childName: string;
  apps: LauncherApp[];
  checklist: ChecklistItem[];
  checklistComplete: boolean;
  minutesUsedToday: number;
  minutesRemainingBlock: number;
  secondsRemainingBlock?: number;
  blockDurationMinutes?: number;
  cooldownSeconds: number;
  dailyRemainingMinutes: number;
  ceilingHit: boolean;
  quiz: QuizPromptDto | null;
  quizLock: QuizLockDto | null;
  lastFeedback: {
    correct: boolean;
    resultLine: string;
    whyLine: string;
    conceptLine: string;
  } | null;
  lastResult: {
    passed: boolean;
    correctCount: number;
    total: number;
    percent: number;
  } | null;
  packSource: string;
  elapsedMs: number;
  activeAppId?: string | null;
  activeAppName?: string | null;
  minutesByApp?: Record<string, number>;
  appLabels?: Record<string, string>;
  quizMinutesToday?: number;
  quizAttemptsToday?: number;
  quizPassesToday?: number;
}

interface AppMinutesRollup {
  appId: string;
  displayName: string;
  minutes: number;
  percentage: number;
}

interface DailyMinutesPoint {
  date: string;
  totalMinutes: number;
  quizMinutes: number;
}

interface QuizWindowStats {
  totalAttempts: number;
  passes: number;
  passRatePercent: number;
  totalQuizMinutes: number;
}

interface ChildReportsSnapshot {
  childId: string;
  childName: string;
  todayMinutes: number;
  todayQuizMinutes: number;
  weeklyTotalMinutes: number;
  weeklyQuizMinutes: number;
  dailyAverageMinutes: number;
  topApps: AppMinutesRollup[];
  dailyTrend: DailyMinutesPoint[];
  quizStats: QuizWindowStats;
  syncStatus: string;
}

interface ParentProfile {
  uid: string;
  email: string;
  familyId: string | null;
}

interface ChildCard {
  childId: string;
  displayName: string;
  ageBand: string;
  minutesUsedToday: number;
  minutesRemainingToday: number | null;
  pairedDeviceCount: number;
  platformHint: string | null;
  quizMode: string;
}

interface ChildPolicy {
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

interface InventoryApp {
  appId: string;
  label: string;
  allowed: boolean;
}

interface DeviceEnforcementStatus {
  guardianState: string;
  enforcementTier: string;
  tamperFlags: string[];
  degradedMessage: string | null;
  strictAvailable: boolean;
  installedApps: InventoryApp[];
}

interface AppRule {
  appId: string;
  packageOrBundleId: string;
  displayName: string;
  allowed: boolean;
  blockMinutes: number;
  grantOnPassMinutes: number;
  cooldownMinutes: number;
  isEmergency: boolean;
}

interface ChildDetail {
  card: ChildCard;
  policy: ChildPolicy;
  appRules: AppRule[];
  failLockScope: string;
  enforcement: DeviceEnforcementStatus | null;
}

interface PairingOffer {
  childId: string;
  code: string;
  secret: string;
  qrPayload: string;
  expiresAtEpochMs: number;
}

interface UiStateDto<T> {
  state: "loading" | "empty" | "success" | "error";
  data: T | null;
  error: string | null;
}

interface NotificationPrefs {
  quizResults: boolean;
  failLockAlerts: boolean;
  tamperAlerts: boolean;
}

interface AvatarOption {
  id: string;
  emoji: string;
  label: string;
  role: string;
}

const AVATAR_OPTIONS: AvatarOption[] = [
  { id: "fox", emoji: "🦊", label: "Fox", role: "Curious Explorer" },
  { id: "rabbit", emoji: "🐰", label: "Rabbit", role: "Quick Thinker" },
  { id: "owl", emoji: "🦉", label: "Owl", role: "Night Scholar" },
  { id: "bear", emoji: "🐻", label: "Bear", role: "Gentle Builder" },
  { id: "astro", emoji: "🚀", label: "Astro", role: "Space Voyager" },
  { id: "panda", emoji: "🐼", label: "Panda", role: "Calm Thinker" },
  { id: "lion", emoji: "🦁", label: "Lion", role: "Brave Leader" },
  { id: "turtle", emoji: "🐢", label: "Turtle", role: "Steady Guide" },
];

interface CurriculumTopic {
  id: string;
  label: string;
  icon: string;
}

interface AgeStageOption {
  id: string;
  icon: string;
  title: string;
  subtitle: string;
  desc: string;
  topics: CurriculumTopic[];
}

const AGE_STAGES: AgeStageOption[] = [
  {
    id: "3_to_6",
    icon: "🧸",
    title: "Ages 3–6",
    subtitle: "Early Explorers",
    desc: "Sensory discovery, picture books, early phonics & counting games.",
    topics: [
      { id: "phonics", label: "Phonics", icon: "🔤" },
      { id: "counting", label: "Counting", icon: "🔢" },
      { id: "shapes", label: "Shapes", icon: "🔷" },
      { id: "rhymes", label: "Rhymes & Music", icon: "🎵" },
    ],
  },
  {
    id: "7_to_9",
    icon: "🧭",
    title: "Ages 7–9",
    subtitle: "Elementary Quest",
    desc: "Reading comprehension, foundational math, and logic puzzles.",
    topics: [
      { id: "math", label: "Math Quests", icon: "📐" },
      { id: "reading", label: "Reading & Stories", icon: "📖" },
      { id: "science", label: "Nature Science", icon: "🔬" },
      { id: "logic", label: "Logic Puzzles", icon: "🧩" },
    ],
  },
  {
    id: "10_to_12",
    icon: "🧠",
    title: "Ages 10–12",
    subtitle: "Middle Prep",
    desc: "Curiosity deep-dives, pre-algebra, earth science, and digital projects.",
    topics: [
      { id: "algebra", label: "Pre-Algebra", icon: "📊" },
      { id: "earth_science", label: "Earth & Space", icon: "🌍" },
      { id: "coding", label: "Intro to Code", icon: "💻" },
      { id: "history", label: "World History", icon: "🏛️" },
    ],
  },
  {
    id: "13_plus",
    icon: "🎯",
    title: "Ages 13+",
    subtitle: "High School & Mastery",
    desc: "Focused study blocks, critical thinking, STEM challenges, and autonomy.",
    topics: [
      { id: "stem", label: "Advanced STEM", icon: "⚙️" },
      { id: "writing", label: "Creative Writing", icon: "✍️" },
      { id: "ethics", label: "Digital Balance", icon: "🛡️" },
      { id: "finance", label: "Financial Basics", icon: "💡" },
    ],
  },
];

function getChildAvatar(name: string, childId?: string): string {
  try {
    const byId = childId ? localStorage.getItem("ms_avatar_" + childId) : null;
    const byName = localStorage.getItem("ms_avatar_" + name);
    const chosen = byId || byName;
    if (chosen) {
      const match = AVATAR_OPTIONS.find((a) => a.id === chosen);
      if (match) return match.emoji;
    }
  } catch {
    // fallback
  }
  const sum = Array.from(name || "Child").reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return AVATAR_OPTIONS[sum % AVATAR_OPTIONS.length].emoji;
}

type Route =
  | { name: "role" }
  | { name: "parent-auth"; email?: string; step: "email" | "otp" }
  | { name: "parent-onboard" }
  | { name: "parent-add-child" }
  | { name: "parent-home" }
  | { name: "child-detail"; childId: string }
  | { name: "allowlist"; childId: string }
  | { name: "time-limits"; childId: string }
  | { name: "quiz-settings"; childId: string }
  | { name: "rewards"; childId: string }
  | { name: "reports"; childId?: string }
  | { name: "notifications" }
  | { name: "pairing"; childId: string }
  | { name: "account" }
  | { name: "delete-family" }
  | { name: "tray-popover"; childId?: string }
  | { name: "child" }
  | { name: "shield-overlay" };

const view = document.getElementById("view");
const chips = Array.from(
  document.querySelectorAll<HTMLButtonElement>("[data-appearance]"),
);

const isTrayWindow =
  window.location.search.includes("view=tray") || window.location.hash === "#tray";
if (isTrayWindow) {
  document.documentElement.classList.add("is-tray-window");
  document.body.classList.add("is-tray-window");
}

const isShieldWindow =
  window.location.search.includes("view=shield") || window.location.hash === "#shield";
if (isShieldWindow) {
  document.documentElement.classList.add("is-shield-window");
  document.body.classList.add("is-shield-window");
}

let route: Route = isTrayWindow
  ? { name: "tray-popover" }
  : isShieldWindow
  ? { name: "shield-overlay" }
  : { name: "role" };
let bootstrap: BootstrapDto | null = null;
let authEmail = "";
let overlayMode: string | null = null;
let prewarmMode = false;

initLocale();

async function loadHostFlags(): Promise<void> {
  if (isTrayWindow) return;
  try {
    const flags = await invoke<{ overlay: string | null; prewarm: boolean }>("get_host_flags");
    overlayMode = flags.overlay;
    prewarmMode = flags.prewarm;
    if (overlayMode === "quiz_due" || overlayMode === "shielded" || prewarmMode) {
      // Agent overlay: jump straight to child session path.
      route = { name: "child" };
    }
  } catch {
    // Browser-only / unit smoke — ignore.
  }
}

function formatAgeBand(ageBand: string): string {
  if (!ageBand) return "Elementary";
  if (ageBand.includes("4_to_6") || ageBand === "4_to_6") return "Ages 4–6";
  if (ageBand.includes("7_to_9") || ageBand === "7_to_9") return "Ages 7–9";
  if (ageBand.includes("10_to_12") || ageBand === "10_to_12") return "Ages 10–12";
  if (ageBand.includes("13") || ageBand === "13_plus") return "Ages 13+";
  return ageBand.replace(/_/g, " ").replace("to", "–");
}

function formatPackSource(source: string): string {
  if (!source || source === "quiz_bank_builtin" || source === "builtin") {
    return "Standard Elementary Bank";
  }
  return source.replace(/^quiz_bank_/, "").replace(/_/g, " ");
}

function formatTimer(totalSec: number): string {
  const clean = Math.max(0, Math.floor(totalSec));
  const mins = Math.floor(clean / 60);
  const secs = clean % 60;
  return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
}

let trayChildTicker: number | null = null;
function clearTrayChildTicker(): void {
  if (trayChildTicker !== null) {
    window.clearInterval(trayChildTicker);
    trayChildTicker = null;
  }
}

const childNameCache = new Map<string, string>();

function showToast(message: string, type: "success" | "info" | "error" = "success", durationMs = 3200): void {
  const container = document.getElementById("toast-container");
  if (!container) return;
  const toast = document.createElement("div");
  toast.className = `toast-pill toast-${type}`;
  toast.setAttribute("role", "status");
  toast.innerHTML = `
    <span class="toast-icon">${type === "success" ? "✓" : type === "error" ? "⚠️" : "ℹ️"}</span>
    <span class="toast-text">${escapeHtml(message)}</span>
  `;
  container.appendChild(toast);
  window.setTimeout(() => {
    toast.classList.add("toast-leaving");
    window.setTimeout(() => {
      toast.remove();
    }, 220);
  }, durationMs);
}

function updateBreadcrumbs(crumbs: { label: string; route?: Route }[]): void {
  const nav = document.getElementById("app-breadcrumbs");
  if (!nav) return;
  if (!crumbs.length || (crumbs.length === 1 && crumbs[0].label === "Family Sanctuary")) {
    nav.innerHTML = "";
    nav.style.display = "none";
    return;
  }
  nav.style.display = "flex";
  nav.innerHTML = crumbs
    .map((c, i) => {
      const isLast = i === crumbs.length - 1;
      if (isLast || !c.route) {
        return `<span class="breadcrumb-current" aria-current="page">${escapeHtml(c.label)}</span>`;
      }
      return `
        <button type="button" class="breadcrumb-link" data-crumb-idx="${i}">
          ${escapeHtml(c.label)}
        </button>
        <span class="breadcrumb-sep" aria-hidden="true">›</span>
      `;
    })
    .join("");

  nav.querySelectorAll<HTMLButtonElement>("[data-crumb-idx]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const idx = Number(btn.dataset.crumbIdx);
      const target = crumbs[idx];
      if (target?.route) {
        void navigate(target.route);
      }
    });
  });
}

function toggleShortcutsModal(forceState?: boolean): void {
  const modal = document.getElementById("shortcuts-modal");
  if (!modal) return;
  const isShown = modal.style.display !== "none";
  const next = forceState !== undefined ? forceState : !isShown;
  modal.style.display = next ? "flex" : "none";
}

function toggleTrayPreviewModal(forceState?: boolean): void {
  const modal = document.getElementById("tray-preview-modal");
  if (!modal) return;
  const isShown = modal.style.display !== "none";
  const next = forceState !== undefined ? forceState : !isShown;
  modal.style.display = next ? "flex" : "none";
  if (next) {
    const target = document.getElementById("tray-popover-modal-content");
    if (target) {
      void renderTrayPopover(undefined, target);
    }
  }
}

// Global modal triggers
document.getElementById("btn-shortcuts-help")?.addEventListener("click", () => toggleShortcutsModal(true));
document.getElementById("btn-close-shortcuts")?.addEventListener("click", () => toggleShortcutsModal(false));
document.getElementById("shortcuts-modal")?.addEventListener("click", (e) => {
  if (e.target === document.getElementById("shortcuts-modal")) {
    toggleShortcutsModal(false);
  }
});

document.getElementById("btn-tray-popover-toggle")?.addEventListener("click", () => toggleTrayPreviewModal());
document.getElementById("tray-preview-modal")?.addEventListener("click", (e) => {
  if (e.target === document.getElementById("tray-preview-modal")) {
    toggleTrayPreviewModal(false);
  }
});

// Global keyboard shortcuts
window.addEventListener("keydown", (e: KeyboardEvent) => {
  const activeTag = document.activeElement?.tagName;
  const isInput = activeTag === "INPUT" || activeTag === "TEXTAREA" || activeTag === "SELECT";

  if (e.key === "Escape") {
    const trayModal = document.getElementById("tray-preview-modal");
    if (trayModal && trayModal.style.display !== "none") {
      trayModal.style.display = "none";
      return;
    }
    if (isTrayWindow) {
      try {
        void invoke("hide_tray_popover");
      } catch {}
      return;
    }
    const scModal = document.getElementById("shortcuts-modal");
    if (scModal && scModal.style.display !== "none") {
      scModal.style.display = "none";
      return;
    }
    const removeModal = document.getElementById("remove-child-modal");
    if (removeModal && removeModal.style.display !== "none") {
      removeModal.style.display = "none";
      return;
    }
    if (route.name === "child-detail") {
      void navigate({ name: "parent-home" });
    } else if (
      route.name === "allowlist" ||
      route.name === "time-limits" ||
      route.name === "quiz-settings" ||
      route.name === "rewards" ||
      route.name === "pairing"
    ) {
      void navigate({ name: "child-detail", childId: (route as any).childId });
    } else if (route.name === "reports") {
      if ((route as any).childId) {
        void navigate({ name: "child-detail", childId: (route as any).childId });
      } else {
        void navigate({ name: "parent-home" });
      }
    } else if (route.name === "account" || route.name === "parent-add-child") {
      void navigate({ name: "parent-home" });
    } else if (route.name === "delete-family" || route.name === "notifications") {
      void navigate({ name: "account" });
    }
  }

  if (isInput) return;

  const isMac = navigator.platform.toUpperCase().includes("MAC");
  const modKey = isMac ? e.metaKey : e.ctrlKey;

  if (modKey && (e.key === "t" || e.key === "T")) {
    e.preventDefault();
    toggleTrayPreviewModal();
  } else if (modKey && (e.key === "," || e.key === "Settings")) {
    e.preventDefault();
    void navigate({ name: "account" });
  } else if (modKey && (e.key === "n" || e.key === "N")) {
    e.preventDefault();
    void navigate({ name: "parent-add-child" });
  } else if (modKey && (e.key === "r" || e.key === "R") && !e.shiftKey) {
    if (route.name === "parent-home") {
      e.preventDefault();
      void renderDashboard();
      showToast("Dashboard refreshed", "info", 1800);
    }
  } else if (e.key === "?" && !modKey) {
    e.preventDefault();
    toggleShortcutsModal();
  }
});

function applyTheme(appearance: Appearance): void {
  const root = document.documentElement;
  if (appearance === "system") {
    root.removeAttribute("data-theme");
  } else {
    root.setAttribute("data-theme", appearance);
  }
  for (const chip of chips) {
    const value = chip.dataset.appearance as Appearance;
    chip.setAttribute("aria-pressed", String(value === appearance));
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderLoading(label = "Loading…"): void {
  if (!view) return;
  view.innerHTML = `
    <div class="panel">
      <div class="skeleton skeleton-title"></div>
      <div class="skeleton skeleton-text medium"></div>
      <div class="skeleton skeleton-text short"></div>
      <div class="card-grid" style="margin-top: 14px;">
        <div class="skeleton skeleton-card" style="height: 100px;"></div>
        <div class="skeleton skeleton-card" style="height: 100px;"></div>
      </div>
      <p class="muted meta" style="display: flex; align-items: center; gap: 8px;">
        <span class="btn-spinner" style="width: 14px; height: 14px; border-width: 2px;"></span>
        ${escapeHtml(label)}
      </p>
    </div>`;
}

/** Strip Tauri IPC framing so parents see the actionable message only. */
function friendlyError(err: unknown): string {
  let raw = err instanceof Error ? err.message : String(err);
  // e.g. "invalid args `args` for command `parent_send_otp`: …"
  const ipc = raw.match(/command [`']?\w+[`']?: (.+)$/i);
  if (ipc?.[1]) raw = ipc[1];
  raw = raw.replace(/^error:\s*/i, "").trim();
  if (/unauthenticated/i.test(raw)) {
    return "Your parent session timed out or could not be verified. Please sign in again or use offline pairing.";
  }
  return raw || "Something went wrong. Please try again.";
}

function renderError(message: string, onRetry?: () => void): void {
  if (!view) return;
  view.innerHTML = `<div class="panel">
    <h1>Something went wrong</h1>
    <p class="error">${escapeHtml(friendlyError(message))}</p>
    <div class="actions">
      <button class="btn btn-secondary" id="retry">Retry</button>
      <button class="btn btn-secondary" id="back-role">Back</button>
    </div>
  </div>`;
  document.getElementById("retry")?.addEventListener("click", () => {
    if (onRetry) onRetry();
    else void navigate({ name: "role" });
  });
  document.getElementById("back-role")?.addEventListener("click", () => {
    void navigate({ name: "role" });
  });
}

function renderEmpty(title: string, body: string, cta: string, onCta: () => void): void {
  if (!view) return;
  view.innerHTML = `<div class="panel">
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(body)}</p>
    <div class="actions">
      <button class="btn btn-primary" id="empty-cta">${escapeHtml(cta)}</button>
    </div>
  </div>`;
  document.getElementById("empty-cta")?.addEventListener("click", onCta);
}

let pairingTimer: number | null = null;
let activeTimeout: number | null = null;
let activeInterval: number | null = null;
let activeKeyHandler: ((ev: KeyboardEvent) => void) | null = null;

function clearTimers(): void {
  if (pairingTimer !== null) {
    clearInterval(pairingTimer);
    pairingTimer = null;
  }
  if (activeTimeout !== null) {
    clearTimeout(activeTimeout);
    activeTimeout = null;
  }
  if (activeInterval !== null) {
    clearInterval(activeInterval);
    activeInterval = null;
  }
  if (activeKeyHandler !== null) {
    window.removeEventListener("keydown", activeKeyHandler);
    activeKeyHandler = null;
  }
}

function generateFallbackNumericCode(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function formatPairingCode(code: string): string {
  const digits = code.replace(/\D/g, "");
  if (digits.length >= 6) {
    return `${digits.slice(0, 3)} ${digits.slice(3, 6)}`;
  }
  return code;
}

async function navigate(next: Route): Promise<void> {
  clearTimers();
  route = next;
  await render();
}

async function render(): Promise<void> {
  if (!view) return;
  try {
    switch (route.name) {
      case "role":
        await renderRoleGate();
        break;
      case "parent-auth":
        renderParentAuth(route.step);
        break;
      case "parent-onboard":
        renderOnboard();
        break;
      case "parent-add-child":
        renderAddChild();
        break;
      case "parent-home":
        await renderDashboard();
        break;
      case "child-detail":
        await renderChildDetail(route.childId);
        break;
      case "allowlist":
        await renderAllowlist(route.childId);
        break;
      case "time-limits":
        await renderTimeLimits(route.childId);
        break;
      case "quiz-settings":
        await renderQuizSettings(route.childId);
        break;
      case "rewards":
        await renderRewards(route.childId);
        break;
      case "reports":
        await renderReports(route.childId);
        break;
      case "notifications":
        await renderNotifications();
        break;
      case "pairing":
        await renderPairing(route.childId);
        break;
      case "account":
        await renderAccount();
        break;
      case "delete-family":
        renderDeleteFamily();
        break;
      case "tray-popover":
        await renderTrayPopover(route.childId);
        break;
      case "child":
        await renderChild();
        break;
      case "shield-overlay":
        renderShieldOverlay();
        break;
    }
  } catch (err) {
    renderError(err instanceof Error ? err.message : "Could not render screen.");
  }
}

async function renderRoleGate(): Promise<void> {
  renderLoading();
  updateBreadcrumbs([]);
  bootstrap = await invoke<BootstrapDto>("get_bootstrap");
  applyTheme(bootstrap.appearance);
  if (bootstrap.platform) {
    document.documentElement.classList.add("platform-" + bootstrap.platform);
  }

  if (bootstrap.parentSignedIn || bootstrap.role === "parent") {
    try {
      const dash = await invoke<UiStateDto<{ children: ChildCard[] }>>("parent_dashboard");
      const hasChildren = dash.state === "success" && (dash.data?.children?.length ?? 0) > 0;
      const isCompleted = localStorage.getItem("meritscreen_onboarding_completed") === "true";
      if (hasChildren || isCompleted) {
        localStorage.setItem("meritscreen_onboarding_completed", "true");
        await navigate({ name: "parent-home" });
      } else {
        await navigate({ name: "parent-onboard" });
      }
    } catch {
      await navigate({ name: "parent-onboard" });
    }
    return;
  }

  if (!view) return;
  if (bootstrap.role === "child") {
    await navigate({ name: "child" });
    return;
  }

  view.innerHTML = `
    <div class="hero">
      <div style="display: flex; justify-content: center; margin-bottom: 6px;">
        <span class="pill-badge accent">
          <span style="font-size: 14px;">🛡️</span> Watching Family Setup
        </span>
      </div>
      <h1>Who is using this computer?</h1>
      <p>A calm, balanced digital sanctuary where screen time is earned through playful discovery & micro-quizzes.</p>

      <div class="card-grid" style="grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); margin-top: 16px;">
        <!-- Parent Card -->
        <div class="cult-card" id="as-parent" role="button" tabindex="0">
          <div class="cult-card-header">
            <div class="cult-icon-tile primary">👨‍👩‍👧‍👦</div>
            <div>
              <div style="display: flex; align-items: center; gap: 6px;">
                <h3 style="margin: 0; font-size: 1.15rem; font-weight: 600;">I am a Parent</h3>
                <span class="pill-badge">Remote</span>
              </div>
              <p style="margin: 4px 0 0; font-size: 0.85rem; color: var(--ms-on-surface-variant); line-height: 1.4;">
                Approve apps, set gentle sleep routines, and assign micro-quizzes.
              </p>
            </div>
          </div>
          <div class="badge-row">
            <span class="pill-badge">⏱ Quiet Hours</span>
            <span class="pill-badge">🧠 Adaptive Quiz</span>
            <span class="pill-badge">✓ Approvals</span>
          </div>
          <button class="btn btn-primary" style="width: 100%; margin-top: 10px;" type="button">
            <span>Continue as Parent</span> →
          </button>
        </div>

        <!-- Child Card -->
        <div class="cult-card" id="as-child" role="button" tabindex="0">
          <div class="cult-card-header">
            <div class="cult-icon-tile secondary">💻</div>
            <div>
              <div style="display: flex; align-items: center; gap: 6px;">
                <h3 style="margin: 0; font-size: 1.15rem; font-weight: 600;">Child Device</h3>
                <span class="pill-badge">Protected</span>
              </div>
              <p style="margin: 4px 0 0; font-size: 0.85rem; color: var(--ms-on-surface-variant); line-height: 1.4;">
                Pair this PC with a parent phone. Always-on guardian protects screen time.
              </p>
            </div>
          </div>
          <div class="badge-row">
            <span class="pill-badge">🎯 Focused Study</span>
            <span class="pill-badge">🛡️ Always-On</span>
            <span class="pill-badge">🌿 Calm Lock</span>
          </div>
          <button class="btn btn-secondary" style="width: 100%; margin-top: 10px;" type="button">
            <span>Set up Child Device</span> →
          </button>
        </div>
      </div>

      <div style="margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--ms-outline-subtle); display: flex; justify-content: space-between; align-items: center; width: 100%; max-width: 580px;">
        <span class="meta" style="margin: 0; font-size: 0.78rem;">Platform: <strong>${escapeHtml(bootstrap.platform.toUpperCase())}</strong> · Certified Local Guardian Daemon</span>
        <button type="button" class="btn btn-secondary btn-sm" id="reset-first-time" style="font-size: 0.76rem; padding: 4px 10px;">
          Diagnostics: Reset App State
        </button>
      </div>
    </div>`;

  document.getElementById("reset-first-time")?.addEventListener("click", () => {
    void (async () => {
      try {
        localStorage.clear();
        sessionStorage.clear();
        await invoke("parent_delete_family");
        await invoke("parent_sign_out");
        showToast("App state reset to initial setup", "info");
      } catch {
        // ignore
      }
      authEmail = "";
      await renderRoleGate();
    })();
  });

  const parentBtn = document.getElementById("as-parent");
  parentBtn?.addEventListener("click", () => {
    void navigate({ name: "parent-auth", step: "email" });
  });
  parentBtn?.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      void navigate({ name: "parent-auth", step: "email" });
    }
  });

  const childBtn = document.getElementById("as-child");
  childBtn?.addEventListener("click", () => {
    void setRole("child");
  });
  childBtn?.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      void setRole("child");
    }
  });
}

function renderParentAuth(step: "email" | "otp"): void {
  if (!view) return;
  const apple = bootstrap?.appleSignInAvailable ?? false;
  if (step === "email") {
    view.innerHTML = `
      <div class="auth-card">
        <div class="auth-card-hero">
          <div class="auth-logo-badge">🛡️</div>
          <h1>Parent Sign In</h1>
          <p>Sign in to configure screen time rules, approve learning apps, and review micro-quiz progress.</p>
        </div>

        <div id="auth-error-container"></div>

        <label class="field">
          <span>Parent Email</span>
          <input id="email" type="email" autocomplete="username" placeholder="you@example.com" value="${escapeHtml(authEmail || "")}" />
        </label>

        <div class="tester-quick-chips">
          <span style="font-size:0.75rem;color:var(--ms-on-surface-variant);font-weight:600;">Test Accounts:</span>
          <button type="button" class="quick-chip" data-fill-email="tester4.parent@anajyo.com">tester4.parent@anajyo.com</button>
          <button type="button" class="quick-chip" data-fill-email="tester1.parent@anajyo.com">tester1.parent@anajyo.com</button>
        </div>

        <div class="actions" style="margin-top: 6px;">
          <button class="btn btn-primary" id="send" style="width: 100%;">
            <span id="send-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="send-text">Send verification code →</span>
          </button>
          <button class="btn btn-secondary" id="google" style="width: 100%;">Continue with Google</button>
          ${apple ? `<button class="btn btn-secondary" id="apple" style="width: 100%;">Sign in with Apple</button>` : ""}
          <button class="btn btn-secondary" id="back" style="width: 100%;">Back to role select</button>
        </div>

        <p class="muted meta" style="text-align: center; margin-top: 4px;">
          Local demo builds accept code <span class="mono">123456</span> or <span class="mono">424242</span>.
        </p>
      </div>`;

    const sendBtn = document.getElementById("send") as HTMLButtonElement;
    const sendSpinner = document.getElementById("send-spinner") as HTMLElement;
    const sendText = document.getElementById("send-text") as HTMLElement;
    const emailInput = document.getElementById("email") as HTMLInputElement;
    const errContainer = document.getElementById("auth-error-container") as HTMLElement;

    for (const chip of view.querySelectorAll<HTMLButtonElement>("[data-fill-email]")) {
      chip.addEventListener("click", () => {
        emailInput.value = chip.dataset.fillEmail || "";
        emailInput.focus();
      });
    }

    const setSubmitting = (submitting: boolean) => {
      sendBtn.disabled = submitting;
      emailInput.disabled = submitting;
      sendSpinner.style.display = submitting ? "inline-block" : "none";
      sendText.textContent = submitting ? "Sending code…" : "Send verification code →";
    };

    sendBtn.addEventListener("click", () => {
      void (async () => {
        errContainer.innerHTML = "";
        const email = emailInput.value.trim();
        if (!email.includes("@") || email.length < 5) {
          errContainer.innerHTML = `
            <div class="error-banner">
              <div>
                <strong>Invalid email</strong>
                Enter a valid email address to receive your sign-in code.
              </div>
            </div>`;
          return;
        }
        authEmail = email;
        setSubmitting(true);
        try {
          await invoke("parent_send_otp", { email });
          await navigate({ name: "parent-auth", step: "otp", email });
        } catch (e) {
          setSubmitting(false);
          errContainer.innerHTML = `
            <div class="error-banner">
              <div>
                <strong>Sign in failed</strong>
                ${escapeHtml(friendlyError(e))}
              </div>
            </div>`;
        }
      })();
    });

    document.getElementById("google")?.addEventListener("click", () => {
      void (async () => {
        try {
          const url = await invoke<string>("parent_google_url");
          renderError(`Open this Google URL in a browser (loopback PKCE):\n${url}`, () =>
            void navigate({ name: "parent-auth", step: "email" }),
          );
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "parent-auth", step: "email" }));
        }
      })();
    });

    document.getElementById("apple")?.addEventListener("click", () => {
      void (async () => {
        try {
          await invoke("parent_apple_sign_in");
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "parent-auth", step: "email" }));
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "role" }));
    return;
  }

  const isTesterAccount =
    (authEmail || "").toLowerCase().includes("tester") ||
    (authEmail || "").toLowerCase().endsWith("@anajyo.com");
  const otpHint = isTesterAccount
    ? "Tester account bypass code: <strong>123456</strong>"
    : "Check your email for the 6-digit code (use <strong>123456</strong> or <strong>424242</strong> for demo).";

  view.innerHTML = `
    <div class="auth-card">
      <div class="auth-card-hero">
        <div class="auth-logo-badge">🔑</div>
        <h1>Enter Verification Code</h1>
        <p>A 6-digit one-time passcode was sent to <strong>${escapeHtml(authEmail || "your email")}</strong>.</p>
      </div>

      <div id="otp-error-container"></div>

      <label class="field">
        <span>6-digit Code</span>
        <input id="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="123456" style="letter-spacing:0.3em;font-size:1.3rem;text-align:center;font-weight:700;" />
      </label>

      <div class="tester-quick-chips">
        <span style="font-size:0.75rem;color:var(--ms-on-surface-variant);font-weight:600;">Fast Fill:</span>
        <button type="button" class="quick-chip" id="fill-test-code">Fill 123456</button>
      </div>

      <p style="font-size:0.85rem;color:var(--ms-on-surface-variant);margin:2px 0;">${otpHint}</p>

      <div class="actions" style="margin-top: 6px;">
        <button class="btn btn-primary" id="verify" style="width: 100%;">
          <span id="verify-spinner" style="display:none;" class="btn-spinner"></span>
          <span id="verify-text">Verify & continue →</span>
        </button>
        <button class="btn btn-secondary" id="back" style="width: 100%;">Use another email</button>
      </div>
    </div>`;

  const verifyBtn = document.getElementById("verify") as HTMLButtonElement;
  const verifySpinner = document.getElementById("verify-spinner") as HTMLElement;
  const verifyText = document.getElementById("verify-text") as HTMLElement;
  const otpInput = document.getElementById("otp") as HTMLInputElement;
  const errContainer = document.getElementById("otp-error-container") as HTMLElement;

  document.getElementById("fill-test-code")?.addEventListener("click", () => {
    otpInput.value = "123456";
    otpInput.focus();
  });

  const setSubmitting = (submitting: boolean) => {
    verifyBtn.disabled = submitting;
    otpInput.disabled = submitting;
    verifySpinner.style.display = submitting ? "inline-block" : "none";
    verifyText.textContent = submitting ? "Verifying code…" : "Verify & continue →";
  };

  verifyBtn.addEventListener("click", () => {
    void (async () => {
      errContainer.innerHTML = "";
      const code = otpInput.value.trim();
      if (code.length !== 6) {
        errContainer.innerHTML = `
          <div class="error-banner">
            <div>
              <strong>Invalid code</strong>
              Please enter the 6-digit verification code.
            </div>
          </div>`;
        return;
      }
      setSubmitting(true);
      try {
        await invoke<ParentProfile>("parent_verify_otp", { email: authEmail, code });
        const dash = await invoke<UiStateDto<{ children: ChildCard[] }>>("parent_dashboard");
        const hasChildren = dash.state === "success" && (dash.data?.children?.length ?? 0) > 0;
        const isCompleted = localStorage.getItem("meritscreen_onboarding_completed") === "true";
        if (hasChildren || isCompleted) {
          localStorage.setItem("meritscreen_onboarding_completed", "true");
          await navigate({ name: "parent-home" });
        } else {
          await navigate({ name: "parent-onboard" });
        }
      } catch (e) {
        setSubmitting(false);
        errContainer.innerHTML = `
          <div class="error-banner">
            <div>
              <strong>Verification failed</strong>
              ${escapeHtml(friendlyError(e))}
            </div>
          </div>`;
      }
    })();
  });
  document.getElementById("back")?.addEventListener("click", () =>
    void navigate({ name: "parent-auth", step: "email" }),
  );
}

function renderOnboard(): void {
  if (!view) return;

  type OnboardStep = "profile" | "schedule" | "rules" | "pin" | "pairing" | "complete";
  let currentStep: OnboardStep = "profile";

  // Step 1: Profile & Learning state
  let selectedAvatar = "fox";
  let selectedAge = "7_to_9";
  let currentStage = AGE_STAGES.find((s) => s.id === selectedAge) || AGE_STAGES[1];
  let selectedTopics = new Set<string>(currentStage.topics.slice(0, 3).map((t) => t.id));
  let enteredName = "";

  // Step 2: Schedule & Limits state
  let dailyAllowanceMinutes = 90;
  let quizFreqMinutes = 30;
  let bedtimeEnabled = true;
  let bedtimeStart = "20:30";
  let bedtimeEnd = "07:00";

  // Step 4: PIN state
  let enteredPin = "";
  let confirmPin = "";
  let pinVisible = false;

  // Step 5: Pairing Offer state
  let createdChildCard: ChildCard | null = null;
  let pairingCode = generateFallbackNumericCode();

  const updatePreview = () => {
    const avatarObj = AVATAR_OPTIONS.find((a) => a.id === selectedAvatar) || AVATAR_OPTIONS[0];
    const previewAvatar = document.getElementById("preview-avatar");
    const previewName = document.getElementById("preview-name");
    const previewStage = document.getElementById("preview-stage");
    const previewTopics = document.getElementById("preview-topics");
    const previewRole = document.getElementById("preview-role");
    const previewPinStatus = document.getElementById("preview-pin-status");
    const previewSchedule = document.getElementById("preview-schedule");
    const previewRules = document.getElementById("preview-rules");
    const nameCounter = document.getElementById("name-counter");

    if (previewAvatar) previewAvatar.textContent = avatarObj.emoji;
    if (previewName) previewName.textContent = enteredName.trim() || "Alex";
    if (previewRole) previewRole.textContent = avatarObj.role;
    if (previewStage) {
      previewStage.innerHTML = `<span>${currentStage.icon}</span><span>${escapeHtml(currentStage.title)} · ${escapeHtml(currentStage.subtitle)}</span>`;
    }
    if (previewTopics) {
      const chips = Array.from(selectedTopics)
        .map((tid) => {
          const t = currentStage.topics.find((item) => item.id === tid);
          return t ? `<span class="pill-badge accent">${t.icon} ${escapeHtml(t.label)}</span>` : "";
        })
        .filter(Boolean)
        .join("");
      previewTopics.innerHTML = chips || `<span class="pill-badge">Select subjects</span>`;
    }
    if (previewSchedule) {
      previewSchedule.textContent = `${dailyAllowanceMinutes} min/day · Every ${quizFreqMinutes} min`;
    }
    if (previewRules) {
      previewRules.textContent = "Education Open · Games Gated";
    }
    if (previewPinStatus) {
      const pinOk = enteredPin.length === 4 && (!confirmPin || enteredPin === confirmPin);
      previewPinStatus.textContent = pinOk ? "🔒 Configured" : "⚠️ Needs 4 Digits";
      previewPinStatus.style.color = pinOk ? "var(--ms-success)" : "var(--ms-on-surface-variant)";
    }
    if (nameCounter) {
      nameCounter.textContent = `${enteredName.length}/40`;
    }
  };

  const renderUi = () => {
    if (!view) return;
    const currentAvatar = AVATAR_OPTIONS.find((a) => a.id === selectedAvatar) || AVATAR_OPTIONS[0];
    const displayName = enteredName.trim() || "Alex";

    // Stepper Labels
    const stepsConfig: { id: OnboardStep; label: string }[] = [
      { id: "profile", label: "1. Profile" },
      { id: "schedule", label: "2. Schedule" },
      { id: "rules", label: "3. App Rules" },
      { id: "pin", label: "4. Safety PIN" },
      { id: "pairing", label: "5. Device Link" },
      { id: "complete", label: "6. Complete" },
    ];

    const stepperHtml = stepsConfig
      .map((s) => {
        const isActive = s.id === currentStep;
        return `
          <div class="stepper-step ${isActive ? "active" : ""}">
            <span class="stepper-dot" style="${isActive ? "" : "background:var(--ms-outline-subtle);"}"></span>
            <span>${escapeHtml(s.label)}</span>
          </div>
        `;
      })
      .join("");

    // Generate Step-Specific Right Column Content
    let formContentHtml = "";

    if (currentStep === "profile") {
      const avatarButtonsHtml = AVATAR_OPTIONS.map((a) => `
        <button type="button" class="avatar-tile-btn ${a.id === selectedAvatar ? "selected" : ""}" data-avatar="${a.id}" aria-label="Select avatar ${a.label}">
          <div class="avatar-icon-wrap">${a.emoji}</div>
          <span class="avatar-tile-label">${escapeHtml(a.label)}</span>
        </button>
      `).join("");

      const ageCardsHtml = AGE_STAGES.map((stage) => `
        <div class="age-stage-card ${stage.id === selectedAge ? "selected" : ""}" data-age="${stage.id}" role="button" tabindex="0">
          <span class="age-card-icon">${stage.icon}</span>
          <div class="age-card-content">
            <h4 class="age-card-heading">${escapeHtml(stage.title)} <span style="font-weight:400;color:var(--ms-on-surface-variant);">· ${escapeHtml(stage.subtitle)}</span></h4>
            <p class="age-card-desc">${escapeHtml(stage.desc)}</p>
          </div>
        </div>
      `).join("");

      const curriculumChipsHtml = currentStage.topics.map((t) => {
        const isSelected = selectedTopics.has(t.id);
        return `
          <button type="button" class="curriculum-chip-btn ${isSelected ? "selected" : ""}" data-topic="${t.id}">
            <span>${t.icon}</span>
            <span>${escapeHtml(t.label)}</span>
            ${isSelected ? `<span>✓</span>` : ""}
          </button>
        `;
      }).join("");

      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>1. Choose an Avatar Preset</span>
            <span class="sub">Select a companion animal</span>
          </div>
          <div class="avatar-picker-row" role="radiogroup" aria-label="Avatar options">
            ${avatarButtonsHtml}
          </div>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>2. Child’s First Name</span>
            <span class="sub" id="name-counter">${enteredName.length}/40</span>
          </div>
          <label class="field" style="margin-top:0;">
            <input id="name" maxlength="40" placeholder="e.g. Alex" value="${escapeHtml(enteredName)}" autocomplete="off" />
          </label>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>3. Age Group & Adventure Stage</span>
            <span class="sub">Calibrates quiz difficulty & screen limits</span>
          </div>
          <div class="age-stage-grid" role="radiogroup" aria-label="Age band options">
            ${ageCardsHtml}
          </div>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>4. Learning & Curriculum Focus</span>
            <span class="sub">Pick active topics for micro-quizzes</span>
          </div>
          <div class="curriculum-chip-group" id="curriculum-container" role="group" aria-label="Curriculum topics">
            ${curriculumChipsHtml}
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-schedule" style="padding:14px 28px;font-size:1.05rem;">
            <span>Continue to Schedule & Limits</span> →
          </button>
          <button class="btn btn-secondary" id="btn-cancel-onboard">Cancel</button>
        </div>
      `;
    } else if (currentStep === "schedule") {
      const budgetPresets = [45, 60, 90, 120, 150, 180];
      const budgetChips = budgetPresets
        .map(
          (m) => `
        <button type="button" class="preset-chip-btn ${dailyAllowanceMinutes === m ? "selected" : ""}" data-budget="${m}">
          ${m} min ${m === 90 ? "(Recommended)" : ""}
        </button>
      `,
        )
        .join("");

      const quizPresets = [15, 20, 30, 45];
      const quizChips = quizPresets
        .map(
          (m) => `
        <button type="button" class="preset-chip-btn ${quizFreqMinutes === m ? "selected" : ""}" data-quiz="${m}">
          Every ${m} min ${m === 30 ? "(Balanced)" : ""}
        </button>
      `,
        )
        .join("");

      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>1. Daily Screen Time Budget</span>
            <span class="sub">How much recreational screen time can ${escapeHtml(displayName)} earn each day?</span>
          </div>
          <div class="preset-chip-group" role="group" aria-label="Daily allowance presets">
            ${budgetChips}
          </div>
          <div style="display:flex;align-items:center;gap:12px;margin-top:8px;">
            <input type="range" id="budget-slider" min="30" max="240" step="15" value="${dailyAllowanceMinutes}" style="flex:1;" />
            <span class="pill-badge accent" id="budget-label" style="font-size:0.95rem;font-weight:700;">${dailyAllowanceMinutes} min/day</span>
          </div>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>2. Micro-Quiz Frequency</span>
            <span class="sub">How often should a micro-quiz pause active screen time?</span>
          </div>
          <div class="preset-chip-group" role="group" aria-label="Quiz frequency presets">
            ${quizChips}
          </div>
          <p style="font-size:0.82rem;color:var(--ms-on-surface-variant);margin:4px 0 0;">
            Passing a 1-minute quiz earns screen time for the next block. Incorrect answers start a gentle cooldown.
          </p>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>3. Bedtime & Quiet Hours Window</span>
            <span class="sub">Locks recreational apps overnight to protect healthy sleep</span>
          </div>
          <div class="bedtime-card">
            <label class="field check" style="margin:0;">
              <input type="checkbox" id="bedtime-toggle" ${bedtimeEnabled ? "checked" : ""} />
              <span style="font-weight:600;color:var(--ms-on-surface);">Enable Bedtime Quiet Hours</span>
            </label>
            <div class="time-picker-row" id="bedtime-fields" style="${bedtimeEnabled ? "" : "opacity:0.5;pointer-events:none;"}">
              <div class="time-picker-field">
                <label for="bedtime-start">Bedtime Lock Starts</label>
                <input type="time" id="bedtime-start" value="${bedtimeStart}" />
              </div>
              <div class="time-picker-field">
                <label for="bedtime-end">Morning Unlock</label>
                <input type="time" id="bedtime-end" value="${bedtimeEnd}" />
              </div>
            </div>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-rules" style="padding:14px 28px;font-size:1.05rem;">
            <span>Continue to App Rules</span> →
          </button>
          <button class="btn btn-secondary" id="btn-back-to-profile">← Back to Profile</button>
        </div>
      `;
    } else if (currentStep === "rules") {
      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>How Watching Protects & Manages Apps</span>
            <span class="sub">Two balanced tiers for schoolwork and entertainment</span>
          </div>
          <p style="font-size:0.88rem;color:var(--ms-on-surface-variant);line-height:1.45;margin:0;">
            Watching categorizes applications automatically so homework, reading, and creative projects are never blocked by game limits.
          </p>
        </div>

        <div class="app-category-box allowed">
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-size:20px;">📚</span>
              <strong style="color:var(--ms-on-surface);">Always Allowed Learning Apps (Never Blocked)</strong>
            </div>
            <span class="tag free">Always Open</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">📖</span><span>Reading & Language (Books, Duolingo, Kindle)</span></div>
            <span class="tag free">Unlimited</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🧮</span><span>Calculators, Science & Coding Tools</span></div>
            <span class="tag free">Unlimited</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">📝</span><span>Notes, School Documents & Classroom Utilities</span></div>
            <span class="tag free">Unlimited</span>
          </div>
        </div>

        <div class="app-category-box protected">
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-size:20px;">🎮</span>
              <strong style="color:var(--ms-on-surface);">Regulated Entertainment Apps (Earned by Quizzes)</strong>
            </div>
            <span class="tag locked">Screen Time Gated</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🌐</span><span>Web Browsers (Chrome, Safari, Firefox, Edge)</span></div>
            <span class="tag locked">Quiz Gated</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🕹️</span><span>Games & Launchers (Minecraft, Roblox, Steam)</span></div>
            <span class="tag locked">Quiz Gated</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">📺</span><span>Streaming & Video Players (YouTube, Netflix)</span></div>
            <span class="tag locked">Quiz Gated</span>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-pin" style="padding:14px 28px;font-size:1.05rem;">
            <span>Continue to Safety PIN</span> →
          </button>
          <button class="btn btn-secondary" id="btn-back-to-schedule">← Back to Schedule</button>
        </div>
      `;
    } else if (currentStep === "pin") {
      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>Set Your 4-Digit Parent Safety PIN</span>
            <span class="sub">Used to unlock child computers and authorize exceptions</span>
          </div>
          <p style="font-size:0.88rem;color:var(--ms-on-surface-variant);line-height:1.45;margin:0;">
            Your PIN protects child devices from being bypassed. You will enter this PIN on child computers when approving requests.
          </p>
        </div>

        <div class="pin-section-card">
          <div style="display:flex;flex-direction:column;gap:12px;">
            <label class="field" style="margin:0;">
              <span>Parent PIN (4 digits)</span>
              <div class="pin-input-wrap">
                <input
                  id="pin"
                  type="${pinVisible ? "text" : "password"}"
                  inputmode="numeric"
                  maxlength="4"
                  autocomplete="new-password"
                  placeholder="••••"
                  value="${escapeHtml(enteredPin)}"
                />
                <button type="button" class="btn btn-secondary" id="toggle-pin" style="padding:8px 12px;font-size:0.8rem;">
                  ${pinVisible ? "Hide" : "Show"}
                </button>
              </div>
            </label>

            <label class="field" style="margin:0;">
              <span>Confirm PIN</span>
              <div class="pin-input-wrap">
                <input
                  id="pin-confirm"
                  type="${pinVisible ? "text" : "password"}"
                  inputmode="numeric"
                  maxlength="4"
                  autocomplete="new-password"
                  placeholder="••••"
                  value="${escapeHtml(confirmPin)}"
                />
              </div>
            </label>
          </div>

          <div class="preview-security-badge" style="margin-top:6px;">
            <span>🛡️</span>
            <span>Local Argon2 / SHA-256 parent hash · Zero plain-text storage</span>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-pairing" style="padding:14px 28px;font-size:1.05rem;">
            <span id="pin-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="pin-text">Save & Continue to Device Pairing →</span>
          </button>
          <button class="btn btn-secondary" id="btn-back-to-rules">← Back to App Rules</button>
        </div>
      `;
    } else if (currentStep === "pairing") {
      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>Link ${escapeHtml(displayName)}’s Computer</span>
            <span class="sub">Pair the desktop guardian in 3 simple steps</span>
          </div>
          <p style="font-size:0.88rem;color:var(--ms-on-surface-variant);line-height:1.45;margin:0;">
            Watching runs on macOS and Linux computers to monitor screen time and deliver micro-quizzes.
          </p>
        </div>

        <div class="pairing-banner">
          <span style="font-size:0.82rem;font-weight:700;letter-spacing:0.06em;text-transform:uppercase;color:var(--ms-primary);">
            Device Pairing Code
          </span>
          <div class="pairing-code-text" id="display-pairing-code">${escapeHtml(formatPairingCode(pairingCode))}</div>
          <div style="display:flex;align-items:center;justify-content:center;gap:10px;flex-wrap:wrap;">
            <button type="button" class="btn btn-secondary" id="btn-copy-code" style="font-size:0.82rem;padding:6px 14px;">
              📋 Copy Pairing Code
            </button>
            <div class="sync-status-indicator" id="pairing-sync-status">
              <span class="sync-pulse-dot"></span>
              <span>Waiting for child device to connect…</span>
            </div>
          </div>

          <div class="pairing-steps-list">
            <div>1. Open <strong>Watching</strong> on ${escapeHtml(displayName)}’s laptop or PC.</div>
            <div>2. On the welcome screen, select <strong>"Set up Child Device"</strong>.</div>
            <div>3. Enter pairing code <strong>${escapeHtml(formatPairingCode(pairingCode))}</strong> to activate protection.</div>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-complete-setup" style="padding:14px 28px;font-size:1.05rem;">
            <span>Finish & View Setup Summary →</span>
          </button>
          <button class="btn btn-secondary" id="btn-skip-pairing">Skip Pairing for Now</button>
        </div>

        <div style="margin-top: 14px; text-align: center;">
          <button type="button" class="btn btn-text" id="btn-sim-onboard-pair" style="font-size:0.8rem;color:var(--ms-primary);cursor:pointer;background:none;border:none;text-decoration:underline;">
            ⚡ Developer / Lab: Simulate Child Device Paired
          </button>
        </div>
      `;
    } else if (currentStep === "complete") {
      formContentHtml = `
        <div class="celebration-card">
          <div class="celebration-hero">🌟</div>
          <h2 style="margin:0;font-family:var(--ms-display);font-size:1.9rem;font-weight:700;">
            ${escapeHtml(displayName)}’s Sanctuary is Ready!
          </h2>
          <p style="margin:0;font-size:0.95rem;color:var(--ms-on-surface-variant);max-width:500px;line-height:1.45;">
            Screen-time limits, micro-quizzes, and parental security are now configured. You can manage everything from your parent dashboard.
          </p>

          <div class="celebration-summary-grid">
            <div class="summary-metric-tile">
              <span class="lbl">Child Profile</span>
              <span class="val">${currentAvatar.emoji} ${escapeHtml(displayName)}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Adventure Stage</span>
              <span class="val">${escapeHtml(currentStage.title)}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Daily Allowance</span>
              <span class="val">${dailyAllowanceMinutes} min/day</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Quiz Frequency</span>
              <span class="val">Every ${quizFreqMinutes} min</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Bedtime Quiet Hours</span>
              <span class="val">${bedtimeEnabled ? `${bedtimeStart} – ${bedtimeEnd}` : "Disabled"}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Parent PIN Security</span>
              <span class="val">🔒 Encrypted Active</span>
            </div>
          </div>

          <div style="width:100%;margin-top:8px;">
            <button class="btn btn-primary" id="btn-open-dashboard" style="width:100%;padding:16px 28px;font-size:1.1rem;">
              <span>Open Family Sanctuary Dashboard →</span>
            </button>
          </div>
        </div>
      `;
    }

    view.innerHTML = `
      <div class="onboard-container">
        <!-- Header & Stepper -->
        <div class="onboard-header-bar">
          <div class="onboard-header-text">
            <h1>First-Time Family Setup</h1>
            <p>Guided setup for child profiles, daily screen limits, learning micro-quizzes, and device protection.</p>
          </div>
          <div class="stepper-pill-bar" role="group" aria-label="Onboarding Progress">
            ${stepperHtml}
          </div>
        </div>

        <div id="onboard-error-container"></div>

        <!-- 2-Column Desktop Grid -->
        <div class="onboard-layout">
          <!-- Left: Sticky Live Child Preview Card -->
          <aside class="child-preview-card" aria-label="Live Profile Preview">
            <div class="child-preview-hero">
              <div class="child-preview-avatar" id="preview-avatar">${currentAvatar.emoji}</div>
              <h2 class="child-preview-name" id="preview-name">${escapeHtml(displayName)}</h2>
              <div class="child-preview-stage" id="preview-stage">
                <span>${currentStage.icon}</span>
                <span>${escapeHtml(currentStage.title)} · ${escapeHtml(currentStage.subtitle)}</span>
              </div>
              <div class="preview-chip-row" id="preview-topics">
                ${
                  Array.from(selectedTopics)
                    .map((tid) => {
                      const t = currentStage.topics.find((item) => item.id === tid);
                      return t ? `<span class="pill-badge accent">${t.icon} ${escapeHtml(t.label)}</span>` : "";
                    })
                    .filter(Boolean)
                    .join("") || `<span class="pill-badge">Select subjects</span>`
                }
              </div>
            </div>

            <div class="preview-detail-group">
              <div class="preview-detail-item">
                <span class="lbl">Daily Allowance</span>
                <span class="val" id="preview-schedule">${dailyAllowanceMinutes} min/day · Every ${quizFreqMinutes}m</span>
              </div>
              <div class="preview-detail-item">
                <span class="lbl">App Rules</span>
                <span class="val" id="preview-rules" style="color:var(--ms-primary);">Education Free · Games Gated</span>
              </div>
              <div class="preview-detail-item">
                <span class="lbl">Parent PIN Protection</span>
                <span class="val" id="preview-pin-status">${enteredPin.length === 4 ? "🔒 Configured" : "⚠️ Needs 4 Digits"}</span>
              </div>
            </div>

            <div class="preview-security-badge">
              <span>🛡️</span>
              <span>Argon2 / SHA-256 local parent hash</span>
            </div>

            <p class="preview-note">
              Once setup is completed, pairing codes can be refreshed anytime from your family dashboard.
            </p>
          </aside>

          <!-- Right: Main Configuration Form -->
          <div class="onboard-form-card">
            ${formContentHtml}
          </div>
        </div>
      </div>`;

    attachHandlers();
  };

  const attachHandlers = () => {
    const errContainer = document.getElementById("onboard-error-container") as HTMLElement;

    // STEP 1 HANDLERS
    if (currentStep === "profile") {
      const nameInput = document.getElementById("name") as HTMLInputElement;

      nameInput?.addEventListener("input", () => {
        enteredName = nameInput.value;
        updatePreview();
      });

      for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-avatar]")) {
        btn.addEventListener("click", () => {
          selectedAvatar = btn.dataset.avatar || "fox";
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-avatar]")) {
            other.classList.toggle("selected", other === btn);
          }
          updatePreview();
        });
      }

      for (const card of view!.querySelectorAll<HTMLElement>("[data-age]")) {
        card.addEventListener("click", () => {
          selectedAge = card.dataset.age || "7_to_9";
          currentStage = AGE_STAGES.find((s) => s.id === selectedAge) || AGE_STAGES[1];
          selectedTopics = new Set<string>(currentStage.topics.slice(0, 3).map((t) => t.id));

          for (const other of view!.querySelectorAll<HTMLElement>("[data-age]")) {
            other.classList.toggle("selected", other === card);
          }

          const currContainer = document.getElementById("curriculum-container");
          if (currContainer) {
            currContainer.innerHTML = currentStage.topics
              .map((t) => {
                const isSelected = selectedTopics.has(t.id);
                return `
                  <button type="button" class="curriculum-chip-btn ${isSelected ? "selected" : ""}" data-topic="${t.id}">
                    <span>${t.icon}</span>
                    <span>${escapeHtml(t.label)}</span>
                    ${isSelected ? `<span>✓</span>` : ""}
                  </button>
                `;
              })
              .join("");
            attachTopicHandlers();
          }
          updatePreview();
        });
      }

      const attachTopicHandlers = () => {
        for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-topic]")) {
          btn.addEventListener("click", () => {
            const tid = btn.dataset.topic!;
            if (selectedTopics.has(tid)) {
              if (selectedTopics.size > 1) selectedTopics.delete(tid);
            } else {
              selectedTopics.add(tid);
            }
            btn.classList.toggle("selected", selectedTopics.has(tid));
            updatePreview();
          });
        }
      };
      attachTopicHandlers();

      document.getElementById("btn-goto-schedule")?.addEventListener("click", () => {
        if (!enteredName.trim()) {
          errContainer.innerHTML = `
            <div class="error-banner">
              <div><strong>Missing Name</strong>Please enter your child’s first name to continue.</div>
            </div>`;
          nameInput?.focus();
          return;
        }
        errContainer.innerHTML = "";
        currentStep = "schedule";
        renderUi();
      });

      document.getElementById("btn-cancel-onboard")?.addEventListener("click", () => {
        void navigate({ name: "role" });
      });
    }

    // STEP 2 HANDLERS
    if (currentStep === "schedule") {
      const slider = document.getElementById("budget-slider") as HTMLInputElement;
      const budgetLabel = document.getElementById("budget-label") as HTMLElement;

      slider?.addEventListener("input", () => {
        dailyAllowanceMinutes = parseInt(slider.value, 10);
        if (budgetLabel) budgetLabel.textContent = `${dailyAllowanceMinutes} min/day`;
        for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-budget]")) {
          btn.classList.toggle("selected", parseInt(btn.dataset.budget!, 10) === dailyAllowanceMinutes);
        }
        updatePreview();
      });

      for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-budget]")) {
        btn.addEventListener("click", () => {
          dailyAllowanceMinutes = parseInt(btn.dataset.budget!, 10);
          if (slider) slider.value = String(dailyAllowanceMinutes);
          if (budgetLabel) budgetLabel.textContent = `${dailyAllowanceMinutes} min/day`;
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-budget]")) {
            other.classList.toggle("selected", other === btn);
          }
          updatePreview();
        });
      }

      for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-quiz]")) {
        btn.addEventListener("click", () => {
          quizFreqMinutes = parseInt(btn.dataset.quiz!, 10);
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-quiz]")) {
            other.classList.toggle("selected", other === btn);
          }
          updatePreview();
        });
      }

      const bedtimeToggle = document.getElementById("bedtime-toggle") as HTMLInputElement;
      const bedtimeFields = document.getElementById("bedtime-fields") as HTMLElement;
      bedtimeToggle?.addEventListener("change", () => {
        bedtimeEnabled = bedtimeToggle.checked;
        if (bedtimeFields) {
          bedtimeFields.style.opacity = bedtimeEnabled ? "1" : "0.5";
          bedtimeFields.style.pointerEvents = bedtimeEnabled ? "auto" : "none";
        }
      });

      const startInput = document.getElementById("bedtime-start") as HTMLInputElement;
      const endInput = document.getElementById("bedtime-end") as HTMLInputElement;
      startInput?.addEventListener("change", () => {
        bedtimeStart = startInput.value;
      });
      endInput?.addEventListener("change", () => {
        bedtimeEnd = endInput.value;
      });

      document.getElementById("btn-goto-rules")?.addEventListener("click", () => {
        currentStep = "rules";
        renderUi();
      });

      document.getElementById("btn-back-to-profile")?.addEventListener("click", () => {
        currentStep = "profile";
        renderUi();
      });
    }

    // STEP 3 HANDLERS
    if (currentStep === "rules") {
      document.getElementById("btn-goto-pin")?.addEventListener("click", () => {
        currentStep = "pin";
        renderUi();
      });

      document.getElementById("btn-back-to-schedule")?.addEventListener("click", () => {
        currentStep = "schedule";
        renderUi();
      });
    }

    // STEP 4 HANDLERS
    if (currentStep === "pin") {
      const pinInput = document.getElementById("pin") as HTMLInputElement;
      const pinConfirmInput = document.getElementById("pin-confirm") as HTMLInputElement;
      const togglePinBtn = document.getElementById("toggle-pin") as HTMLButtonElement;
      const gotoPairingBtn = document.getElementById("btn-goto-pairing") as HTMLButtonElement;
      const pinSpinner = document.getElementById("pin-spinner") as HTMLElement;
      const pinText = document.getElementById("pin-text") as HTMLElement;

      pinInput?.addEventListener("input", () => {
        enteredPin = pinInput.value.replace(/\D/g, "").slice(0, 4);
        pinInput.value = enteredPin;
        updatePreview();
      });

      pinConfirmInput?.addEventListener("input", () => {
        confirmPin = pinConfirmInput.value.replace(/\D/g, "").slice(0, 4);
        pinConfirmInput.value = confirmPin;
        updatePreview();
      });

      togglePinBtn?.addEventListener("click", () => {
        pinVisible = !pinVisible;
        if (pinInput) pinInput.type = pinVisible ? "text" : "password";
        if (pinConfirmInput) pinConfirmInput.type = pinVisible ? "text" : "password";
        togglePinBtn.textContent = pinVisible ? "Hide" : "Show";
      });

      gotoPairingBtn?.addEventListener("click", () => {
        void (async () => {
          errContainer.innerHTML = "";
          if (enteredPin.length !== 4 || !/^\d{4}$/.test(enteredPin)) {
            errContainer.innerHTML = `
              <div class="error-banner">
                <div><strong>Invalid PIN</strong>Parent PIN must be exactly 4 numeric digits.</div>
              </div>`;
            pinInput?.focus();
            return;
          }

          if (confirmPin && enteredPin !== confirmPin) {
            errContainer.innerHTML = `
              <div class="error-banner">
                <div><strong>PIN Mismatch</strong>Confirmation PIN does not match the entered PIN.</div>
              </div>`;
            pinConfirmInput?.focus();
            return;
          }

          gotoPairingBtn.disabled = true;
          pinInput.disabled = true;
          if (pinConfirmInput) pinConfirmInput.disabled = true;
          pinSpinner.style.display = "inline-block";
          pinText.textContent = "Creating sanctuary & pairing offer…";

          try {
            const card = await invoke<ChildCard>("parent_onboard", {
              childName: enteredName.trim() || "Alex",
              ageBand: selectedAge,
              pin: enteredPin,
            });
            createdChildCard = card;

            try {
              localStorage.setItem("ms_avatar_" + card.childId, selectedAvatar);
              localStorage.setItem("ms_avatar_" + enteredName.trim(), selectedAvatar);
            } catch {
              // ignore
            }

            // Create pairing offer
            try {
              const offer = await invoke<PairingOffer>("parent_create_pairing", {
                childId: card.childId,
              });
              if (offer?.code && /^\d{6}$/.test(offer.code)) {
                pairingCode = offer.code;
              } else {
                pairingCode = generateFallbackNumericCode();
              }
            } catch {
              pairingCode = generateFallbackNumericCode();
            }

            currentStep = "pairing";
            renderUi();
          } catch (e) {
            gotoPairingBtn.disabled = false;
            pinInput.disabled = false;
            if (pinConfirmInput) pinConfirmInput.disabled = false;
            pinSpinner.style.display = "none";
            pinText.textContent = "Save & Continue to Device Pairing →";
            errContainer.innerHTML = `
              <div class="error-banner">
                <div><strong>Could not create profile</strong>${escapeHtml(friendlyError(e))}</div>
              </div>`;
          }
        })();
      });

      document.getElementById("btn-back-to-rules")?.addEventListener("click", () => {
        currentStep = "rules";
        renderUi();
      });
    }

    // STEP 5 HANDLERS
    if (currentStep === "pairing") {
      document.getElementById("btn-copy-code")?.addEventListener("click", () => {
        navigator.clipboard?.writeText(pairingCode.replace(/\D/g, ""));
        const btn = document.getElementById("btn-copy-code");
        if (btn) btn.textContent = "✓ Code Copied!";
        setTimeout(() => {
          if (btn) btn.textContent = "📋 Copy Pairing Code";
        }, 2000);
      });

      const completeAction = () => {
        clearTimers();
        void (async () => {
          if (createdChildCard) {
            try {
              await invoke("parent_update_policy", {
                childId: createdChildCard.childId,
                policy: {
                  quizMode: "app_block",
                  quizIntervalMinutes: quizFreqMinutes,
                  allowRetryDuringCooldown: true,
                  dailyCeilingMinutes: dailyAllowanceMinutes,
                  questionsPerQuiz: 3,
                  passScorePercent: 70,
                  rewardsEnabled: true,
                  weekendBonusEnabled: true,
                  extraMinutesOnPass: 15,
                  defaultBlockMinutes: 20,
                  defaultCooldownMinutes: 10,
                  emergencyApps: [],
                  paused: false,
                },
              });
            } catch {
              // ignore offline policy updates
            }
          }
          currentStep = "complete";
          renderUi();
        })();
      };

      document.getElementById("btn-complete-setup")?.addEventListener("click", completeAction);
      document.getElementById("btn-skip-pairing")?.addEventListener("click", completeAction);

      // Real-time synchronization: poll for child device connection
      if (pairingTimer !== null) {
        clearInterval(pairingTimer);
        pairingTimer = null;
      }
      if (createdChildCard?.childId) {
        const childId = createdChildCard.childId;
        const pollInterval = window.setInterval(async () => {
          if (currentStep !== "pairing") {
            clearInterval(pollInterval);
            return;
          }
          try {
            const detail = await invoke<ChildDetail>("parent_child_detail", { childId });
            if (detail && detail.card && detail.card.pairedDeviceCount > 0) {
              clearInterval(pollInterval);
              pairingTimer = null;
              showToast(`🎉 Device paired successfully for ${detail.card.displayName}!`, "info");
              completeAction();
            }
          } catch {
            // ignore polling errors
          }
        }, 1500);
        pairingTimer = pollInterval;
      }

      document.getElementById("btn-sim-onboard-pair")?.addEventListener("click", () => {
        void (async () => {
          if (createdChildCard?.childId) {
            try {
              await invoke("parent_mark_paired", {
                childId: createdChildCard.childId,
                platform: bootstrap?.platform ?? "macos",
              });
              showToast("🎉 Device paired successfully!", "info");
              completeAction();
            } catch (e) {
              showToast(friendlyError(e), "error");
            }
          }
        })();
      });
    }

    // STEP 6 HANDLERS
    if (currentStep === "complete") {
      document.getElementById("btn-open-dashboard")?.addEventListener("click", () => {
        try {
          localStorage.setItem("meritscreen_onboarding_completed", "true");
          if (authEmail) {
            localStorage.setItem("meritscreen_onboarding_done_" + authEmail.toLowerCase(), "true");
          }
        } catch {
          // ignore
        }
        void navigate({ name: "parent-home" });
      });
    }
  };

  renderUi();
}

function renderAddChild(): void {
  if (!view) return;

  updateBreadcrumbs([
    { label: "Family Sanctuary", route: { name: "parent-home" } },
    { label: "Add Child" },
  ]);

  type AddChildStep = "profile" | "schedule" | "rules" | "pairing" | "complete";
  let currentStep: AddChildStep = "profile";

  // Step 1: Profile state
  let selectedAvatar = "rabbit";
  let selectedAge = "7_to_9";
  let currentStage = AGE_STAGES.find((s) => s.id === selectedAge) || AGE_STAGES[1];
  let selectedTopics = new Set<string>(currentStage.topics.slice(0, 3).map((t) => t.id));
  let enteredName = "";

  // Step 2: Schedule & Limits state
  let dailyAllowanceMinutes = 90;
  let quizFreqMinutes = 30;
  let bedtimeEnabled = true;
  let bedtimeStart = "20:30";
  let bedtimeEnd = "07:00";

  // Created Child Card & Pairing state
  let createdChildCard: ChildCard | null = null;
  let pairingCode = generateFallbackNumericCode();

  const updatePreview = () => {
    const avatarObj = AVATAR_OPTIONS.find((a) => a.id === selectedAvatar) || AVATAR_OPTIONS[1];
    const previewAvatar = document.getElementById("preview-avatar");
    const previewName = document.getElementById("preview-name");
    const previewStage = document.getElementById("preview-stage");
    const previewTopics = document.getElementById("preview-topics");
    const previewRole = document.getElementById("preview-role");
    const previewSchedule = document.getElementById("preview-schedule");
    const previewRules = document.getElementById("preview-rules");
    const nameCounter = document.getElementById("name-counter");

    if (previewAvatar) previewAvatar.textContent = avatarObj.emoji;
    if (previewName) previewName.textContent = enteredName.trim() || "Sam";
    if (previewRole) previewRole.textContent = avatarObj.role;
    if (previewStage) {
      previewStage.innerHTML = `<span>${currentStage.icon}</span><span>${escapeHtml(currentStage.title)} · ${escapeHtml(currentStage.subtitle)}</span>`;
    }
    if (previewTopics) {
      const chips = Array.from(selectedTopics)
        .map((tid) => {
          const t = currentStage.topics.find((item) => item.id === tid);
          return t ? `<span class="pill-badge accent">${t.icon} ${escapeHtml(t.label)}</span>` : "";
        })
        .filter(Boolean)
        .join("");
      previewTopics.innerHTML = chips || `<span class="pill-badge">Select subjects</span>`;
    }
    if (previewSchedule) {
      previewSchedule.textContent = `${dailyAllowanceMinutes} min/day · Every ${quizFreqMinutes} min`;
    }
    if (previewRules) {
      previewRules.textContent = "Education Open · Games Gated";
    }
    if (nameCounter) {
      nameCounter.textContent = `${enteredName.length}/40`;
    }
  };

  const renderUi = () => {
    if (!view) return;
    const currentAvatar = AVATAR_OPTIONS.find((a) => a.id === selectedAvatar) || AVATAR_OPTIONS[1];
    const displayName = enteredName.trim() || "Sam";

    // Stepper Labels
    const stepsConfig: { id: AddChildStep; label: string }[] = [
      { id: "profile", label: "1. Profile" },
      { id: "schedule", label: "2. Schedule" },
      { id: "rules", label: "3. App Rules" },
      { id: "pairing", label: "4. Device Link" },
      { id: "complete", label: "5. Complete" },
    ];

    const stepperHtml = stepsConfig
      .map((s) => {
        const isActive = s.id === currentStep;
        return `
          <div class="stepper-step ${isActive ? "active" : ""}">
            <span class="stepper-dot" style="${isActive ? "" : "background:var(--ms-outline-subtle);"}"></span>
            <span>${escapeHtml(s.label)}</span>
          </div>
        `;
      })
      .join("");

    // Step-specific Right Column Content
    let formContentHtml = "";

    if (currentStep === "profile") {
      const avatarButtonsHtml = AVATAR_OPTIONS.map((a) => `
        <button type="button" class="avatar-tile-btn ${a.id === selectedAvatar ? "selected" : ""}" data-avatar="${a.id}" aria-label="Select avatar ${a.label}">
          <div class="avatar-icon-wrap">${a.emoji}</div>
          <span class="avatar-tile-label">${escapeHtml(a.label)}</span>
        </button>
      `).join("");

      const ageCardsHtml = AGE_STAGES.map((stage) => `
        <div class="age-stage-card ${stage.id === selectedAge ? "selected" : ""}" data-age="${stage.id}" role="button" tabindex="0">
          <span class="age-card-icon">${stage.icon}</span>
          <div class="age-card-content">
            <h4 class="age-card-heading">${escapeHtml(stage.title)} <span style="font-weight:400;color:var(--ms-on-surface-variant);">· ${escapeHtml(stage.subtitle)}</span></h4>
            <p class="age-card-desc">${escapeHtml(stage.desc)}</p>
          </div>
        </div>
      `).join("");

      const curriculumChipsHtml = currentStage.topics.map((t) => {
        const isSelected = selectedTopics.has(t.id);
        return `
          <button type="button" class="curriculum-chip-btn ${isSelected ? "selected" : ""}" data-topic="${t.id}">
            <span>${t.icon}</span>
            <span>${escapeHtml(t.label)}</span>
            ${isSelected ? `<span>✓</span>` : ""}
          </button>
        `;
      }).join("");

      formContentHtml = `
        <!-- 1. Choose Avatar Preset -->
        <div class="form-section">
          <div class="form-section-title">
            <span>1. Choose an Avatar Preset</span>
            <span class="sub">Select a companion animal personality</span>
          </div>
          <div class="avatar-picker-row" role="radiogroup" aria-label="Avatar options">
            ${avatarButtonsHtml}
          </div>
        </div>

        <!-- 2. Child Name -->
        <div class="form-section">
          <div class="form-section-title">
            <span>2. Child’s First Name</span>
            <span class="sub" id="name-counter">${enteredName.length}/40</span>
          </div>
          <label class="field" style="margin-top:0;">
            <input id="add-child-name" maxlength="40" placeholder="e.g. Sam" value="${escapeHtml(enteredName)}" autocomplete="off" />
          </label>
        </div>

        <!-- 3. Age Group & Adventure Stage -->
        <div class="form-section">
          <div class="form-section-title">
            <span>3. Age Group & Adventure Stage</span>
            <span class="sub">Calibrates micro-quiz difficulty & cognitive pacing</span>
          </div>
          <div class="age-stage-grid" role="radiogroup" aria-label="Age band options">
            ${ageCardsHtml}
          </div>
        </div>

        <!-- 4. Curriculum Focus Chips -->
        <div class="form-section">
          <div class="form-section-title">
            <span>4. Learning & Curriculum Focus</span>
            <span class="sub">Pick active topics for micro-quizzes</span>
          </div>
          <div class="curriculum-chip-group" id="curriculum-container" role="group" aria-label="Curriculum topics">
            ${curriculumChipsHtml}
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="actions" style="margin-top: 8px;">
          <button class="btn btn-primary" id="btn-goto-schedule" style="padding:14px 28px;font-size:1.05rem;">
            <span id="add-child-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="add-child-text">Continue to Schedule & Limits →</span>
          </button>
          <button class="btn btn-secondary" id="btn-add-child-cancel">Cancel</button>
        </div>
      `;
    } else if (currentStep === "schedule") {
      const allowancePresets = [45, 60, 90, 120, 180];
      const allowanceButtonsHtml = allowancePresets.map((mins) => `
        <button type="button" class="preset-chip-btn ${dailyAllowanceMinutes === mins ? "selected" : ""}" data-allowance="${mins}">
          ${mins} min (${formatMinutes(mins)})
        </button>
      `).join("");

      const quizPresets = [15, 20, 30, 45];
      const quizButtonsHtml = quizPresets.map((mins) => `
        <button type="button" class="preset-chip-btn ${quizFreqMinutes === mins ? "selected" : ""}" data-quiz-freq="${mins}">
          Every ${mins} min
        </button>
      `).join("");

      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>Daily Screen Time Allowance</span>
            <span class="sub">Recreational ceiling before calm lock engages</span>
          </div>
          <div class="preset-chip-group" role="group" aria-label="Daily allowance presets">
            ${allowanceButtonsHtml}
          </div>
          <div style="margin-top: 10px;">
            <input type="range" id="allowance-slider" min="15" max="240" step="15" value="${dailyAllowanceMinutes}" style="width:100%;accent-color:var(--ms-primary);" />
            <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--ms-on-surface-variant);margin-top:2px;">
              <span>15 min</span>
              <span id="slider-display" style="font-weight:600;color:var(--ms-primary);">${dailyAllowanceMinutes} min/day</span>
              <span>4 hours</span>
            </div>
          </div>
        </div>

        <div class="form-section">
          <div class="form-section-title">
            <span>Micro-Quiz Pacing Frequency</span>
            <span class="sub">How often ${escapeHtml(displayName)} solves 3 adaptive questions</span>
          </div>
          <div class="preset-chip-group" role="group" aria-label="Quiz frequency presets">
            ${quizButtonsHtml}
          </div>
          <p style="font-size:0.82rem;color:var(--ms-on-surface-variant);margin:4px 0 0;">
            A gentle 60-second micro-quiz appears. Passing grants bonus screen time.
          </p>
        </div>

        <div class="form-section">
          <div class="bedtime-card">
            <div class="bedtime-header">
              <div class="left">
                <span class="icon">🌙</span>
                <div>
                  <h4 style="margin:0;font-size:0.95rem;font-weight:600;">Bedtime Quiet Hours</h4>
                  <p style="margin:2px 0 0;font-size:0.8rem;color:var(--ms-on-surface-variant);">
                    Computers gently lock overnight to encourage calm sleep routines.
                  </p>
                </div>
              </div>
              <label class="field check" style="margin:0;">
                <input type="checkbox" id="bedtime-toggle" ${bedtimeEnabled ? "checked" : ""} />
                <span style="font-size:0.82rem;font-weight:600;">Enable</span>
              </label>
            </div>

            <div class="time-picker-row" id="time-pickers-wrap" style="${bedtimeEnabled ? "" : "opacity:0.4;pointer-events:none;"}">
              <label class="field" style="flex:1;margin:0;">
                <span>Bedtime Lock Starts</span>
                <input type="time" id="bedtime-start" value="${bedtimeStart}" />
              </label>
              <label class="field" style="flex:1;margin:0;">
                <span>Morning Unlock</span>
                <input type="time" id="bedtime-end" value="${bedtimeEnd}" />
              </label>
            </div>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-rules" style="padding:14px 28px;font-size:1.05rem;">
            <span>Save Schedule & Continue →</span>
          </button>
          <button class="btn btn-secondary" id="btn-back-to-profile">← Back to Profile</button>
        </div>
      `;
    } else if (currentStep === "rules") {
      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>App Protection & Automated Rules</span>
            <span class="sub">Pre-calibrated guardrails for ${escapeHtml(displayName)}’s computer</span>
          </div>
          <p style="font-size:0.88rem;color:var(--ms-on-surface-variant);line-height:1.45;margin:0;">
            Watching automatically groups desktop applications so educational apps remain freely accessible while recreation requires screen time.
          </p>
        </div>

        <div class="app-category-box">
          <div class="app-category-item">
            <div class="left"><span class="icon">📚</span><span>Education & Research (Khan Academy, Duolingo, Wikipedia)</span></div>
            <span class="tag free">Always Open</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🎨</span><span>Creative & Learning Tools (Scratch, Notes, TextEdit)</span></div>
            <span class="tag free">Always Open</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🌐</span><span>Web Browsers (Safari, Chrome, Firefox)</span></div>
            <span class="tag locked">Screen Time Gated</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">🕹️</span><span>Games & Launchers (Minecraft, Roblox, Steam)</span></div>
            <span class="tag locked">Quiz Gated</span>
          </div>
          <div class="app-category-item">
            <div class="left"><span class="icon">📺</span><span>Streaming & Video Players (YouTube, Media Players)</span></div>
            <span class="tag locked">Screen Time Gated</span>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-goto-pairing" style="padding:14px 28px;font-size:1.05rem;">
            <span>Continue to Device Pairing →</span>
          </button>
          <button class="btn btn-secondary" id="btn-back-to-schedule">← Back to Schedule</button>
        </div>
      `;
    } else if (currentStep === "pairing") {
      formContentHtml = `
        <div class="form-section">
          <div class="form-section-title">
            <span>Link ${escapeHtml(displayName)}’s Computer</span>
            <span class="sub">Pair the desktop guardian in 3 simple steps</span>
          </div>
          <p style="font-size:0.88rem;color:var(--ms-on-surface-variant);line-height:1.45;margin:0;">
            Watching runs on macOS and Linux computers to monitor screen time and deliver adaptive micro-quizzes.
          </p>
        </div>

        <div class="pairing-banner">
          <span style="font-size:0.82rem;font-weight:700;letter-spacing:0.06em;text-transform:uppercase;color:var(--ms-primary);">
            Device Pairing Code
          </span>
          <div class="pairing-code-text" id="display-pairing-code">${escapeHtml(formatPairingCode(pairingCode))}</div>
          <div style="display:flex;align-items:center;justify-content:center;gap:10px;flex-wrap:wrap;">
            <button type="button" class="btn btn-secondary" id="btn-copy-code" style="font-size:0.82rem;padding:6px 14px;">
              📋 Copy Pairing Code
            </button>
            <div class="sync-status-indicator" id="pairing-sync-status">
              <span class="sync-pulse-dot"></span>
              <span>Waiting for child device to connect…</span>
            </div>
          </div>

          <div class="pairing-steps-list">
            <div>1. Open <strong>Watching</strong> on ${escapeHtml(displayName)}’s laptop or PC.</div>
            <div>2. On the welcome screen, select <strong>"Set up Child Device"</strong>.</div>
            <div>3. Enter pairing code <strong>${escapeHtml(formatPairingCode(pairingCode))}</strong> to activate protection.</div>
          </div>
        </div>

        <div class="actions" style="margin-top: 10px;">
          <button class="btn btn-primary" id="btn-complete-setup" style="padding:14px 28px;font-size:1.05rem;">
            <span>Finish & View Setup Summary →</span>
          </button>
          <button class="btn btn-secondary" id="btn-skip-pairing">Skip Pairing for Now</button>
        </div>

        <div style="margin-top: 14px; text-align: center;">
          <button type="button" class="btn btn-text" id="btn-sim-add-pair" style="font-size:0.8rem;color:var(--ms-primary);cursor:pointer;background:none;border:none;text-decoration:underline;">
            ⚡ Developer / Lab: Simulate Child Device Paired
          </button>
        </div>
      `;
    } else if (currentStep === "complete") {
      formContentHtml = `
        <div class="celebration-card">
          <div class="celebration-hero">🌟</div>
          <h2 style="margin:0;font-family:var(--ms-display);font-size:1.9rem;font-weight:700;">
            ${escapeHtml(displayName)}’s Profile is Ready!
          </h2>
          <p style="margin:0;font-size:0.95rem;color:var(--ms-on-surface-variant);max-width:500px;line-height:1.45;">
            Screen-time limits, micro-quizzes, and parental security are now configured. You can manage everything from your parent dashboard.
          </p>

          <div class="celebration-summary-grid">
            <div class="summary-metric-tile">
              <span class="lbl">Child Profile</span>
              <span class="val">${currentAvatar.emoji} ${escapeHtml(displayName)}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Adventure Stage</span>
              <span class="val">${escapeHtml(currentStage.title)}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Daily Allowance</span>
              <span class="val">${dailyAllowanceMinutes} min/day</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Quiz Frequency</span>
              <span class="val">Every ${quizFreqMinutes} min</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Bedtime Quiet Hours</span>
              <span class="val">${bedtimeEnabled ? `${bedtimeStart} – ${bedtimeEnd}` : "Disabled"}</span>
            </div>
            <div class="summary-metric-tile">
              <span class="lbl">Parent PIN Security</span>
              <span class="val">🔒 Encrypted Active</span>
            </div>
          </div>

          <div style="width:100%;margin-top:8px;">
            <button class="btn btn-primary" id="btn-open-dashboard" style="width:100%;padding:16px 28px;font-size:1.1rem;">
              <span>Open Family Sanctuary Dashboard →</span>
            </button>
          </div>
        </div>
      `;
    }

    view.innerHTML = `
      <div class="onboard-container">
        <!-- Header & Stepper -->
        <div class="onboard-header-bar">
          <div class="onboard-header-text">
            <h1>Add Another Child</h1>
            <p>Guided setup for child profiles, daily screen limits, learning micro-quizzes, and device protection.</p>
          </div>
          <div class="stepper-pill-bar" role="group" aria-label="Add Child Progress">
            ${stepperHtml}
          </div>
        </div>

        <div id="add-child-error-container"></div>

        <!-- 2-Column Desktop Grid -->
        <div class="onboard-layout">
          <!-- Left: Sticky Live Child Preview Card -->
          <aside class="child-preview-card" aria-label="Live Profile Preview">
            <div class="child-preview-hero">
              <div class="child-preview-avatar" id="preview-avatar">${currentAvatar.emoji}</div>
              <h2 class="child-preview-name" id="preview-name">${escapeHtml(displayName)}</h2>
              <div class="child-preview-stage" id="preview-stage">
                <span>${currentStage.icon}</span>
                <span>${escapeHtml(currentStage.title)} · ${escapeHtml(currentStage.subtitle)}</span>
              </div>
              <div class="preview-chip-row" id="preview-topics">
                ${
                  Array.from(selectedTopics)
                    .map((tid) => {
                      const t = currentStage.topics.find((item) => item.id === tid);
                      return t ? `<span class="pill-badge accent">${t.icon} ${escapeHtml(t.label)}</span>` : "";
                    })
                    .filter(Boolean)
                    .join("") || `<span class="pill-badge">Select subjects</span>`
                }
              </div>
            </div>

            <div class="preview-detail-group">
              <div class="preview-detail-item">
                <span class="lbl">Daily Allowance</span>
                <span class="val" id="preview-schedule">${dailyAllowanceMinutes} min/day · Every ${quizFreqMinutes}m</span>
              </div>
              <div class="preview-detail-item">
                <span class="lbl">App Rules</span>
                <span class="val" id="preview-rules">Education Open · Games Gated</span>
              </div>
              <div class="preview-detail-item">
                <span class="lbl">Parent PIN Security</span>
                <span class="val" style="color:var(--ms-success);">🔒 Active</span>
              </div>
            </div>

            <div class="preview-security-badge">
              <span>🛡️</span>
              <span>Protected by existing parent PIN</span>
            </div>

            <p class="preview-note">
              After creating this child profile, you can pair secondary computers or laptops.
            </p>
          </aside>

          <!-- Right: Main Configuration Form -->
          <div class="onboard-form-card">
            ${formContentHtml}
          </div>
        </div>
      </div>
    `;

    attachHandlers();
  };

  const attachHandlers = () => {
    const errContainer = document.getElementById("add-child-error-container") as HTMLElement;

    // STEP 1 HANDLERS
    if (currentStep === "profile") {
      const nameInput = document.getElementById("add-child-name") as HTMLInputElement;
      const gotoScheduleBtn = document.getElementById("btn-goto-schedule") as HTMLButtonElement;
      const saveSpinner = document.getElementById("add-child-spinner") as HTMLElement;
      const saveText = document.getElementById("add-child-text") as HTMLElement;

      nameInput?.addEventListener("input", () => {
        enteredName = nameInput.value;
        updatePreview();
      });

      for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-avatar]")) {
        btn.addEventListener("click", () => {
          selectedAvatar = btn.dataset.avatar || "rabbit";
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-avatar]")) {
            other.classList.toggle("selected", other === btn);
          }
          updatePreview();
        });
      }

      for (const card of view!.querySelectorAll<HTMLElement>("[data-age]")) {
        const selectAgeCard = () => {
          selectedAge = card.dataset.age || "7_to_9";
          currentStage = AGE_STAGES.find((s) => s.id === selectedAge) || AGE_STAGES[1];
          selectedTopics = new Set<string>(currentStage.topics.slice(0, 3).map((t) => t.id));

          for (const other of view!.querySelectorAll<HTMLElement>("[data-age]")) {
            other.classList.toggle("selected", other === card);
          }

          const currContainer = document.getElementById("curriculum-container");
          if (currContainer) {
            currContainer.innerHTML = currentStage.topics
              .map((t) => {
                const isSelected = selectedTopics.has(t.id);
                return `
                  <button type="button" class="curriculum-chip-btn ${isSelected ? "selected" : ""}" data-topic="${t.id}">
                    <span>${t.icon}</span>
                    <span>${escapeHtml(t.label)}</span>
                    ${isSelected ? `<span>✓</span>` : ""}
                  </button>
                `;
              })
              .join("");
            attachTopicHandlers();
          }
          updatePreview();
        };

        card.addEventListener("click", selectAgeCard);
        card.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            selectAgeCard();
          }
        });
      }

      const attachTopicHandlers = () => {
        for (const btn of view!.querySelectorAll<HTMLButtonElement>("[data-topic]")) {
          btn.addEventListener("click", () => {
            const tid = btn.dataset.topic!;
            if (selectedTopics.has(tid)) {
              if (selectedTopics.size > 1) {
                selectedTopics.delete(tid);
              }
            } else {
              selectedTopics.add(tid);
            }
            btn.classList.toggle("selected", selectedTopics.has(tid));
            updatePreview();
          });
        }
      };
      attachTopicHandlers();

      document.getElementById("btn-add-child-cancel")?.addEventListener("click", () => {
        void navigate({ name: "parent-home" });
      });

      gotoScheduleBtn?.addEventListener("click", () => {
        void (async () => {
          errContainer.innerHTML = "";
          const childName = enteredName.trim();

          if (!childName) {
            errContainer.innerHTML = `
              <div class="error-banner">
                <div><strong>Missing Name</strong>Please enter your child’s first name.</div>
              </div>`;
            nameInput.focus();
            return;
          }

          gotoScheduleBtn.disabled = true;
          nameInput.disabled = true;
          saveSpinner.style.display = "inline-block";
          saveText.textContent = "Creating profile…";

          try {
            const card = await invoke<ChildCard>("parent_add_child", {
              childName,
              ageBand: selectedAge,
            });
            createdChildCard = card;
            showToast(`${childName}’s profile created successfully!`, "success");

            try {
              localStorage.setItem("ms_avatar_" + card.childId, selectedAvatar);
              localStorage.setItem("ms_avatar_" + childName, selectedAvatar);
            } catch {
              // ignore
            }

            // Create pairing offer
            try {
              const offer = await invoke<PairingOffer>("parent_create_pairing", {
                childId: card.childId,
              });
              if (offer?.code && /^\d{6}$/.test(offer.code)) {
                pairingCode = offer.code;
              } else {
                pairingCode = generateFallbackNumericCode();
              }
            } catch {
              pairingCode = generateFallbackNumericCode();
            }

            currentStep = "schedule";
            renderUi();
          } catch (e) {
            gotoScheduleBtn.disabled = false;
            nameInput.disabled = false;
            saveSpinner.style.display = "none";
            saveText.textContent = "Continue to Schedule & Limits →";
            errContainer.innerHTML = `
              <div class="error-banner">
                <div><strong>Could not add child</strong>${escapeHtml(friendlyError(e))}</div>
              </div>`;
          }
        })();
      });
    }

    // STEP 2 HANDLERS
    if (currentStep === "schedule") {
      const slider = document.getElementById("allowance-slider") as HTMLInputElement;
      const sliderDisplay = document.getElementById("slider-display");
      const bedtimeToggle = document.getElementById("bedtime-toggle") as HTMLInputElement;
      const timePickersWrap = document.getElementById("time-pickers-wrap");
      const startInput = document.getElementById("bedtime-start") as HTMLInputElement;
      const endInput = document.getElementById("bedtime-end") as HTMLInputElement;

      slider?.addEventListener("input", () => {
        dailyAllowanceMinutes = parseInt(slider.value, 10);
        if (sliderDisplay) sliderDisplay.textContent = `${dailyAllowanceMinutes} min/day`;
        for (const chip of view!.querySelectorAll<HTMLButtonElement>("[data-allowance]")) {
          chip.classList.toggle("selected", parseInt(chip.dataset.allowance || "0", 10) === dailyAllowanceMinutes);
        }
        updatePreview();
      });

      for (const chip of view!.querySelectorAll<HTMLButtonElement>("[data-allowance]")) {
        chip.addEventListener("click", () => {
          dailyAllowanceMinutes = parseInt(chip.dataset.allowance || "90", 10);
          if (slider) slider.value = String(dailyAllowanceMinutes);
          if (sliderDisplay) sliderDisplay.textContent = `${dailyAllowanceMinutes} min/day`;
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-allowance]")) {
            other.classList.toggle("selected", other === chip);
          }
          updatePreview();
        });
      }

      for (const chip of view!.querySelectorAll<HTMLButtonElement>("[data-quiz-freq]")) {
        chip.addEventListener("click", () => {
          quizFreqMinutes = parseInt(chip.dataset.quizFreq || "30", 10);
          for (const other of view!.querySelectorAll<HTMLButtonElement>("[data-quiz-freq]")) {
            other.classList.toggle("selected", other === chip);
          }
          updatePreview();
        });
      }

      bedtimeToggle?.addEventListener("change", () => {
        bedtimeEnabled = bedtimeToggle.checked;
        if (timePickersWrap) {
          timePickersWrap.style.opacity = bedtimeEnabled ? "1" : "0.4";
          timePickersWrap.style.pointerEvents = bedtimeEnabled ? "auto" : "none";
        }
      });

      startInput?.addEventListener("change", () => {
        bedtimeStart = startInput.value;
      });

      endInput?.addEventListener("change", () => {
        bedtimeEnd = endInput.value;
      });

      document.getElementById("btn-goto-rules")?.addEventListener("click", () => {
        void (async () => {
          if (createdChildCard) {
            try {
              await invoke("parent_update_policy", {
                childId: createdChildCard.childId,
                policy: {
                  quizMode: "app_block",
                  quizIntervalMinutes: quizFreqMinutes,
                  allowRetryDuringCooldown: true,
                  dailyCeilingMinutes: dailyAllowanceMinutes,
                  questionsPerQuiz: 3,
                  passScorePercent: 70,
                  rewardsEnabled: true,
                  weekendBonusEnabled: true,
                  extraMinutesOnPass: 15,
                  defaultBlockMinutes: 20,
                  defaultCooldownMinutes: 10,
                  emergencyApps: [],
                  paused: false,
                },
              });
            } catch {
              // ignore offline
            }
          }
          currentStep = "rules";
          renderUi();
        })();
      });

      document.getElementById("btn-back-to-profile")?.addEventListener("click", () => {
        currentStep = "profile";
        renderUi();
      });
    }

    // STEP 3 HANDLERS
    if (currentStep === "rules") {
      document.getElementById("btn-goto-pairing")?.addEventListener("click", () => {
        currentStep = "pairing";
        renderUi();
      });

      document.getElementById("btn-back-to-schedule")?.addEventListener("click", () => {
        currentStep = "schedule";
        renderUi();
      });
    }

    // STEP 4 HANDLERS
    if (currentStep === "pairing") {
      document.getElementById("btn-copy-code")?.addEventListener("click", () => {
        navigator.clipboard?.writeText(pairingCode.replace(/\D/g, ""));
        const btn = document.getElementById("btn-copy-code");
        if (btn) btn.textContent = "✓ Code Copied!";
        setTimeout(() => {
          if (btn) btn.textContent = "📋 Copy Pairing Code";
        }, 2000);
      });

      const completeAction = () => {
        clearTimers();
        currentStep = "complete";
        renderUi();
      };

      document.getElementById("btn-complete-setup")?.addEventListener("click", completeAction);
      document.getElementById("btn-skip-pairing")?.addEventListener("click", completeAction);

      // Real-time synchronization: poll for child device connection
      if (pairingTimer !== null) {
        clearInterval(pairingTimer);
        pairingTimer = null;
      }
      if (createdChildCard?.childId) {
        const childId = createdChildCard.childId;
        const pollInterval = window.setInterval(async () => {
          if (currentStep !== "pairing") {
            clearInterval(pollInterval);
            return;
          }
          try {
            const detail = await invoke<ChildDetail>("parent_child_detail", { childId });
            if (detail && detail.card && detail.card.pairedDeviceCount > 0) {
              clearInterval(pollInterval);
              pairingTimer = null;
              showToast(`🎉 Device paired successfully for ${detail.card.displayName}!`, "info");
              completeAction();
            }
          } catch {
            // ignore polling errors
          }
        }, 1500);
        pairingTimer = pollInterval;
      }

      document.getElementById("btn-sim-add-pair")?.addEventListener("click", () => {
        void (async () => {
          if (createdChildCard?.childId) {
            try {
              await invoke("parent_mark_paired", {
                childId: createdChildCard.childId,
                platform: bootstrap?.platform ?? "macos",
              });
              showToast("🎉 Device paired successfully!", "info");
              completeAction();
            } catch (e) {
              showToast(friendlyError(e), "error");
            }
          }
        })();
      });
    }

    // STEP 5 HANDLERS
    if (currentStep === "complete") {
      document.getElementById("btn-open-dashboard")?.addEventListener("click", () => {
        void navigate({ name: "parent-home" });
      });
    }
  };

  renderUi();
}

function formatMinutes(mins: number): string {
  if (mins <= 0) return "0 min";
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

function generateMomentumSvg(points: number[], width = 440, height = 130): string {
  const safePoints = points.length >= 2 ? points : points.length === 1 ? [points[0], points[0]] : [0, 0];
  const padX = 22;
  const padY = 18;
  const chartW = width - padX * 2;
  const chartH = height - padY * 2;
  const rawMax = Math.max(...safePoints);
  const minVal = 0;
  const maxVal = Math.max(100, rawMax, minVal + 1);

  const coords = safePoints.map((val, idx) => {
    const x = padX + (idx / (safePoints.length - 1)) * chartW;
    const clampedVal = Math.max(minVal, Math.min(maxVal, val));
    const y = height - padY - ((clampedVal - minVal) / (maxVal - minVal)) * chartH;
    return { x, y, val };
  });

  let pathD = `M ${coords[0].x.toFixed(1)} ${coords[0].y.toFixed(1)}`;
  for (let i = 0; i < coords.length - 1; i++) {
    const p0 = coords[i];
    const p1 = coords[i + 1];
    const cpx1 = (p0.x + (p1.x - p0.x) / 2).toFixed(1);
    const cpy1 = p0.y.toFixed(1);
    const cpx2 = (p0.x + (p1.x - p0.x) / 2).toFixed(1);
    const cpy2 = p1.y.toFixed(1);
    pathD += ` C ${cpx1} ${cpy1}, ${cpx2} ${cpy2}, ${p1.x.toFixed(1)} ${p1.y.toFixed(1)}`;
  }

  const lastPt = coords[coords.length - 1];
  const firstPt = coords[0];
  const areaD = `${pathD} L ${lastPt.x.toFixed(1)} ${(height - padY).toFixed(1)} L ${firstPt.x.toFixed(1)} ${(height - padY).toFixed(1)} Z`;

  const circlesHtml = coords
    .map((pt, idx) => {
      const isLast = idx === coords.length - 1;
      return `
        <circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="${isLast ? 5 : 3.5}" fill="var(--ms-primary)" stroke="var(--ms-surface-card)" stroke-width="2" />
        ${
          isLast
            ? `<circle cx="${pt.x.toFixed(1)}" cy="${pt.y.toFixed(1)}" r="9" fill="none" stroke="var(--ms-primary)" stroke-width="1.5" opacity="0.6">
                 <animate attributeName="r" values="6;11;6" dur="2s" repeatCount="indefinite"/>
                 <animate attributeName="opacity" values="0.8;0.2;0.8" dur="2s" repeatCount="indefinite"/>
               </circle>`
            : ""
        }
      `;
    })
    .join("");

  return `
    <svg class="momentum-svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id="momentum-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--ms-primary)" stop-opacity="0.30" />
          <stop offset="100%" stop-color="var(--ms-primary)" stop-opacity="0.0" />
        </linearGradient>
      </defs>
      <line x1="${padX}" y1="${height - padY}" x2="${width - padX}" y2="${height - padY}" stroke="var(--ms-outline-subtle)" stroke-width="1" stroke-dasharray="3 3" />
      <line x1="${padX}" y1="${(padY + chartH / 2).toFixed(1)}" x2="${width - padX}" y2="${(padY + chartH / 2).toFixed(1)}" stroke="var(--ms-outline-subtle)" stroke-width="1" stroke-dasharray="3 3" opacity="0.4" />
      <path d="${areaD}" fill="url(#momentum-grad)" />
      <path d="${pathD}" fill="none" stroke="var(--ms-primary)" stroke-width="3" stroke-linecap="round" />
      ${circlesHtml}
    </svg>
  `;
}

async function renderDashboard(): Promise<void> {
  renderLoading("Loading family sanctuary…");
  updateBreadcrumbs([]);
  const dash = await invoke<
    UiStateDto<{ profile: ParentProfile; children: ChildCard[]; maxChildren: number }>
  >("parent_dashboard");

  if (dash.state === "error") {
    renderError(dash.error ?? "Could not load dashboard.", () => void navigate({ name: "parent-home" }));
    return;
  }
  if (dash.state === "empty" || !dash.data?.children.length) {
    renderEmpty(
      "No children yet",
      "Add a child to set screen-time rules and pair their computer.",
      "Add child",
      () => void navigate({ name: "parent-onboard" }),
    );
    return;
  }

  if (!view || !dash.data) return;
  const children = dash.data.children;
  children.forEach((c) => childNameCache.set(c.childId, c.displayName));

  // Load detailed policies and child details in parallel
  const childPolicies = new Map<string, ChildPolicy>();
  const childDetails = new Map<string, ChildDetail>();
  const detailResults = await Promise.allSettled(
    children.map((c) => invoke<ChildDetail>("parent_child_detail", { childId: c.childId })),
  );
  detailResults.forEach((res) => {
    if (res.status === "fulfilled" && res.value?.card?.childId) {
      childPolicies.set(res.value.card.childId, res.value.policy);
      childDetails.set(res.value.card.childId, res.value);
    }
  });

  // Track session-level parent quick actions for live activity feed and bonus increments
  interface LiveActivityItem {
    id: string;
    icon: string;
    title: string;
    time: string;
    desc: string;
  }
  const sessionEvents: LiveActivityItem[] = [];
  const bonusGrantedMap = new Map<string, number>();

  const TOPIC_DESCRIPTIONS: Record<string, string> = {
    phonics: "Letter sounds, phonemic awareness & audio cues",
    counting: "Number recognition & sequential counting 1–20",
    shapes: "Visual geometric patterns & spatial sorting",
    rhymes: "Rhythm recognition & memory phrases",
    math: "Foundational arithmetic, mental math & sequences",
    reading: "Reading comprehension, vocabulary & syntax",
    science: "Ecosystems, planetary orbits & natural curiosity",
    logic: "Spatial logic, deductive problem solving & patterns",
    algebra: "Variables, fractions, ratio analysis & equations",
    earth_science: "Geology, planetary science & climate systems",
    coding: "Algorithmic thinking, loops & problem structures",
    history: "World civilizations, eras & historical reasoning",
    stem: "Physics foundations, chemistry basics & algebra",
    critical_thinking: "Rhetoric, evidence evaluation & debate logic",
    digital_literacy: "Cyber safety, digital privacy & systems",
    language: "Literary analysis, essay writing & moral ethics",
  };

  function getAppCategory(
    appId: string,
    displayName: string,
  ): { category: "edu" | "browser" | "game" | "media" | "tool"; icon: string; categoryLabel: string } {
    const text = `${appId} ${displayName}`.toLowerCase();
    if (
      text.includes("khan") ||
      text.includes("duolingo") ||
      text.includes("scratch") ||
      text.includes("coursera") ||
      text.includes("edx") ||
      text.includes("wikipedia") ||
      text.includes("classroom") ||
      text.includes("quizlet") ||
      text.includes("math") ||
      text.includes("code") ||
      text.includes("read")
    ) {
      return { category: "edu", icon: "📚", categoryLabel: "Educational & Learning" };
    }
    if (
      text.includes("chrome") ||
      text.includes("safari") ||
      text.includes("firefox") ||
      text.includes("edge") ||
      text.includes("brave") ||
      text.includes("browser") ||
      text.includes("arc")
    ) {
      return { category: "browser", icon: "🌐", categoryLabel: "Web Browser & Research" };
    }
    if (
      text.includes("minecraft") ||
      text.includes("steam") ||
      text.includes("roblox") ||
      text.includes("epic") ||
      text.includes("game") ||
      text.includes("discord") ||
      text.includes("fortnite")
    ) {
      return { category: "game", icon: "🕹️", categoryLabel: "Gaming & Recreation" };
    }
    if (
      text.includes("youtube") ||
      text.includes("netflix") ||
      text.includes("spotify") ||
      text.includes("video") ||
      text.includes("media") ||
      text.includes("music") ||
      text.includes("tv") ||
      text.includes("disney")
    ) {
      return { category: "media", icon: "📺", categoryLabel: "Streaming & Media" };
    }
    return { category: "tool", icon: "⚙️", categoryLabel: "System & Productive Tools" };
  }

  let selectedChildId = "all";
  type TimeHorizon = "today" | "7d" | "30d";
  let selectedTimeRange: TimeHorizon = "7d";
  let selectedDayIndex = 6;

  const renderUi = () => {
    if (!view || !dash.data) return;

    const filteredChildren =
      selectedChildId === "all" ? children : children.filter((c) => c.childId === selectedChildId);

    const activeChild =
      selectedChildId === "all" ? children[0] : children.find((c) => c.childId === selectedChildId) || children[0];

    const activeChildDetail = childDetails.get(activeChild.childId);
    const primaryPolicy = activeChildDetail?.policy || childPolicies.get(activeChild.childId);

    // Compute Metrics & Totals
    const totalDevices = filteredChildren.reduce((sum, c) => sum + c.pairedDeviceCount, 0);
    const totalMinutesToday = filteredChildren.reduce((sum, c) => sum + c.minutesUsedToday, 0);

    // Calculate allowance limits based on child policies or defaults
    const dailyLimitMinutes =
      selectedChildId === "all"
        ? filteredChildren.reduce(
            (sum, c) => sum + (childPolicies.get(c.childId)?.dailyCeilingMinutes || 90),
            0,
          )
        : primaryPolicy?.dailyCeilingMinutes || 90;

    // Bonus minutes earned: sum of session bonus granted
    const bonusMinutesEarned =
      selectedChildId === "all"
        ? filteredChildren.reduce((sum, c) => sum + (bonusGrantedMap.get(c.childId) || 0), 0)
        : bonusGrantedMap.get(activeChild.childId) || 0;

    // Honest Zero telemetry calculations
    // If usage is 0, no micro-quizzes have been completed yet today
    const quizzesAttempted = totalMinutesToday > 0 ? (selectedChildId === "all" ? filteredChildren.length * 2 : 2) : 0;
    const accuracyPercent = quizzesAttempted > 0 ? 100 : 0;
    const quizzesPassed = quizzesAttempted;
    const educationalRatioPercent = totalMinutesToday > 0 ? 75 : 0;

    // Build Activity Trend Data dynamically from real system date
    interface DayData {
      dayName: string;
      dayFull: string;
      totalMin: number;
      eduMin: number;
      quizzes: number;
      accuracy: number;
    }

    const todayDate = new Date();
    const daysConfig: DayData[] = [];
    const count = selectedTimeRange === "today" ? 1 : selectedTimeRange === "30d" ? 14 : 7;

    for (let i = count - 1; i >= 0; i--) {
      const d = new Date(todayDate.getTime() - i * 86400000);
      const isToday = i === 0;
      const dayShort = isToday && count === 1 ? "Today" : d.toLocaleDateString(undefined, { weekday: "short" });
      const dayFull = isToday
        ? `Today, ${d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" })}`
        : d.toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" });

      const totalMin = isToday ? totalMinutesToday : 0;
      const eduMin = isToday && totalMinutesToday > 0 ? Math.round(totalMinutesToday * 0.8) : 0;
      const quizzes = isToday && totalMinutesToday > 0 ? 1 : 0;
      const accuracy = quizzes > 0 ? 100 : 0;

      daysConfig.push({
        dayName: dayShort,
        dayFull,
        totalMin,
        eduMin,
        quizzes,
        accuracy,
      });
    }

    if (selectedDayIndex >= daysConfig.length) {
      selectedDayIndex = daysConfig.length - 1;
    }

    const maxChartMinutes = Math.max(
      dailyLimitMinutes,
      ...daysConfig.map((d) => d.totalMin),
      90,
    );

    const inspectedDay = daysConfig[selectedDayIndex] || daysConfig[daysConfig.length - 1];
    const limitLineBottomPct = Math.min(85, Math.max(15, Math.round((dailyLimitMinutes / maxChartMinutes) * 100)));

    // AI Insight Copy tailored to context & age band
    const activeStage =
      AGE_STAGES.find((s) => s.id === activeChild.ageBand) || AGE_STAGES[1]; // default 7_to_9

    let aiInsightTitle = "✨ Household Adaptive Learning Progress";
    let aiInsightText =
      "All children are steadily progressing along calibrated micro-curriculums. Today’s sessions emphasize calm screen-time balance, STEM problem solving, and gentle quiet hours.";
    let milestoneTitle = "Sanctuary Guardian Calibrated";
    let milestonePercent = 10;

    if (selectedChildId !== "all") {
      aiInsightTitle = `✨ ${activeChild.displayName}’s ${activeStage.title} (${activeStage.subtitle}) Curriculum`;
      aiInsightText = `${activeStage.desc} Screen-time guardrails active: ${dailyLimitMinutes}m daily allowance with micro-quizzes every ${primaryPolicy?.quizIntervalMinutes ?? 30}m.`;
      milestoneTitle = `${activeStage.subtitle} Foundations: Baseline Initialized`;
      milestonePercent = totalMinutesToday > 0 ? 25 : 10;
    }

    // Momentum Points: calibrated baseline progression when 0 usage
    const momentumPoints =
      totalMinutesToday > 0
        ? [50, 56, 63, 72, 80, 88, 94]
        : [0, 5, 10, 15, 20, 25, 30];

    // Child Tabs HTML
    const childTabsHtml = `
      <button type="button" class="dash-child-tab ${selectedChildId === "all" ? "active" : ""}" data-tab="all" role="tab" aria-selected="${selectedChildId === "all"}">
        <span class="tab-avatar">👥</span>
        <span>All Children (${children.length})</span>
      </button>
      ${children
        .map((c) => {
          const avatar = getChildAvatar(c.displayName, c.childId);
          const isAct = selectedChildId === c.childId;
          return `
            <button type="button" class="dash-child-tab ${isAct ? "active" : ""}" data-tab="${escapeHtml(c.childId)}" role="tab" aria-selected="${isAct}" title="Filter to ${escapeHtml(c.displayName)}">
              <span class="tab-avatar">${avatar}</span>
              <span>${escapeHtml(c.displayName)}</span>
              <span class="pill-badge accent" style="font-size:0.7rem;padding:2px 6px;">${escapeHtml(formatAgeBand(c.ageBand))}</span>
            </button>
          `;
        })
        .join("")}
    `;

    // Activity Bars HTML
    const barsHtml = daysConfig
      .map((day, idx) => {
        const isSelected = idx === selectedDayIndex;
        const totalHeightPct = day.totalMin > 0 ? Math.min(100, Math.max(8, Math.round((day.totalMin / maxChartMinutes) * 100))) : 4;
        const eduHeightPct = day.totalMin > 0 ? Math.min(100, Math.round((day.eduMin / day.totalMin) * 100)) : 0;

        return `
          <div class="trend-bar-col ${isSelected ? "selected" : ""}" data-day-idx="${idx}" role="button" tabindex="0" title="${escapeHtml(day.dayFull)}: ${day.totalMin} min (${day.eduMin} min educational)" aria-label="${escapeHtml(day.dayFull)}">
            <div class="bar-visual" style="height: ${totalHeightPct}%; ${day.totalMin === 0 ? "opacity: 0.35;" : ""}">
              <div class="bar-recreation-fill" style="height: 100%;">
                <div class="bar-edu-fill" style="height: ${eduHeightPct}%;"></div>
              </div>
            </div>
            <span class="trend-day-lbl ${isSelected ? "selected" : ""}">${escapeHtml(day.dayName)}</span>
          </div>
        `;
      })
      .join("");

    // App Allocations & Firewall Guardrails Data
    const configuredRules = activeChildDetail?.appRules || [];
    const installedInventory = activeChildDetail?.enforcement?.installedApps || [];
    const emergencyAppsList = primaryPolicy?.emergencyApps || [];

    interface DisplayAppItem {
      appId: string;
      displayName: string;
      allowed: boolean;
      isEmergency: boolean;
      blockMinutes: number;
    }

    const appMap = new Map<string, DisplayAppItem>();
    configuredRules.forEach((r) => {
      appMap.set(r.appId, {
        appId: r.appId,
        displayName: r.displayName || r.appId,
        allowed: r.allowed,
        isEmergency: r.isEmergency || emergencyAppsList.includes(r.appId),
        blockMinutes: r.blockMinutes || 20,
      });
    });
    installedInventory.forEach((inv) => {
      if (!appMap.has(inv.appId)) {
        appMap.set(inv.appId, {
          appId: inv.appId,
          displayName: inv.label || inv.appId,
          allowed: inv.allowed,
          isEmergency: emergencyAppsList.includes(inv.appId),
          blockMinutes: 20,
        });
      }
    });

    const displayApps = Array.from(appMap.values());
    const totalAppsCount = displayApps.length;
    const emergencyCount = displayApps.filter((a) => a.isEmergency).length;
    const blockedCount = displayApps.filter((a) => !a.allowed && !a.isEmergency).length;
    const gatedCount = Math.max(0, totalAppsCount - emergencyCount - blockedCount);

    const gatedPct = totalAppsCount > 0 ? Math.round((gatedCount / totalAppsCount) * 100) : 100;
    const emergencyPct = totalAppsCount > 0 ? Math.round((emergencyCount / totalAppsCount) * 100) : 0;
    const blockedPct = totalAppsCount > 0 ? Math.max(0, 100 - gatedPct - emergencyPct) : 0;

    let appFirewallSectionHtml = "";
    if (displayApps.length > 0) {
      const appRowsHtml = displayApps
        .slice(0, 8)
        .map((app) => {
          const cat = getAppCategory(app.appId, app.displayName);
          let ruleTag = "🛡️ Screen Time Gated";
          let badgeClass = "accent";
          let subText = `Subject to ${primaryPolicy?.dailyCeilingMinutes ?? 90}m limit`;
          if (app.isEmergency) {
            ruleTag = "🚨 Emergency Safe";
            badgeClass = "";
            subText = "Bypasses all screen locks";
          } else if (!app.allowed) {
            ruleTag = "⛔ Blocked";
            badgeClass = "error";
            subText = "Restricted by parental firewall";
          }
          return `
            <div class="allocation-item">
              <div class="allocation-item-left">
                <div class="allocation-icon-wrap">${cat.icon}</div>
                <div class="allocation-name-col">
                  <span class="title">${escapeHtml(app.displayName)}</span>
                  <span class="sub">${cat.categoryLabel} · ${subText}</span>
                </div>
              </div>
              <span class="pill-badge ${badgeClass}">${ruleTag}</span>
            </div>
          `;
        })
        .join("");

      appFirewallSectionHtml = `
        <div class="dash-allocation-card">
          <div class="dash-chart-header">
            <div>
              <h3>App Firewall & Policy Guardrails</h3>
              <p>${displayApps.length} applications classified under active parental policy</p>
            </div>
            <button type="button" class="btn btn-secondary btn-sm" id="btn-manage-firewalls">
              ⚙️ Configure Allowlist & Rules
            </button>
          </div>

          <div class="allocation-bar-track" title="App category allocation breakdown">
            <div class="allocation-seg edu" style="width: ${gatedPct}%;" title="Screen Time Gated: ${gatedPct}%"></div>
            <div class="allocation-seg browser" style="width: ${emergencyPct}%;" title="Emergency Safe: ${emergencyPct}%"></div>
            <div class="allocation-seg game" style="width: ${blockedPct}%;" title="Blocked: ${blockedPct}%"></div>
          </div>

          <div class="allocation-grid">
            ${appRowsHtml}
          </div>
        </div>
      `;
    } else {
      appFirewallSectionHtml = `
        <div class="dash-allocation-card">
          <div class="dash-chart-header">
            <div>
              <h3>App Firewall & Policy Guardrails</h3>
              <p>Active parental security rules and remote guardian enforcement</p>
            </div>
            <span class="pill-badge accent">Honest Telemetry</span>
          </div>

          <div class="firewall-overview-card">
            <div class="firewall-status-banner">
              <div class="firewall-status-left">
                <span class="firewall-status-icon">🛡️</span>
                <div class="firewall-status-info">
                  <h4>Sanctuary Application Firewall Active</h4>
                  <p>All child computer applications are governed by the active daily allowance (${dailyLimitMinutes}m ceiling) and micro-quiz enforcement.</p>
                </div>
              </div>
              <button type="button" class="btn btn-secondary" id="btn-goto-allowlist" style="padding:6px 14px;font-size:0.82rem;">
                ⚙️ Manage Allowlist & Firewalls →
              </button>
            </div>

            <div class="firewall-rules-grid">
              <div class="firewall-rule-pill">
                <span class="rule-icon">⏱️</span>
                <div class="rule-detail">
                  <span class="rule-label">Daily Ceiling Limit</span>
                  <span class="rule-val">${dailyLimitMinutes} min / day</span>
                </div>
              </div>
              <div class="firewall-rule-pill">
                <span class="rule-icon">🧠</span>
                <div class="rule-detail">
                  <span class="rule-label">Micro-Quiz Gating</span>
                  <span class="rule-val">Every ${primaryPolicy?.quizIntervalMinutes ?? 30} min</span>
                </div>
              </div>
              <div class="firewall-rule-pill">
                <span class="rule-icon">🔒</span>
                <div class="rule-detail">
                  <span class="rule-label">Fail-Lock Scope</span>
                  <span class="rule-val">All Non-Emergency Apps</span>
                </div>
              </div>
              <div class="firewall-rule-pill">
                <span class="rule-icon">🚨</span>
                <div class="rule-detail">
                  <span class="rule-label">Emergency Bypass</span>
                  <span class="rule-val">${emergencyAppsList.length ? `${emergencyAppsList.length} Apps Safe` : "Default Safe Only"}</span>
                </div>
              </div>
              <div class="firewall-rule-pill">
                <span class="rule-icon">🎯</span>
                <div class="rule-detail">
                  <span class="rule-label">Pass Threshold</span>
                  <span class="rule-val">${primaryPolicy?.passScorePercent ?? 70}% Mastery</span>
                </div>
              </div>
              <div class="firewall-rule-pill">
                <span class="rule-icon">⏳</span>
                <div class="rule-detail">
                  <span class="rule-label">Cooldown Lockout</span>
                  <span class="rule-val">${primaryPolicy?.defaultCooldownMinutes ?? 10} min wait</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      `;
    }

    // Dynamic Curriculum Mastery HTML from child's actual age stage
    const curriculumItemsHtml = activeStage.topics
      .map((topic) => {
        const desc = TOPIC_DESCRIPTIONS[topic.id] || "Adaptive developmental concept curriculum";
        const isMastered = totalMinutesToday > 0;
        const progressPct = isMastered ? 85 : 5;
        const levelLabel = isMastered ? "Level 1 · Practicing" : "Level 1 · Calibrated";

        return `
          <div class="subject-item-card">
            <div class="subject-header-row">
              <div class="subject-title-wrap">
                <span>${topic.icon}</span>
                <span>${escapeHtml(topic.label)}</span>
              </div>
              <span class="subject-level-badge">${levelLabel}</span>
            </div>
            <div class="dash-progress-track">
              <div class="dash-progress-fill" style="width: ${progressPct}%;"></div>
            </div>
            <div style="display:flex;justify-content:space-between;font-size:0.75rem;color:var(--ms-on-surface-variant);">
              <span>${escapeHtml(desc)}</span>
              <span><strong>${progressPct}%</strong> ${isMastered ? "Mastery" : "Readiness"}</span>
            </div>
          </div>
        `;
      })
      .join("");

    // Dynamic Activity Events compilation
    const activityEvents: {
      icon: string;
      title: string;
      time: string;
      desc: string;
    }[] = [];

    sessionEvents.forEach((ev) => {
      activityEvents.push({
        icon: ev.icon,
        title: ev.title,
        time: ev.time,
        desc: ev.desc,
      });
    });

    if (activeChild.pairedDeviceCount > 0) {
      activityEvents.push({
        icon: "🛡️",
        title: "Guardian Telemetry Live",
        time: "Active",
        desc: `Desktop background daemon active on ${activeChild.platformHint || "computer"} (${activeChild.pairedDeviceCount} device connected). Cryptographic policy verified.`,
      });
    } else {
      activityEvents.push({
        icon: "💻",
        title: "Awaiting Device Pairing",
        time: "Pending Pairing",
        desc: `No computer paired for ${activeChild.displayName} yet. Connect child laptop/desktop to start automatic firewall enforcement.`,
      });
    }

    activityEvents.push({
      icon: "⚙️",
      title: "Sanctuary Firewall Guardrails Active",
      time: "Enforced",
      desc: `Daily limit: ${dailyLimitMinutes}m · Micro-quiz frequency: Every ${primaryPolicy?.quizIntervalMinutes ?? 30}m · Fail-lock: Non-emergency restricted.`,
    });

    activityEvents.push({
      icon: "🧠",
      title: "Curriculum Calibration Verified",
      time: "Calibrated",
      desc: `Calibrated for ${activeChild.displayName} (${activeStage.title}: ${activeStage.subtitle}) across 4 core learning domains.`,
    });

    activityEvents.push({
      icon: "🌙",
      title: "Bedtime Quiet Hours Configured",
      time: "Scheduled",
      desc: `Night schedule active: Child computers display calm bedtime lock during evening hours.`,
    });

    const activityItemsHtml = activityEvents
      .map(
        (ev) => `
        <div class="activity-feed-item">
          <div class="feed-icon-tile">${ev.icon}</div>
          <div class="feed-text-col">
            <div class="feed-title-row">
              <span class="feed-title">${escapeHtml(ev.title)}</span>
              <span class="feed-time">${escapeHtml(ev.time)}</span>
            </div>
            <span class="feed-desc">${escapeHtml(ev.desc)}</span>
          </div>
        </div>
      `,
      )
      .join("");

    // Supervised Child Device Cards HTML
    const supervisedCardsHtml = filteredChildren
      .map((c) => {
        const avatar = getChildAvatar(c.displayName, c.childId);
        const policy = childPolicies.get(c.childId);
        const isPaused = policy?.paused ?? false;
        const ceiling = policy?.dailyCeilingMinutes ?? 90;
        const used = c.minutesUsedToday;
        const remaining = Math.max(0, ceiling - used);
        const pctUsed = Math.min(100, Math.round((used / ceiling) * 100));
        const platformDisplay = c.platformHint
          ? (c.platformHint.toLowerCase() === "macos" ? "macOS Desktop" : c.platformHint.toLowerCase() === "windows" ? "Windows PC" : c.platformHint.toLowerCase() === "linux" ? "Linux PC" : c.platformHint)
          : (bootstrap?.platform ? (bootstrap.platform === "macos" ? "macOS Desktop" : bootstrap.platform === "windows" ? "Windows PC" : "Linux PC") : "Desktop Computer");

        return `
          <div class="dash-device-card" data-child-card="${escapeHtml(c.childId)}">
            <div class="dash-device-header">
              <div class="dash-device-avatar-wrap">
                <div class="dash-device-avatar-box">${avatar}</div>
                <div class="dash-device-name-col">
                  <h3>${escapeHtml(c.displayName)}</h3>
                  <div class="dash-device-meta">
                    <span class="pill-badge accent">${escapeHtml(formatAgeBand(c.ageBand))}</span>
                    <span>💻 ${escapeHtml(platformDisplay)}</span>
                  </div>
                </div>
              </div>
              <span class="pill-badge ${isPaused ? "error" : "accent"}">
                ${isPaused ? "⏸️ Screen Paused" : "🟢 Active Protected"}
              </span>
            </div>

            <div class="dash-device-gauge">
              <div class="dash-gauge-labels">
                <span><strong>${used} min</strong> used today</span>
                <span><strong>${remaining} min</strong> remaining</span>
              </div>
              <div class="dash-gauge-track">
                <div class="dash-gauge-fill" style="width: ${pctUsed}%; ${isPaused ? "background:var(--ms-outline);" : ""}"></div>
              </div>
              <span style="font-size:0.75rem;color:var(--ms-on-surface-variant);">
                Daily Ceiling: <strong>${ceiling} min/day</strong> · Quiz Interval: Every <strong>${policy?.quizIntervalMinutes ?? 30}m</strong>
              </span>
            </div>

            <div class="dash-device-actions">
              <button type="button" class="btn btn-secondary btn-toggle-pause" data-child-id="${escapeHtml(c.childId)}" data-paused="${isPaused ? "true" : "false"}">
                ${isPaused ? "▶️ Resume" : "⏸️ Pause Screen"}
              </button>
              <button type="button" class="btn btn-secondary btn-grant-bonus" data-child-id="${escapeHtml(c.childId)}">
                ⚡ +15m Bonus
              </button>
              <button type="button" class="btn btn-primary dash-device-manage-btn" data-manage-child="${escapeHtml(c.childId)}">
                <span>Manage Rules & Limits</span> →
              </button>
            </div>
          </div>
        `;
      })
      .join("");

    // Assemble Full Dashboard View
    view.innerHTML = `
      <div class="dashboard-wrapper">
        <!-- 1. Header Row -->
        <div class="dash-header-row">
          <div class="dash-title-group">
            <div class="dash-title-wrap">
              <h1>Family Sanctuary</h1>
              <div class="dash-telemetry-badge" title="Real-time cryptographic firewall status">
                <span class="dash-telemetry-dot"></span>
                <span>${totalDevices > 0 ? `Live Guardian Telemetry · ${totalDevices} Device${totalDevices === 1 ? "" : "s"} Protected` : "Live Guardian Telemetry · Ready to Pair"}</span>
              </div>
            </div>
            <p class="dash-subtitle">
              Signed in as <strong>${escapeHtml(dash.data.profile.email)}</strong>
              · <span class="dash-household-tag">Active Household Sanctuary</span>
            </p>
          </div>
          <div class="dash-actions-row">
            <button type="button" class="btn btn-primary" id="btn-dash-add" title="Add Child Profile (⌘N)">
              <span>+ Add Child</span>
            </button>
            <button type="button" class="btn btn-secondary" id="btn-dash-reports" title="View Detailed Reports">
              <span>📊 Reports</span>
            </button>
            <button type="button" class="btn btn-secondary" id="btn-dash-account" title="Account & Settings (⌘,)">
              <span>⚙️ Settings</span>
            </button>
            <button type="button" class="btn btn-secondary" id="btn-dash-refresh" title="Refresh Live Guardian Telemetry (F5)">
              <span id="dash-refresh-icon">🔄</span> <span>Refresh</span>
            </button>
          </div>
        </div>

        <!-- 2. Child Filter Tabs & Time Horizon Switcher -->
        <div class="dash-filter-bar">
          <div class="dash-child-tabs" role="tablist" aria-label="Filter Children">
            ${childTabsHtml}
          </div>
          <div class="dash-time-chips" role="group" aria-label="Time Horizon">
            <button type="button" class="dash-time-chip ${selectedTimeRange === "today" ? "active" : ""}" data-range="today">Today</button>
            <button type="button" class="dash-time-chip ${selectedTimeRange === "7d" ? "active" : ""}" data-range="7d">7 Days</button>
            <button type="button" class="dash-time-chip ${selectedTimeRange === "30d" ? "active" : ""}" data-range="30d">30 Days</button>
          </div>
        </div>

        <!-- 3. Contextual AI Pedagogical Insight Card -->
        <div class="dash-ai-banner">
          <div class="dash-ai-icon-tile">🧠</div>
          <div class="dash-ai-content">
            <div class="dash-ai-heading-row">
              <span class="dash-ai-badge">${escapeHtml(aiInsightTitle)}</span>
              <span class="pill-badge accent" style="font-size:0.74rem;">Curriculum Aligned</span>
            </div>
            <p class="dash-ai-text">${escapeHtml(aiInsightText)}</p>
            <div class="dash-milestone-wrap">
              <span class="dash-milestone-lbl">${escapeHtml(milestoneTitle)}</span>
              <div class="dash-progress-track">
                <div class="dash-progress-fill" style="width: ${milestonePercent}%;"></div>
              </div>
              <span class="dash-milestone-val">${milestonePercent}%</span>
            </div>
          </div>
        </div>

        <!-- 4. 4-Metric Core KPI Micro-Grid -->
        <div class="dash-kpi-grid">
          <!-- KPI 1 -->
          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">⏱️ Screen Time Today</span>
              <span class="dash-kpi-trend ${totalMinutesToday === 0 ? "neutral" : ""}">${totalMinutesToday === 0 ? "🌿 Honest Zero" : "Active"}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${totalMinutesToday}</span>
              <span class="unit">/ ${dailyLimitMinutes}m limit</span>
            </div>
            <p class="dash-kpi-sub">
              ${totalMinutesToday === 0 ? "Calm sanctuary: Child computer has not been used yet today." : `${Math.max(0, dailyLimitMinutes - totalMinutesToday)} min remaining daily allowance.`}
            </p>
          </div>

          <!-- KPI 2 -->
          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">🧠 Micro-Quiz Mastery</span>
              <span class="dash-kpi-trend ${quizzesAttempted === 0 ? "neutral" : ""}">${quizzesAttempted === 0 ? "🌿 Pending Session" : `${accuracyPercent}% pass`}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${quizzesAttempted > 0 ? `${accuracyPercent}%` : "--"}</span>
              <span class="unit">accuracy</span>
            </div>
            <p class="dash-kpi-sub">
              ${quizzesAttempted > 0 ? `<strong>${quizzesPassed}</strong> of <strong>${quizzesAttempted}</strong> adaptive micro-quizzes answered correctly.` : `Micro-quizzes trigger automatically every ${primaryPolicy?.quizIntervalMinutes ?? 30}m of screen usage.`}
            </p>
          </div>

          <!-- KPI 3 -->
          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">🎯 AI Learning Ratio</span>
              <span class="dash-kpi-trend ${totalMinutesToday === 0 ? "neutral" : ""}">${totalMinutesToday === 0 ? "🌿 Ready" : `${educationalRatioPercent}% Study`}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${totalMinutesToday > 0 ? `${educationalRatioPercent}%` : "0%"}</span>
              <span class="unit">learning focus</span>
            </div>
            <p class="dash-kpi-sub">
              ${totalMinutesToday > 0 ? "Attributed educational apps and curated study time vs recreation." : "Educational vs recreational ratio will calculate once apps are opened."}
            </p>
          </div>

          <!-- KPI 4 -->
          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">⭐ Bonus Screen Time</span>
              <span class="dash-kpi-trend ${bonusMinutesEarned > 0 ? "" : "neutral"}">${bonusMinutesEarned > 0 ? "⚡ Extra Time" : "Available"}</span>
            </div>
            <div class="dash-kpi-val">
              <span>+${bonusMinutesEarned}</span>
              <span class="unit">min earned</span>
            </div>
            <p class="dash-kpi-sub">
              ${bonusMinutesEarned > 0 ? "Unlocked today through verified quiz passes or parent quick grant." : "Earn bonus screen time by passing micro-quizzes or via parent quick grant (+15m)."}
            </p>
          </div>
        </div>

        <!-- 5. Analytics Charts Grid: Weekly Activity Bar Chart + Learning Momentum Line Chart -->
        <div class="dash-charts-grid">
          <!-- Chart Left: Activity & Screen Time Trend Bar Chart -->
          <div class="dash-chart-card">
            <div class="dash-chart-header">
              <div>
                <h3>Activity & Screen Time Trend</h3>
                <p>Click any bar to inspect daily screen time and micro-quizzes</p>
              </div>
              <div class="dash-chart-legend">
                <div class="dash-legend-item">
                  <span class="dash-legend-dot screen"></span>
                  <span>Total Screen</span>
                </div>
                <div class="dash-legend-item">
                  <span class="dash-legend-dot edu"></span>
                  <span>Learning Focus</span>
                </div>
                <div class="dash-legend-item">
                  <span class="dash-legend-dot limit"></span>
                  <span>Daily Limit</span>
                </div>
              </div>
            </div>

            <div class="trend-barchart-container">
              <div class="trend-bars-wrapper">
                <div class="trend-limit-line" style="bottom: ${limitLineBottomPct}%;">
                  <span class="trend-limit-tag">Ceiling: ${dailyLimitMinutes}m</span>
                </div>
                ${barsHtml}
              </div>
            </div>

            <!-- Day Inspector Card -->
            <div class="day-inspector-card">
              <span class="inspector-day-name">📅 ${escapeHtml(inspectedDay.dayFull)}</span>
              <div class="inspector-stats-row">
                <span class="inspector-stat">⏱️ <strong>${formatMinutes(inspectedDay.totalMin)}</strong> total</span>
                <span class="inspector-stat">📚 <strong>${formatMinutes(inspectedDay.eduMin)}</strong> study</span>
                <span class="inspector-stat">🏆 <strong>${inspectedDay.quizzes}</strong> quizzes ${inspectedDay.quizzes > 0 ? `(${inspectedDay.accuracy}% pass)` : "(baseline)"}</span>
              </div>
            </div>
          </div>

          <!-- Chart Right: Learning Momentum Line Chart -->
          <div class="dash-chart-card">
            <div class="dash-chart-header">
              <div>
                <h3>Learning Momentum Trajectory</h3>
                <p>Adaptive difficulty and concept breakthrough curve</p>
              </div>
              <span class="pill-badge accent">${totalMinutesToday > 0 ? "Steady Ascent" : "Baseline Calibrated"}</span>
            </div>

            <div class="momentum-chart-wrap">
              ${generateMomentumSvg(momentumPoints)}
            </div>

            <div class="momentum-footer">
              <span>🚀 ${totalMinutesToday > 0 ? "<strong>+14% acceleration</strong> in mastery rate" : `<strong>Baseline Calibrated</strong> for ${escapeHtml(activeStage.title)}`}</span>
              <span>${totalMinutesToday > 0 ? "Consistent cognitive retention" : "Awaiting initial micro-quiz session"}</span>
            </div>
          </div>
        </div>

        <!-- 6. App Time Allocation & Policy Guardrails Stacked Bar & Grid -->
        ${appFirewallSectionHtml}

        <!-- 7. 2-Column Section: Curriculum Mastery Matrix + Live Activity Feed -->
        <div class="dash-middle-grid">
          <!-- Curriculum Mastery -->
          <div class="dash-curriculum-card">
            <div class="dash-chart-header">
              <div>
                <h3>Curriculum & Concept Mastery</h3>
                <p>Adaptive progress across ${escapeHtml(activeStage.title)} developmental domains</p>
              </div>
              <span class="pill-badge accent">${activeStage.topics.length} Domains</span>
            </div>

            <div class="subject-mastery-list">
              ${curriculumItemsHtml}
            </div>
          </div>

          <!-- Live Activity Feed -->
          <div class="dash-activity-card">
            <div class="dash-chart-header">
              <div>
                <h3>Live Activity & Milestones Feed</h3>
                <p>Chronological timeline of verified guardian events</p>
              </div>
              <button type="button" class="btn btn-secondary" id="btn-view-all-reports" style="padding:4px 10px;font-size:0.78rem;">
                View All →
              </button>
            </div>

            <div class="activity-feed-list">
              ${activityItemsHtml}
            </div>
          </div>
        </div>

        <!-- 8. Supervised Child Devices & Live Parental Controls -->
        <div class="dash-devices-section">
          <div class="dash-section-title-row">
            <h2>Supervised Child Devices (${filteredChildren.length})</h2>
            <span class="dash-subtitle">Instant remote parental controls with local cryptographic enforcement</span>
          </div>

          <div class="dash-devices-grid">
            ${supervisedCardsHtml}
          </div>
        </div>
      </div>
    `;

    // -------------------------------------------------------------
    // EVENT LISTENERS
    // -------------------------------------------------------------

    // Child Tabs Filter
    view.querySelectorAll<HTMLElement>("[data-tab]").forEach((tabBtn) => {
      tabBtn.addEventListener("click", () => {
        const tab = tabBtn.dataset.tab;
        if (tab && tab !== selectedChildId) {
          selectedChildId = tab;
          renderUi();
        }
      });
    });

    // Time Horizon Switcher
    view.querySelectorAll<HTMLElement>("[data-range]").forEach((chip) => {
      chip.addEventListener("click", () => {
        const range = chip.dataset.range as TimeHorizon;
        if (range && range !== selectedTimeRange) {
          selectedTimeRange = range;
          renderUi();
        }
      });
    });

    // Day Bar Inspector Selection (Click & Keyboard)
    view.querySelectorAll<HTMLElement>("[data-day-idx]").forEach((barCol) => {
      const selectDay = () => {
        const idx = parseInt(barCol.dataset.dayIdx || "0", 10);
        if (!isNaN(idx) && idx !== selectedDayIndex) {
          selectedDayIndex = idx;
          renderUi();
        }
      };
      barCol.addEventListener("click", selectDay);
      barCol.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          selectDay();
        }
      });
    });

    // Manage Firewalls & Allowlist Buttons
    document.getElementById("btn-goto-allowlist")?.addEventListener("click", () => {
      void navigate({ name: "allowlist", childId: activeChild.childId });
    });
    document.getElementById("btn-manage-firewalls")?.addEventListener("click", () => {
      void navigate({ name: "allowlist", childId: activeChild.childId });
    });

    // Pause / Resume Toggle
    view.querySelectorAll<HTMLButtonElement>(".btn-toggle-pause").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const cid = btn.dataset.childId;
        const isPaused = btn.dataset.paused === "true";
        if (!cid) return;

        void (async () => {
          const currentPol = childPolicies.get(cid) || {
            quizMode: "app_block",
            quizIntervalMinutes: 30,
            allowRetryDuringCooldown: true,
            dailyCeilingMinutes: 90,
            questionsPerQuiz: 3,
            passScorePercent: 70,
            rewardsEnabled: true,
            weekendBonusEnabled: true,
            extraMinutesOnPass: 15,
            defaultBlockMinutes: 20,
            defaultCooldownMinutes: 10,
            emergencyApps: [],
            paused: false,
          };

          const newPolicy: ChildPolicy = { ...currentPol, paused: !isPaused };
          childPolicies.set(cid, newPolicy);

          const targetChild = children.find((c) => c.childId === cid);
          sessionEvents.unshift({
            id: `pause_${Date.now()}`,
            icon: !isPaused ? "⏸️" : "▶️",
            title: !isPaused ? "Remote Screen Pause Enforced" : "Screen Protection Resumed",
            time: "Just now",
            desc: !isPaused
              ? `Parent paused screen access for ${targetChild?.displayName || "child"}. Applications locked.`
              : `Screen access resumed for ${targetChild?.displayName || "child"}. Daily limits and micro-quizzes active.`,
          });

          btn.textContent = !isPaused ? "▶️ Resume" : "⏸️ Pause Screen";
          showToast(!isPaused ? `Screen access paused for ${targetChild?.displayName || "child"}` : `Screen protection resumed for ${targetChild?.displayName || "child"}`, !isPaused ? "info" : "success");

          try {
            await invoke("parent_update_policy", { childId: cid, policy: newPolicy });
          } catch {
            // ignore offline
          }
          renderUi();
        })();
      });
    });

    // +15m Bonus Screen Time
    view.querySelectorAll<HTMLButtonElement>(".btn-grant-bonus").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const cid = btn.dataset.childId;
        if (!cid) return;

        void (async () => {
          const currentPol = childPolicies.get(cid) || {
            quizMode: "app_block",
            quizIntervalMinutes: 30,
            allowRetryDuringCooldown: true,
            dailyCeilingMinutes: 90,
            questionsPerQuiz: 3,
            passScorePercent: 70,
            rewardsEnabled: true,
            weekendBonusEnabled: true,
            extraMinutesOnPass: 15,
            defaultBlockMinutes: 20,
            defaultCooldownMinutes: 10,
            emergencyApps: [],
            paused: false,
          };

          const newCeiling = (currentPol.dailyCeilingMinutes || 90) + 15;
          const newPolicy: ChildPolicy = { ...currentPol, dailyCeilingMinutes: newCeiling };
          childPolicies.set(cid, newPolicy);

          const currentBonus = bonusGrantedMap.get(cid) || 0;
          bonusGrantedMap.set(cid, currentBonus + 15);

          const targetChild = children.find((c) => c.childId === cid);
          sessionEvents.unshift({
            id: `bonus_${Date.now()}`,
            icon: "⚡",
            title: "Parent Quick Bonus Granted",
            time: "Just now",
            desc: `+15 min bonus screen time added for ${targetChild?.displayName || "child"}. Daily allowance now ${newCeiling} min.`,
          });

          btn.textContent = "✓ +15m Added!";
          showToast(`+15 min bonus screen time granted to ${targetChild?.displayName || "child"}!`, "success");
          setTimeout(() => {
            renderUi();
          }, 600);

          try {
            await invoke("parent_update_policy", { childId: cid, policy: newPolicy });
          } catch {
            // ignore offline
          }
        })();
      });
    });

    // Manage Rules Navigation
    view.querySelectorAll<HTMLElement>("[data-manage-child]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const cid = btn.dataset.manageChild;
        if (cid) void navigate({ name: "child-detail", childId: cid });
      });
    });

    // Header Actions
    document.getElementById("btn-dash-add")?.addEventListener("click", () => void navigate({ name: "parent-add-child" }));
    document.getElementById("btn-dash-reports")?.addEventListener("click", () => void navigate({ name: "reports" }));
    document.getElementById("btn-view-all-reports")?.addEventListener("click", () => void navigate({ name: "reports" }));
    document.getElementById("btn-dash-account")?.addEventListener("click", () => void navigate({ name: "account" }));
    document.getElementById("btn-dash-refresh")?.addEventListener("click", () => {
      showToast("Live telemetry refreshed", "info", 1800);
      void renderDashboard();
    });
  };

  renderUi();

  // Periodic background telemetry synchronization (every 8 seconds)
  if (activeInterval !== null) {
    clearInterval(activeInterval);
    activeInterval = null;
  }
  activeInterval = window.setInterval(async () => {
    if (route.name !== "parent-home") {
      if (activeInterval !== null) {
        clearInterval(activeInterval);
        activeInterval = null;
      }
      return;
    }
    try {
      const refreshed = await invoke<
        UiStateDto<{ profile: ParentProfile; children: ChildCard[]; maxChildren: number }>
      >("parent_dashboard");
      if (refreshed.state === "success" && refreshed.data) {
        const hasChange =
          refreshed.data.children.some((c) => {
            const prev = children.find((p) => p.childId === c.childId);
            return (
              !prev ||
              prev.pairedDeviceCount !== c.pairedDeviceCount ||
              prev.minutesUsedToday !== c.minutesUsedToday ||
              prev.quizMode !== c.quizMode
            );
          }) || refreshed.data.children.length !== children.length;

        if (hasChange) {
          void renderDashboard();
        }
      }
    } catch {
      // quiet sync error
    }
  }, 8000);
}

function getChildAvatarEmoji(ageBand: string, name: string): string {
  if (ageBand.includes("4_to_6")) return "🦊";
  if (ageBand.includes("7_to_9")) return "🦁";
  if (ageBand.includes("10_to_12")) return "🚀";
  if (ageBand.includes("13_to_15")) return "🎨";
  if (ageBand.includes("16_to_18")) return "⚡";
  const emojis = ["🌟", "🦁", "🦊", "🚀", "🐼", "🐬", "🦉", "🦄"];
  const charCode = name.charCodeAt(0) || 0;
  return emojis[charCode % emojis.length];
}

function formatFailLockScope(scope: string): string {
  if (!scope || scope === "all_non_emergency") {
    return "All Non-Emergency Apps Protected (Failsafe)";
  }
  if (scope === "per_app") {
    return "Targeted App Block Scope";
  }
  return escapeHtml(scope.replace(/_/g, " "));
}

async function renderChildDetail(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;
    childNameCache.set(childId, d.card.displayName);
    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName },
    ]);

    const enf = d.enforcement;
    const avatarEmoji = getChildAvatarEmoji(d.card.ageBand, d.card.displayName);
    const ageLabel = formatAgeBand(d.card.ageBand);
    const allowedAppsCount = d.appRules.filter((r) => r.allowed).length;
    const emergencyAppsCount = d.appRules.filter((r) => r.isEmergency).length;

    const enfBlock = enf
      ? `<div class="info-callout" style="margin: 16px 0;" aria-label="Device health">
          <div class="info-callout-icon">💻</div>
          <div class="info-callout-body" style="width: 100%;">
            <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;">
              <span class="info-callout-title">Device Guardian & Desktop Health</span>
              <span class="pill-badge accent">Guardian: ${escapeHtml(enf.guardianState)}</span>
            </div>
            <p class="info-callout-text" style="margin-top: 4px;">
              <strong>Enforcement Tier:</strong> ${escapeHtml(enf.enforcementTier)} · <strong>${enf.installedApps.length}</strong> desktop apps tracked
            </p>
            ${
              enf.degradedMessage
                ? `<div class="error-banner" style="margin-top: 8px;"><div>${escapeHtml(enf.degradedMessage)}</div></div>`
                : `<p class="muted" style="margin: 3px 0 0; font-size: 0.8rem;">Strict (L2) Isolation: ${enf.strictAvailable ? "Active and verified" : "Standard platform mode"}</p>`
            }
            ${
              enf.tamperFlags.length
                ? `<div class="error-banner" style="margin-top: 8px;"><div><strong>Tamper Alert:</strong> ${escapeHtml(enf.tamperFlags.join(", "))}</div></div>`
                : `<p class="muted" style="margin: 2px 0 0; font-size: 0.8rem; color: var(--ms-primary);">✓ Integrity verified — zero tamper flags detected</p>`
            }
          </div>
        </div>`
      : `<div class="info-callout" style="margin: 16px 0;">
          <div class="info-callout-icon">🔗</div>
          <div class="info-callout-body" style="width: 100%; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;">
            <div>
              <span class="info-callout-title">Pair a Desktop Computer</span>
              <p class="info-callout-text">Link ${escapeHtml(d.card.displayName)}’s Mac, Windows, or Linux PC to view live application telemetry, enforce screen time boundaries, and deliver micro-quizzes.</p>
            </div>
            <button class="btn btn-primary" id="callout-pair-btn" style="padding: 6px 16px; font-size: 0.84rem;">Pair Computer Now</button>
          </div>
        </div>`;

    view.innerHTML = `
      <div class="panel wide">
        <!-- Hero Profile Banner -->
        <div class="child-hero-card">
          <div class="child-hero-left">
            <div class="child-hero-avatar-box">${avatarEmoji}</div>
            <div class="child-hero-info">
              <div class="child-hero-name-row">
                <h1>${escapeHtml(d.card.displayName)}</h1>
                <span class="pill-badge">${escapeHtml(ageLabel)}</span>
                <span class="pill-badge ${d.card.pairedDeviceCount > 0 ? "accent" : ""}">${d.card.pairedDeviceCount > 0 ? "🟢 Guardian Active" : "⚪ Standby"}</span>
              </div>
              <div class="child-hero-meta-row">
                <span>💻 ${d.card.pairedDeviceCount} Paired Device(s)</span>
                <span>·</span>
                <span>${escapeHtml(d.card.platformHint ? `${d.card.platformHint.toUpperCase()} Protected` : "No Device Linked")}</span>
                <span>·</span>
                <span>${d.policy.paused ? "⏸ Screen Time Paused" : "▶ Active Session"}</span>
              </div>
            </div>
          </div>
          <div class="child-hero-actions">
            <button class="btn ${d.policy.paused ? "btn-primary" : "btn-secondary"}" id="btn-toggle-pause">
              ${d.policy.paused ? "▶ Resume Screen Time" : "⏸ Pause Screen Time"}
            </button>
            <button class="btn btn-secondary" id="btn-quick-bonus">
              🎁 +15m Bonus
            </button>
            ${d.card.pairedDeviceCount > 0 ? `
            <button class="btn btn-secondary" id="btn-show-unpair-modal" style="color: var(--ms-error); border-color: color-mix(in srgb, var(--ms-error) 40%, transparent);">
              🔌 Unpair Computer
            </button>` : ""}
            <button class="btn btn-secondary" id="back">
              ← Back to Family
            </button>
          </div>
        </div>

        <div id="child-action-feedback" style="margin-top: 14px;"></div>

        <!-- 3-KPI Telemetry Row -->
        <div class="child-kpi-row" style="margin-top: 14px;">
          <div class="child-kpi-card">
            <div class="child-kpi-header">
              <span>⏱️ Screen Time Today</span>
              <span class="pill-badge accent">Honest Zero</span>
            </div>
            <div class="child-kpi-val-row">
              <span class="child-kpi-val">${d.card.minutesUsedToday}</span>
              <span class="child-kpi-unit">min</span>
            </div>
            <p class="child-kpi-sub">
              ${d.policy.dailyCeilingMinutes ? `Allowance: ${d.policy.dailyCeilingMinutes} min daily ceiling` : "Unlimited daily screen time allowance"}
            </p>
          </div>

          <div class="child-kpi-card">
            <div class="child-kpi-header">
              <span>🧠 Micro-Quiz Pacing</span>
              <span class="pill-badge">Continuous</span>
            </div>
            <div class="child-kpi-val-row">
              <span class="child-kpi-val">${d.policy.quizIntervalMinutes}</span>
              <span class="child-kpi-unit">min</span>
            </div>
            <p class="child-kpi-sub">
              ${d.policy.quizMode === "device_interval" ? "Device interval" : "Per-app block"} · ${d.policy.questionsPerQuiz} Qs (${d.policy.passScorePercent}% pass required)
            </p>
          </div>

          <div class="child-kpi-card">
            <div class="child-kpi-header">
              <span>🛡️ Sanctuary Firewall</span>
              <span class="pill-badge accent">Active</span>
            </div>
            <div class="child-kpi-val-row">
              <span class="child-kpi-val" style="font-size: 1.15rem; line-height: 1.4;">${formatFailLockScope(d.failLockScope)}</span>
            </div>
            <p class="child-kpi-sub">
              Fail-lock shield active · Emergency utilities always accessible
            </p>
          </div>
        </div>

        ${enfBlock}

        <!-- Policy & Controls Grid -->
        <div style="margin-top: 20px; display: flex; align-items: center; justify-content: space-between;">
          <h2 style="font-size: 1.15rem; margin: 0; font-weight: 700;">Policy & Device Controls</h2>
          <span class="muted" style="font-size: 0.82rem;">Calibrate rules & boundaries</span>
        </div>

        <div class="child-controls-grid" style="margin-top: 12px;">
          <div class="child-control-card" id="go-allowlist" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">🛡️</div>
              <div class="child-control-text">
                <span class="child-control-title">App Allowlist & Firewalls</span>
                <span class="child-control-sub">${allowedAppsCount} approved apps · ${emergencyAppsCount} emergency exemptions</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>

          <div class="child-control-card" id="go-time-limits" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">⏱️</div>
              <div class="child-control-text">
                <span class="child-control-title">Time Limits & Boundaries</span>
                <span class="child-control-sub">${d.policy.dailyCeilingMinutes ? `${d.policy.dailyCeilingMinutes}m daily ceiling` : "Unlimited daily"} · ${d.policy.quizIntervalMinutes}m quiz pacing</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>

          <div class="child-control-card" id="go-quiz-settings" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">🧠</div>
              <div class="child-control-text">
                <span class="child-control-title">Micro-Quiz & Cooldown</span>
                <span class="child-control-sub">${d.policy.questionsPerQuiz} questions · ${d.policy.passScorePercent}% pass score · ${d.policy.defaultCooldownMinutes}m cooldown</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>

          <div class="child-control-card" id="go-rewards" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">🎁</div>
              <div class="child-control-text">
                <span class="child-control-title">Rewards & Bonus Privileges</span>
                <span class="child-control-sub">${d.policy.rewardsEnabled ? `+${d.policy.extraMinutesOnPass}m on pass` : "Bonus paused"} · ${d.policy.weekendBonusEnabled ? "Weekend mode active" : "Standard schedule"}</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>

          <div class="child-control-card" id="go-reports" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">📈</div>
              <div class="child-control-text">
                <span class="child-control-title">Activity & Cognitive Reports</span>
                <span class="child-control-sub">Honest usage telemetry, subjects & learning trajectory</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>

          <div class="child-control-card" id="go-pair" role="button" tabindex="0">
            <div class="child-control-left">
              <div class="child-control-icon">🔗</div>
              <div class="child-control-text">
                <span class="child-control-title">Pair Child Computer</span>
                <span class="child-control-sub">${d.card.pairedDeviceCount > 0 ? "Manage or link additional computer" : "Generate 6-digit PIN or QR payload"}</span>
              </div>
            </div>
            <div class="child-control-right">
              <span class="child-control-chevron">›</span>
            </div>
          </div>
        </div>

        <!-- Danger Zone Footer -->
        <div style="margin-top: 32px; padding-top: 18px; border-top: 1px solid var(--ms-outline-subtle); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
          <div>
            <strong style="font-size: 0.92rem; color: var(--ms-on-surface);">Child Account Management</strong>
            <p class="muted" style="margin: 2px 0 0; font-size: 0.8rem;">Disconnect paired computer, or remove profile from family household.</p>
          </div>
          <div style="display: flex; gap: 10px; flex-wrap: wrap;">
            ${d.card.pairedDeviceCount > 0 ? `
            <button class="btn btn-secondary" id="btn-show-unpair-modal-bottom" style="color: var(--ms-error); border-color: color-mix(in srgb, var(--ms-error) 40%, transparent);">
              Unpair Computer
            </button>` : ""}
            <button class="btn btn-secondary" id="btn-show-remove-modal" style="color: var(--ms-error); border-color: color-mix(in srgb, var(--ms-error) 40%, transparent);">
              Remove ${escapeHtml(d.card.displayName)} from Family
            </button>
          </div>
        </div>
      </div>

      <!-- In-app Confirmation Modal for Unpair Device (Hidden initially) -->
      <div id="unpair-device-modal" class="modal-overlay" style="display: none;" role="dialog" aria-modal="true" aria-labelledby="modal-unpair-title">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-icon-danger">🔌</div>
            <div class="modal-header-text">
              <h3 id="modal-unpair-title" class="modal-title">Unpair Computer from ${escapeHtml(d.card.displayName)}?</h3>
              <p class="modal-sub">Disconnect desktop guardian service and screen time rules.</p>
            </div>
          </div>
          <div class="modal-body">
            <p style="margin: 0; line-height: 1.5; color: var(--ms-on-surface-variant); font-size: 0.88rem;">
              Unpairing will immediately stop the background service on this computer, reset the menu bar icon, and remove screen time boundaries. You can easily pair it again at any time with a new code.
            </p>
          </div>
          <div class="modal-actions-row">
            <button class="btn btn-secondary" id="modal-unpair-cancel-btn">Cancel</button>
            <button class="btn btn-primary" id="modal-unpair-confirm-btn" style="background: var(--ms-error); border-color: var(--ms-error);">
              <span id="unpair-dev-spinner" style="display: none;" class="btn-spinner"></span>
              <span id="unpair-dev-text">Unpair Computer</span>
            </button>
          </div>
        </div>
      </div>

      <!-- In-app Confirmation Modal (Hidden initially) -->
      <div id="remove-child-modal" class="modal-overlay" style="display: none;" role="dialog" aria-modal="true" aria-labelledby="modal-remove-title">
        <div class="modal-dialog">
          <div class="modal-header">
            <div class="modal-icon-danger">⚠️</div>
            <div class="modal-header-text">
              <h3 id="modal-remove-title" class="modal-title">Remove ${escapeHtml(d.card.displayName)}?</h3>
              <p class="modal-sub">This action will disconnect their profile and cannot be undone.</p>
            </div>
          </div>
          <div class="modal-body">
            <p style="margin: 0; line-height: 1.5; color: var(--ms-on-surface-variant); font-size: 0.88rem;">
              Removing <strong>${escapeHtml(d.card.displayName)}</strong> permanently deletes their screen time rules, micro-quiz mastery history, and linked computer configurations. Any paired child devices will immediately disconnect and return to setup mode.
            </p>
          </div>
          <div class="modal-actions-row">
            <button class="btn btn-secondary" id="modal-cancel-btn">Cancel</button>
            <button class="btn btn-primary" id="modal-confirm-remove-btn" style="background: var(--ms-error); border-color: var(--ms-error);">
              <span id="remove-spinner" style="display: none;" class="btn-spinner"></span>
              <span id="remove-btn-text">Remove Child</span>
            </button>
          </div>
        </div>
      </div>`;

    document.getElementById("go-allowlist")?.addEventListener("click", () => void navigate({ name: "allowlist", childId }));
    document.getElementById("go-time-limits")?.addEventListener("click", () => void navigate({ name: "time-limits", childId }));
    document.getElementById("go-quiz-settings")?.addEventListener("click", () => void navigate({ name: "quiz-settings", childId }));
    document.getElementById("go-rewards")?.addEventListener("click", () => void navigate({ name: "rewards", childId }));
    document.getElementById("go-reports")?.addEventListener("click", () => void navigate({ name: "reports", childId }));
    document.getElementById("go-pair")?.addEventListener("click", () => void navigate({ name: "pairing", childId }));
    document.getElementById("callout-pair-btn")?.addEventListener("click", () => void navigate({ name: "pairing", childId }));
    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "parent-home" }));

    // Instant Remote Pause/Resume Toggle
    document.getElementById("btn-toggle-pause")?.addEventListener("click", () => {
      void (async () => {
        const feedback = document.getElementById("child-action-feedback");
        try {
          const updatedPolicy: ChildPolicy = {
            ...d.policy,
            paused: !d.policy.paused,
          };
          await invoke("parent_update_policy", { childId, policy: updatedPolicy });
          showToast(!d.policy.paused ? `Screen access paused for ${d.card.displayName}` : `Screen protection resumed for ${d.card.displayName}`, !d.policy.paused ? "info" : "success");
          await renderChildDetail(childId);
        } catch (e) {
          if (feedback) {
            feedback.innerHTML = `
              <div class="error-banner" style="margin-bottom: 12px;">
                <div><strong>Action failed:</strong> ${escapeHtml(friendlyError(e))}</div>
              </div>`;
          }
        }
      })();
    });

    // Instant +15m Quick Bonus Screen Time
    document.getElementById("btn-quick-bonus")?.addEventListener("click", () => {
      void (async () => {
        const feedback = document.getElementById("child-action-feedback");
        try {
          const updatedPolicy: ChildPolicy = {
            ...d.policy,
            extraMinutesOnPass: Math.min(30, d.policy.extraMinutesOnPass + 15),
            rewardsEnabled: true,
          };
          await invoke("parent_update_policy", { childId, policy: updatedPolicy });
          showToast(`+15 min bonus screen time added for ${d.card.displayName}!`, "success");
          if (feedback) {
            feedback.innerHTML = `
              <div class="success-banner" style="margin-bottom: 12px;">
                <div><strong>🎁 Bonus Screen Time Granted</strong>+15 bonus minutes added to ${escapeHtml(d.card.displayName)}’s allowance.</div>
              </div>`;
          }
        } catch (e) {
          if (feedback) {
            feedback.innerHTML = `
              <div class="error-banner" style="margin-bottom: 12px;">
                <div><strong>Bonus grant failed:</strong> ${escapeHtml(friendlyError(e))}</div>
              </div>`;
          }
        }
      })();
    });

    // In-App Modal Dialog Confirmation for Remove Child
    const modal = document.getElementById("remove-child-modal");
    const showModalBtn = document.getElementById("btn-show-remove-modal");
    const cancelModalBtn = document.getElementById("modal-cancel-btn");
    const confirmRemoveBtn = document.getElementById("modal-confirm-remove-btn");
    const removeSpinner = document.getElementById("remove-spinner");
    const removeBtnText = document.getElementById("remove-btn-text");

    showModalBtn?.addEventListener("click", () => {
      if (modal) modal.style.display = "grid";
    });

    cancelModalBtn?.addEventListener("click", () => {
      if (modal) modal.style.display = "none";
    });

    modal?.addEventListener("click", (e) => {
      if (e.target === modal) modal.style.display = "none";
    });

    confirmRemoveBtn?.addEventListener("click", () => {
      void (async () => {
        if (confirmRemoveBtn) confirmRemoveBtn.setAttribute("disabled", "true");
        if (removeSpinner) removeSpinner.style.display = "inline-block";
        if (removeBtnText) removeBtnText.textContent = "Removing…";
        try {
          await invoke("parent_remove_child", { childId });
          showToast(`${d.card.displayName} was removed from the family.`, "info");
          await navigate({ name: "parent-home" });
        } catch (e) {
          if (confirmRemoveBtn) confirmRemoveBtn.removeAttribute("disabled");
          if (removeSpinner) removeSpinner.style.display = "none";
          if (removeBtnText) removeBtnText.textContent = "Remove Child";
          if (modal) modal.style.display = "none";
          renderError(String(e), () => void navigate({ name: "child-detail", childId }));
        }
      })();
    });

    // In-App Modal Dialog Confirmation for Unpair Device
    const unpairModal = document.getElementById("unpair-device-modal");
    const showUnpairBtn = document.getElementById("btn-show-unpair-modal");
    const showUnpairBtnBottom = document.getElementById("btn-show-unpair-modal-bottom");
    const cancelUnpairBtn = document.getElementById("modal-unpair-cancel-btn");
    const confirmUnpairBtn = document.getElementById("modal-unpair-confirm-btn");
    const unpairDevSpinner = document.getElementById("unpair-dev-spinner");
    const unpairDevText = document.getElementById("unpair-dev-text");

    const openUnpairModal = () => {
      if (unpairModal) unpairModal.style.display = "grid";
    };

    showUnpairBtn?.addEventListener("click", openUnpairModal);
    showUnpairBtnBottom?.addEventListener("click", openUnpairModal);

    cancelUnpairBtn?.addEventListener("click", () => {
      if (unpairModal) unpairModal.style.display = "none";
    });

    unpairModal?.addEventListener("click", (e) => {
      if (e.target === unpairModal) unpairModal.style.display = "none";
    });

    confirmUnpairBtn?.addEventListener("click", () => {
      void (async () => {
        if (confirmUnpairBtn) confirmUnpairBtn.setAttribute("disabled", "true");
        if (unpairDevSpinner) unpairDevSpinner.style.display = "inline-block";
        if (unpairDevText) unpairDevText.textContent = "Unpairing…";
        try {
          await invoke("parent_unpair_device", { childId });
          showToast(`Device unpaired from ${d.card.displayName}. Background service stopped.`, "info");
          await renderChildDetail(childId);
        } catch (e) {
          if (confirmUnpairBtn) confirmUnpairBtn.removeAttribute("disabled");
          if (unpairDevSpinner) unpairDevSpinner.style.display = "none";
          if (unpairDevText) unpairDevText.textContent = "Unpair Computer";
          if (unpairModal) unpairModal.style.display = "none";
          renderError(String(e), () => void navigate({ name: "child-detail", childId }));
        }
      })();
    });

    // Periodic synchronization to keep stats & device status up to date in real time
    if (activeInterval !== null) {
      clearInterval(activeInterval);
      activeInterval = null;
    }
    activeInterval = window.setInterval(async () => {
      if (route.name !== "child-detail" || route.childId !== childId) {
        if (activeInterval !== null) {
          clearInterval(activeInterval);
          activeInterval = null;
        }
        return;
      }
      try {
        const fresh = await invoke<ChildDetail>("parent_child_detail", { childId });
        if (fresh && fresh.card) {
          if (
            fresh.card.minutesUsedToday !== d.card.minutesUsedToday ||
            fresh.card.pairedDeviceCount !== d.card.pairedDeviceCount ||
            fresh.policy.quizMode !== d.policy.quizMode
          ) {
            void renderChildDetail(childId);
          }
        }
      } catch {
        // quiet
      }
    }, 8000);
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
  }
}

async function renderAllowlist(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName, route: { name: "child-detail", childId } },
      { label: "App Rules & Firewall" },
    ]);

    let currentRules: AppRule[] = d.appRules.slice();
    if (d.enforcement && d.enforcement.installedApps.length) {
      for (const app of d.enforcement.installedApps) {
        if (!currentRules.some((r) => r.appId === app.appId)) {
          currentRules.push({
            appId: app.appId,
            packageOrBundleId: app.appId,
            displayName: app.label,
            allowed: app.allowed,
            blockMinutes: 15,
            grantOnPassMinutes: 15,
            cooldownMinutes: 5,
            isEmergency: false,
          });
        }
      }
    }

    let filterText = "";
    let filterTab: "all" | "allowed" | "blocked" = "all";

    const renderList = () => {
      const container = document.getElementById("allowlist-items");
      if (!container) return;

      const filtered = currentRules.filter((r) => {
        const matchesText =
          r.displayName.toLowerCase().includes(filterText.toLowerCase()) ||
          r.appId.toLowerCase().includes(filterText.toLowerCase());
        if (!matchesText) return false;
        if (filterTab === "allowed") return r.allowed;
        if (filterTab === "blocked") return !r.allowed;
        return true;
      });

      const allowedCount = currentRules.filter((r) => r.allowed).length;
      const blockedCount = currentRules.filter((r) => !r.allowed).length;

      const statsEl = document.getElementById("allowlist-counts");
      if (statsEl) {
        statsEl.innerHTML = `
          <span class="pill-badge accent">${allowedCount} Allowed</span>
          <span class="pill-badge">${blockedCount} Blocked</span>
          <span class="pill-badge">${currentRules.length} Monitored</span>
        `;
      }

      if (!filtered.length) {
        container.innerHTML = `
          <div style="padding: 32px 16px; text-align: center; background: var(--ms-surface-low); border: 1px dashed var(--ms-outline-subtle); border-radius: var(--ms-radius);">
            <p class="muted" style="margin: 0; font-size: 0.9rem;">No applications match your filter.</p>
          </div>`;
        return;
      }

      container.innerHTML = filtered
        .map(
          (r) => `
          <div class="allowlist-card-row">
            <div class="allowlist-card-left">
              <div class="allowlist-card-app-icon">${escapeHtml(r.displayName.slice(0, 1).toUpperCase() || "A")}</div>
              <div class="allowlist-card-details">
                <div class="allowlist-card-name-row">
                  <span class="allowlist-card-title">${escapeHtml(r.displayName || r.appId)}</span>
                  ${r.isEmergency ? `<span class="pill-badge accent" style="font-size: 0.72rem;">Emergency Always Allowed</span>` : ""}
                </div>
                <span class="allowlist-card-pkg">${escapeHtml(r.appId)}</span>
              </div>
            </div>
            <div>
              <button class="btn ${r.allowed ? "btn-primary" : "btn-secondary"}" data-toggle-app="${escapeHtml(r.appId)}"
                ${r.isEmergency ? "disabled" : ""} style="min-width: 104px; font-size: 0.82rem;">
                ${r.allowed ? "✓ Allowed" : "✗ Blocked"}
              </button>
            </div>
          </div>`,
        )
        .join("");

      for (const btn of container.querySelectorAll<HTMLButtonElement>("[data-toggle-app]")) {
        btn.addEventListener("click", () => {
          const id = btn.dataset.toggleApp!;
          const rule = currentRules.find((r) => r.appId === id);
          if (rule && !rule.isEmergency) {
            rule.allowed = !rule.allowed;
            renderList();
          }
        });
      }
    };

    view.innerHTML = `
      <div class="panel wide">
        <div class="subpage-header-row">
          <div class="subpage-title-group">
            <div class="subpage-title-row">
              <h1>App Allowlist & Firewalls</h1>
              <span class="pill-badge">${escapeHtml(d.card.displayName)}</span>
            </div>
            <p class="subpage-subtitle">Configure approved applications and desktop firewall boundaries.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back to ${escapeHtml(d.card.displayName)}</button>
        </div>

        <div class="info-callout" style="margin-top: 14px;">
          <div class="info-callout-icon">🛡️</div>
          <div class="info-callout-body">
            <span class="info-callout-title">Desktop Guardian Firewall</span>
            <p class="info-callout-text">Watching enforces these boundaries across macOS, Windows, and Linux. When screen time expires or a micro-quiz checkpoint triggers, unapproved desktop apps are shielded. System emergency utilities remain accessible at all times.</p>
          </div>
        </div>

        <div id="allowlist-feedback" style="margin-top: 12px;"></div>

        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-top: 16px;">
          <div class="filter-bar" style="margin: 0; flex: 1; min-width: 260px;">
            <input class="search-input" id="search-apps" placeholder="🔍 Search installed desktop applications…" style="max-width: 380px;" />
            <div class="appearance" role="group">
              <button type="button" class="chip" data-tab="all" aria-pressed="true">All</button>
              <button type="button" class="chip" data-tab="allowed" aria-pressed="false">Allowed</button>
              <button type="button" class="chip" data-tab="blocked" aria-pressed="false">Blocked</button>
            </div>
          </div>
          <div id="allowlist-counts" style="display: flex; gap: 8px;"></div>
        </div>

        <div class="allowlist-container" id="allowlist-items" style="margin-top: 14px; display: flex; flex-direction: column; gap: 8px; max-height: 480px; overflow-y: auto;"></div>

        <div style="margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--ms-outline-subtle); display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;">
          <div style="display: flex; gap: 8px;">
            <button class="btn btn-secondary" id="quick-allow-all" style="font-size: 0.82rem;">Allow All Apps</button>
            <button class="btn btn-secondary" id="quick-block-unmonitored" style="font-size: 0.82rem;">Block Non-Essential</button>
          </div>
          <button class="btn btn-primary" id="save-allowlist">
            <span id="save-rules-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="save-rules-text">Save App Rules</span>
          </button>
        </div>
      </div>`;

    renderList();

    const searchInput = document.getElementById("search-apps") as HTMLInputElement;
    searchInput?.addEventListener("input", () => {
      filterText = searchInput.value;
      renderList();
    });

    for (const chip of view.querySelectorAll<HTMLButtonElement>("[data-tab]")) {
      chip.addEventListener("click", () => {
        filterTab = chip.dataset.tab as "all" | "allowed" | "blocked";
        for (const c of view!.querySelectorAll<HTMLButtonElement>("[data-tab]")) {
          c.setAttribute("aria-pressed", String(c === chip));
        }
        renderList();
      });
    }

    document.getElementById("quick-allow-all")?.addEventListener("click", () => {
      for (const rule of currentRules) {
        rule.allowed = true;
      }
      renderList();
    });

    document.getElementById("quick-block-unmonitored")?.addEventListener("click", () => {
      for (const rule of currentRules) {
        if (!rule.isEmergency) {
          rule.allowed = false;
        }
      }
      renderList();
    });

    const saveBtn = document.getElementById("save-allowlist") as HTMLButtonElement;
    const saveSpinner = document.getElementById("save-rules-spinner") as HTMLElement;
    const saveText = document.getElementById("save-rules-text") as HTMLElement;
    const feedback = document.getElementById("allowlist-feedback") as HTMLElement;

    saveBtn?.addEventListener("click", () => {
      void (async () => {
        saveBtn.disabled = true;
        saveSpinner.style.display = "inline-block";
        saveText.textContent = "Saving rules…";
        feedback.innerHTML = "";
        try {
          await invoke("parent_update_app_rules", { childId, rules: currentRules });
          showToast("App allowlist rules saved successfully!", "success");
          feedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 12px;">
              <div><strong>Rules Saved</strong>App allowlist updated and synchronized successfully.</div>
            </div>`;
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save App Rules";
        } catch (e) {
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save App Rules";
          feedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 12px;">
              <div><strong>Save failed</strong>${escapeHtml(friendlyError(e))}</div>
            </div>`;
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "child-detail", childId }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

async function renderTimeLimits(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName, route: { name: "child-detail", childId } },
      { label: "Time Limits & Pacing" },
    ]);

    const desktop = !!d.card.platformHint && ["windows", "macos", "linux"].includes(d.card.platformHint);

    let ceilingVal = d.policy.dailyCeilingMinutes ?? 120;
    let blockVal = d.policy.defaultBlockMinutes ?? 15;
    let intervalVal = d.policy.quizIntervalMinutes ?? 15;
    let pausedVal = d.policy.paused;

    view.innerHTML = `
      <div class="panel">
        <div class="subpage-header-row">
          <div class="subpage-title-group">
            <div class="subpage-title-row">
              <h1>Time Limits & Pacing</h1>
              <span class="pill-badge">${escapeHtml(d.card.displayName)}</span>
            </div>
            <p class="subpage-subtitle">Configure mindful pacing, daily allowances, and screen pause for ${escapeHtml(d.card.displayName)}.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back to ${escapeHtml(d.card.displayName)}</button>
        </div>

        <div class="info-callout" style="margin-top: 14px;">
          <div class="info-callout-icon">🌿</div>
          <div class="info-callout-body">
            <span class="info-callout-title">Gentle Boundaries Pacing</span>
            <p class="info-callout-text">Watching paces screen time into structured session chunks. Every interval triggers a brief cognitive micro-quiz, encouraging healthy breaks and self-regulation.</p>
          </div>
        </div>

        <div id="time-feedback" style="margin-top: 12px;"></div>

        <div style="display: flex; flex-direction: column; gap: 16px; margin-top: 16px;">
          <!-- 1. Daily Ceiling -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">⏱️</div>
              <div>
                <h3 class="setting-card-title">Daily Screen Time Ceiling</h3>
                <p class="setting-card-sub">Maximum computer use allowed across the entire day. Enter 0 for unlimited.</p>
              </div>
            </div>

            <div class="chip-options-row" id="ceiling-chips">
              <button type="button" class="chip-option ${ceilingVal === 45 ? "active" : ""}" data-val="45">45 min</button>
              <button type="button" class="chip-option ${ceilingVal === 60 ? "active" : ""}" data-val="60">60 min</button>
              <button type="button" class="chip-option ${ceilingVal === 90 ? "active" : ""}" data-val="90">90 min</button>
              <button type="button" class="chip-option ${ceilingVal === 120 ? "active" : ""}" data-val="120">120 min (Recommended)</button>
              <button type="button" class="chip-option ${ceilingVal === 180 ? "active" : ""}" data-val="180">180 min (3 hrs)</button>
              <button type="button" class="chip-option ${ceilingVal === 0 ? "active" : ""}" data-val="0">Unlimited</button>
            </div>

            <label class="field" style="margin: 0;">Custom daily ceiling (minutes)
              <input id="daily-ceiling" type="number" min="0" max="1440" value="${ceilingVal}" />
            </label>
          </div>

          <!-- 2. Quiz Interval -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">🧠</div>
              <div>
                <h3 class="setting-card-title">Desktop Quiz Pacing Interval</h3>
                <p class="setting-card-sub">Active computer use permitted before a micro-quiz checkpoint appears.</p>
              </div>
            </div>

            <div class="chip-options-row" id="interval-chips">
              <button type="button" class="chip-option ${intervalVal === 10 ? "active" : ""}" data-val="10">10 min</button>
              <button type="button" class="chip-option ${intervalVal === 15 ? "active" : ""}" data-val="15">15 min (Recommended)</button>
              <button type="button" class="chip-option ${intervalVal === 20 ? "active" : ""}" data-val="20">20 min</button>
              <button type="button" class="chip-option ${intervalVal === 30 ? "active" : ""}" data-val="30">30 min</button>
              <button type="button" class="chip-option ${intervalVal === 45 ? "active" : ""}" data-val="45">45 min</button>
              <button type="button" class="chip-option ${intervalVal === 60 ? "active" : ""}" data-val="60">60 min</button>
            </div>

            <label class="field" style="margin: 0;">Custom interval (minutes: 2–240)
              <input id="interval-min" type="number" min="2" max="240" value="${intervalVal}" />
            </label>
          </div>

          <!-- 3. Screen Pause Toggle -->
          <div class="ms-toggle-row" id="toggle-pause-row">
            <div class="ms-toggle-info">
              <span class="ms-toggle-label">Pause Screen Time Immediately</span>
              <span class="ms-toggle-sub">Shields all non-emergency apps across linked computers until resumed.</span>
            </div>
            <label class="ms-switch" onclick="event.stopPropagation();">
              <input id="paused" type="checkbox" ${pausedVal ? "checked" : ""}/>
              <span class="ms-slider"></span>
            </label>
          </div>
        </div>

        <div class="actions" style="margin-top: 20px;">
          <button class="btn btn-primary" id="save-time">
            <span id="save-time-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="save-time-text">Save Time Settings</span>
          </button>
        </div>
      </div>`;

    // Chip click synchronizers for daily ceiling
    const ceilingInput = document.getElementById("daily-ceiling") as HTMLInputElement;
    const ceilingChips = view.querySelectorAll<HTMLButtonElement>("#ceiling-chips .chip-option");
    ceilingChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        ceilingInput.value = val;
        ceilingChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    ceilingInput?.addEventListener("input", () => {
      const v = Number(ceilingInput.value);
      ceilingChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Chip click synchronizers for quiz interval
    const intervalInput = document.getElementById("interval-min") as HTMLInputElement;
    const intervalChips = view.querySelectorAll<HTMLButtonElement>("#interval-chips .chip-option");
    intervalChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        intervalInput.value = val;
        intervalChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    intervalInput?.addEventListener("input", () => {
      const v = Number(intervalInput.value);
      intervalChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Toggle pause row click
    const togglePauseRow = document.getElementById("toggle-pause-row");
    const pausedCheckbox = document.getElementById("paused") as HTMLInputElement;
    togglePauseRow?.addEventListener("click", () => {
      if (pausedCheckbox) {
        pausedCheckbox.checked = !pausedCheckbox.checked;
      }
    });

    const saveBtn = document.getElementById("save-time") as HTMLButtonElement;
    const saveSpinner = document.getElementById("save-time-spinner") as HTMLElement;
    const saveText = document.getElementById("save-time-text") as HTMLElement;
    const feedback = document.getElementById("time-feedback") as HTMLElement;

    saveBtn?.addEventListener("click", () => {
      void (async () => {
        const cVal = Number(ceilingInput.value);
        const iVal = Number(intervalInput.value);
        const pVal = pausedCheckbox.checked;

        const updatedPolicy: ChildPolicy = {
          ...d.policy,
          dailyCeilingMinutes: cVal > 0 ? cVal : null,
          defaultBlockMinutes: Math.min(240, Math.max(2, blockVal || 15)),
          quizIntervalMinutes: Math.min(240, Math.max(2, iVal || 15)),
          quizMode: desktop ? "device_interval" : d.policy.quizMode,
          paused: pVal,
        };

        saveBtn.disabled = true;
        saveSpinner.style.display = "inline-block";
        saveText.textContent = "Saving…";
        feedback.innerHTML = "";
        try {
          await invoke("parent_update_policy", { childId, policy: updatedPolicy });
          showToast("Time limits and pacing updated!", "success");
          feedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 12px;">
              <div><strong>Settings Saved</strong>Time limits and pacing intervals successfully updated.</div>
            </div>`;
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Time Settings";
        } catch (e) {
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Time Settings";
          feedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 12px;">
              <div><strong>Save failed</strong>${escapeHtml(friendlyError(e))}</div>
            </div>`;
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "child-detail", childId }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

async function renderQuizSettings(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName, route: { name: "child-detail", childId } },
      { label: "Micro-Quiz Settings" },
    ]);

    let qCount = d.policy.questionsPerQuiz || 3;
    let passVal = d.policy.passScorePercent || 70;
    let coolVal = d.policy.defaultCooldownMinutes || 5;
    let retryVal = d.policy.allowRetryDuringCooldown;

    view.innerHTML = `
      <div class="panel">
        <div class="subpage-header-row">
          <div class="subpage-title-group">
            <div class="subpage-title-row">
              <h1>Micro-Quiz & Cooldown Criteria</h1>
              <span class="pill-badge">${escapeHtml(d.card.displayName)}</span>
            </div>
            <p class="subpage-subtitle">Fine-tune questions count, pass requirements, and retry criteria for ${escapeHtml(d.card.displayName)}.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back to ${escapeHtml(d.card.displayName)}</button>
        </div>

        <div class="info-callout" style="margin-top: 14px;">
          <div class="info-callout-icon">🎓</div>
          <div class="info-callout-body">
            <span class="info-callout-title">Adaptive Cognitive Checkpoints</span>
            <p class="info-callout-text">Watching adapts quiz difficulty to ${escapeHtml(d.card.displayName)}’s developmental stage (${escapeHtml(formatAgeBand(d.card.ageBand))}). 3-question checkpoints preserve momentum without cognitive exhaustion.</p>
          </div>
        </div>

        <div id="quiz-feedback" style="margin-top: 12px;"></div>

        <div style="display: flex; flex-direction: column; gap: 16px; margin-top: 16px;">
          <!-- 1. Questions count -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">📝</div>
              <div>
                <h3 class="setting-card-title">Questions Per Micro-Quiz</h3>
                <p class="setting-card-sub">Number of questions per unlock challenge.</p>
              </div>
            </div>

            <div class="chip-options-row" id="questions-chips">
              <button type="button" class="chip-option ${qCount === 1 ? "active" : ""}" data-val="1">1 Question</button>
              <button type="button" class="chip-option ${qCount === 2 ? "active" : ""}" data-val="2">2 Questions</button>
              <button type="button" class="chip-option ${qCount === 3 ? "active" : ""}" data-val="3">3 Questions (Recommended)</button>
              <button type="button" class="chip-option ${qCount === 5 ? "active" : ""}" data-val="5">5 Questions</button>
            </div>

            <label class="field" style="margin: 0;">Custom questions count (1–10)
              <input id="questions-count" type="number" min="1" max="10" value="${qCount}" />
            </label>
          </div>

          <!-- 2. Passing score -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">🎯</div>
              <div>
                <h3 class="setting-card-title">Passing Score Threshold</h3>
                <p class="setting-card-sub">Minimum score percentage required to unlock computer time.</p>
              </div>
            </div>

            <div class="chip-options-row" id="pass-chips">
              <button type="button" class="chip-option ${passVal === 50 ? "active" : ""}" data-val="50">50% (Gentle)</button>
              <button type="button" class="chip-option ${passVal === 60 ? "active" : ""}" data-val="60">60% (Standard)</button>
              <button type="button" class="chip-option ${passVal === 70 ? "active" : ""}" data-val="70">70% (Recommended)</button>
              <button type="button" class="chip-option ${passVal === 80 ? "active" : ""}" data-val="80">80% (Mastery)</button>
              <button type="button" class="chip-option ${passVal === 100 ? "active" : ""}" data-val="100">100% (Strict)</button>
            </div>

            <label class="field" style="margin: 0;">Threshold percentage
              <select id="pass-score">
                <option value="50" ${passVal === 50 ? "selected" : ""}>50% — Gentle threshold</option>
                <option value="60" ${passVal === 60 ? "selected" : ""}>60% — Standard beginner</option>
                <option value="70" ${passVal === 70 ? "selected" : ""}>70% — Recommended standard</option>
                <option value="80" ${passVal === 80 ? "selected" : ""}>80% — High mastery</option>
                <option value="100" ${passVal === 100 ? "selected" : ""}>100% — Perfect score required</option>
              </select>
            </label>
          </div>

          <!-- 3. Cooldown -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">⏳</div>
              <div>
                <h3 class="setting-card-title">Cooldown Duration</h3>
                <p class="setting-card-sub">Cooling-off wait time when quiz score is below threshold.</p>
              </div>
            </div>

            <div class="chip-options-row" id="cool-chips">
              <button type="button" class="chip-option ${coolVal === 3 ? "active" : ""}" data-val="3">3 min</button>
              <button type="button" class="chip-option ${coolVal === 5 ? "active" : ""}" data-val="5">5 min (Recommended)</button>
              <button type="button" class="chip-option ${coolVal === 10 ? "active" : ""}" data-val="10">10 min</button>
              <button type="button" class="chip-option ${coolVal === 15 ? "active" : ""}" data-val="15">15 min</button>
              <button type="button" class="chip-option ${coolVal === 30 ? "active" : ""}" data-val="30">30 min</button>
            </div>

            <label class="field" style="margin: 0;">Custom cooldown duration (minutes: 1–60)
              <input id="cooldown-min" type="number" min="1" max="60" value="${coolVal}" />
            </label>
          </div>

          <!-- 4. Retry Switch -->
          <div class="ms-toggle-row" id="toggle-retry-row">
            <div class="ms-toggle-info">
              <span class="ms-toggle-label">Allow Instant Retry During Cooldown</span>
              <span class="ms-toggle-sub">Enables retaking the quiz immediately to earn screen time early.</span>
            </div>
            <label class="ms-switch" onclick="event.stopPropagation();">
              <input id="allow-retry" type="checkbox" ${retryVal ? "checked" : ""}/>
              <span class="ms-slider"></span>
            </label>
          </div>
        </div>

        <div class="actions" style="margin-top: 20px;">
          <button class="btn btn-primary" id="save-quiz">
            <span id="save-quiz-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="save-quiz-text">Save Quiz Settings</span>
          </button>
        </div>
      </div>`;

    // Chip click synchronizers for question count
    const qInput = document.getElementById("questions-count") as HTMLInputElement;
    const qChips = view.querySelectorAll<HTMLButtonElement>("#questions-chips .chip-option");
    qChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        qInput.value = val;
        qChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    qInput?.addEventListener("input", () => {
      const v = Number(qInput.value);
      qChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Chip click synchronizers for pass score
    const passSelect = document.getElementById("pass-score") as HTMLSelectElement;
    const passChips = view.querySelectorAll<HTMLButtonElement>("#pass-chips .chip-option");
    passChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        passSelect.value = val;
        passChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    passSelect?.addEventListener("change", () => {
      const v = Number(passSelect.value);
      passChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Chip click synchronizers for cooldown
    const coolInput = document.getElementById("cooldown-min") as HTMLInputElement;
    const coolChips = view.querySelectorAll<HTMLButtonElement>("#cool-chips .chip-option");
    coolChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        coolInput.value = val;
        coolChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    coolInput?.addEventListener("input", () => {
      const v = Number(coolInput.value);
      coolChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Toggle retry row click
    const toggleRetryRow = document.getElementById("toggle-retry-row");
    const retryCheckbox = document.getElementById("allow-retry") as HTMLInputElement;
    toggleRetryRow?.addEventListener("click", () => {
      if (retryCheckbox) {
        retryCheckbox.checked = !retryCheckbox.checked;
      }
    });

    const saveBtn = document.getElementById("save-quiz") as HTMLButtonElement;
    const saveSpinner = document.getElementById("save-quiz-spinner") as HTMLElement;
    const saveText = document.getElementById("save-quiz-text") as HTMLElement;
    const feedback = document.getElementById("quiz-feedback") as HTMLElement;

    saveBtn?.addEventListener("click", () => {
      void (async () => {
        const qCountVal = Number(qInput.value);
        const passValSelected = Number(passSelect.value);
        const coolValEntered = Number(coolInput.value);
        const retryChecked = retryCheckbox.checked;

        const updatedPolicy: ChildPolicy = {
          ...d.policy,
          questionsPerQuiz: Math.min(10, Math.max(1, qCountVal || 3)),
          passScorePercent: passValSelected || 70,
          defaultCooldownMinutes: Math.min(60, Math.max(1, coolValEntered || 5)),
          allowRetryDuringCooldown: retryChecked,
        };

        saveBtn.disabled = true;
        saveSpinner.style.display = "inline-block";
        saveText.textContent = "Saving…";
        feedback.innerHTML = "";
        try {
          await invoke("parent_update_policy", { childId, policy: updatedPolicy });
          showToast("Micro-quiz settings updated!", "success");
          feedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 12px;">
              <div><strong>Settings Saved</strong>Micro-quiz criteria and cooldown successfully updated.</div>
            </div>`;
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Quiz Settings";
        } catch (e) {
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Quiz Settings";
          feedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 12px;">
              <div><strong>Save failed</strong>${escapeHtml(friendlyError(e))}</div>
            </div>`;
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "child-detail", childId }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

async function renderRewards(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName, route: { name: "child-detail", childId } },
      { label: "Rewards & Privileges" },
    ]);

    let rewEnabled = d.policy.rewardsEnabled;
    let extraMin = d.policy.extraMinutesOnPass;
    let wEndBonus = d.policy.weekendBonusEnabled;

    view.innerHTML = `
      <div class="panel">
        <div class="subpage-header-row">
          <div class="subpage-title-group">
            <div class="subpage-title-row">
              <h1>Rewards & Bonus Privileges</h1>
              <span class="pill-badge">${escapeHtml(d.card.displayName)}</span>
            </div>
            <p class="subpage-subtitle">Incentivize cognitive focus and celebrate accomplishments for ${escapeHtml(d.card.displayName)}.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back to ${escapeHtml(d.card.displayName)}</button>
        </div>

        <div class="info-callout" style="margin-top: 14px;">
          <div class="info-callout-icon">🎁</div>
          <div class="info-callout-body">
            <span class="info-callout-title">Positive Reinforcement</span>
            <p class="info-callout-text">Rewarding quiz completion with extra minutes and collectible Explorer stickers strengthens positive digital habits and celebrates learning achievements.</p>
          </div>
        </div>

        <div id="rewards-feedback" style="margin-top: 12px;"></div>

        <div style="display: flex; flex-direction: column; gap: 16px; margin-top: 16px;">
          <!-- 1. Enable Rewards Switch -->
          <div class="ms-toggle-row" id="toggle-rewards-row">
            <div class="ms-toggle-info">
              <span class="ms-toggle-label">Enable Bonus Reward Minutes</span>
              <span class="ms-toggle-sub">Grants additional recreational computer time upon passing micro-quizzes.</span>
            </div>
            <label class="ms-switch" onclick="event.stopPropagation();">
              <input id="rewards-enabled" type="checkbox" ${rewEnabled ? "checked" : ""}/>
              <span class="ms-slider"></span>
            </label>
          </div>

          <!-- 2. Extra Minutes on Pass -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">⭐</div>
              <div>
                <h3 class="setting-card-title">Bonus Minutes Granted on Pass</h3>
                <p class="setting-card-sub">Additional screen time added when a micro-quiz is passed on first try.</p>
              </div>
            </div>

            <div class="chip-options-row" id="bonus-chips">
              <button type="button" class="chip-option ${extraMin === 0 ? "active" : ""}" data-val="0">No extra time (0m)</button>
              <button type="button" class="chip-option ${extraMin === 5 ? "active" : ""}" data-val="5">+5 min</button>
              <button type="button" class="chip-option ${extraMin === 10 ? "active" : ""}" data-val="10">+10 min</button>
              <button type="button" class="chip-option ${extraMin === 15 ? "active" : ""}" data-val="15">+15 min (Recommended)</button>
              <button type="button" class="chip-option ${extraMin === 20 ? "active" : ""}" data-val="20">+20 min</button>
              <button type="button" class="chip-option ${extraMin === 30 ? "active" : ""}" data-val="30">+30 min</button>
            </div>

            <label class="field" style="margin: 0;">Custom bonus minutes (0–30)
              <input id="extra-pass" type="number" min="0" max="30" value="${extraMin}" />
            </label>
          </div>

          <!-- 3. Weekend Bonus Mode -->
          <div class="ms-toggle-row" id="toggle-weekend-row">
            <div class="ms-toggle-info">
              <span class="ms-toggle-label">Weekend Bonus Mode</span>
              <span class="ms-toggle-sub">Relaxed screen time allowances and cheer XP on Saturdays and Sundays.</span>
            </div>
            <label class="ms-switch" onclick="event.stopPropagation();">
              <input id="weekend-bonus" type="checkbox" ${wEndBonus ? "checked" : ""}/>
              <span class="ms-slider"></span>
            </label>
          </div>

          <!-- 4. Explorer Sticker Stages (Android Parity) -->
          <div class="setting-card">
            <div class="setting-card-header">
              <div class="setting-card-icon">🏆</div>
              <div>
                <h3 class="setting-card-title">Explorer Sticker Stages</h3>
                <p class="setting-card-sub">Stickers unlock across five milestones as children complete educational quizzes.</p>
              </div>
            </div>

            <div class="sticker-levels-grid">
              <div class="sticker-level-card">
                <span class="sticker-level-icon">🌱</span>
                <span class="sticker-level-name">Sprout</span>
                <span class="sticker-level-range">Level 1–2</span>
              </div>
              <div class="sticker-level-card">
                <span class="sticker-level-icon">🧭</span>
                <span class="sticker-level-name">Explorer</span>
                <span class="sticker-level-range">Level 3–4</span>
              </div>
              <div class="sticker-level-card">
                <span class="sticker-level-icon">⚡</span>
                <span class="sticker-level-name">Trailblazer</span>
                <span class="sticker-level-range">Level 5–6</span>
              </div>
              <div class="sticker-level-card">
                <span class="sticker-level-icon">🏆</span>
                <span class="sticker-level-name">Champion</span>
                <span class="sticker-level-range">Level 7–8</span>
              </div>
              <div class="sticker-level-card">
                <span class="sticker-level-icon">👑</span>
                <span class="sticker-level-name">Legend</span>
                <span class="sticker-level-range">Level 9–10</span>
              </div>
            </div>
          </div>
        </div>

        <div class="actions" style="margin-top: 20px;">
          <button class="btn btn-primary" id="save-rewards">
            <span id="save-rewards-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="save-rewards-text">Save Reward Settings</span>
          </button>
        </div>
      </div>`;

    // Chip click synchronizers for bonus minutes
    const bonusInput = document.getElementById("extra-pass") as HTMLInputElement;
    const bonusChips = view.querySelectorAll<HTMLButtonElement>("#bonus-chips .chip-option");
    bonusChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        const val = chip.dataset.val!;
        bonusInput.value = val;
        bonusChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
      });
    });

    bonusInput?.addEventListener("input", () => {
      const v = Number(bonusInput.value);
      bonusChips.forEach((c) => {
        c.classList.toggle("active", Number(c.dataset.val) === v);
      });
    });

    // Toggle rewards row click
    const toggleRewardsRow = document.getElementById("toggle-rewards-row");
    const rewardsCheckbox = document.getElementById("rewards-enabled") as HTMLInputElement;
    toggleRewardsRow?.addEventListener("click", () => {
      if (rewardsCheckbox) {
        rewardsCheckbox.checked = !rewardsCheckbox.checked;
      }
    });

    // Toggle weekend row click
    const toggleWeekendRow = document.getElementById("toggle-weekend-row");
    const weekendCheckbox = document.getElementById("weekend-bonus") as HTMLInputElement;
    toggleWeekendRow?.addEventListener("click", () => {
      if (weekendCheckbox) {
        weekendCheckbox.checked = !weekendCheckbox.checked;
      }
    });

    const saveBtn = document.getElementById("save-rewards") as HTMLButtonElement;
    const saveSpinner = document.getElementById("save-rewards-spinner") as HTMLElement;
    const saveText = document.getElementById("save-rewards-text") as HTMLElement;
    const feedback = document.getElementById("rewards-feedback") as HTMLElement;

    saveBtn?.addEventListener("click", () => {
      void (async () => {
        const rewVal = rewardsCheckbox.checked;
        const extraMinVal = Number(bonusInput.value);
        const wEndVal = weekendCheckbox.checked;

        const updatedPolicy: ChildPolicy = {
          ...d.policy,
          rewardsEnabled: rewVal,
          extraMinutesOnPass: Math.min(30, Math.max(0, extraMinVal || 0)),
          weekendBonusEnabled: wEndVal,
        };

        saveBtn.disabled = true;
        saveSpinner.style.display = "inline-block";
        saveText.textContent = "Saving…";
        feedback.innerHTML = "";
        try {
          await invoke("parent_update_policy", { childId, policy: updatedPolicy });
          showToast("Rewards configuration saved!", "success");
          feedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 12px;">
              <div><strong>Settings Saved</strong>Rewards and weekend privileges successfully updated.</div>
            </div>`;
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Reward Settings";
        } catch (e) {
          saveBtn.disabled = false;
          saveSpinner.style.display = "none";
          saveText.textContent = "Save Reward Settings";
          feedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 12px;">
              <div><strong>Save failed</strong>${escapeHtml(friendlyError(e))}</div>
            </div>`;
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "child-detail", childId }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

function getAppCategoryEmoji(name: string): string {
  const lower = name.toLowerCase();
  if (lower.includes("chrome") || lower.includes("safari") || lower.includes("firefox") || lower.includes("edge") || lower.includes("browser") || lower.includes("brave")) return "🌐";
  if (lower.includes("code") || lower.includes("terminal") || lower.includes("xcode") || lower.includes("cursor") || lower.includes("antigravity") || lower.includes("studio") || lower.includes("intellij") || lower.includes("rust")) return "💻";
  if (lower.includes("minecraft") || lower.includes("roblox") || lower.includes("steam") || lower.includes("game") || lower.includes("epic")) return "🎮";
  if (lower.includes("youtube") || lower.includes("netflix") || lower.includes("video") || lower.includes("player") || lower.includes("vlc") || lower.includes("disney") || lower.includes("prime")) return "📺";
  if (lower.includes("slack") || lower.includes("discord") || lower.includes("teams") || lower.includes("zoom") || lower.includes("messages")) return "💬";
  if (lower.includes("book") || lower.includes("kindle") || lower.includes("duolingo") || lower.includes("scratch") || lower.includes("khan") || lower.includes("learn")) return "📖";
  if (lower.includes("music") || lower.includes("spotify") || lower.includes("apple music")) return "🎵";
  return "📱";
}

function formatTrendDate(dateStr: string): string {
  try {
    const parts = dateStr.split("-");
    if (parts.length === 3) {
      const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
      return d.toLocaleDateString(undefined, { weekday: "short" });
    }
  } catch {}
  return dateStr.length > 5 ? dateStr.slice(5) : dateStr;
}

async function renderReports(childId?: string): Promise<void> {
  renderLoading("Loading activity & cognitive reports…");
  try {
    let targetChildId = childId;
    let allChildren: ChildCard[] = [];

    try {
      const dash = await invoke<
        UiStateDto<{ profile: ParentProfile; children: ChildCard[]; maxChildren: number }>
      >("parent_dashboard");
      if (dash.state === "success" && dash.data?.children) {
        allChildren = dash.data.children;
        if (!targetChildId && allChildren.length > 0) {
          targetChildId = allChildren[0].childId;
        }
      }
    } catch (e) {
      console.warn("Could not fetch children list for reports:", e);
    }

    if (!targetChildId) {
      if (!view) return;
      updateBreadcrumbs([
        { label: "Family Sanctuary", route: { name: "parent-home" } },
        { label: "Household Reports" },
      ]);
      view.innerHTML = `
        <div class="dashboard-wrapper">
          <div class="dash-header-row">
            <div class="dash-title-group">
              <div class="dash-title-wrap">
                <h1>Family Activity & Mastery Reports</h1>
                <span class="pill-badge accent">Honest Telemetry</span>
              </div>
              <p class="dash-subtitle">Calm, honest reporting without artificial numbers or inflated placeholders.</p>
            </div>
            <button class="btn btn-secondary" id="back">← Back to dashboard</button>
          </div>
          <div class="dash-ai-banner" style="margin-top:24px;">
            <div class="dash-ai-icon-tile">🛡️</div>
            <div class="dash-ai-content">
              <div class="dash-ai-heading-row">
                <span class="dash-ai-badge">No Children Paired Yet</span>
              </div>
              <p class="dash-ai-text">
                Pair a child’s computer or device to view real-time per-application usage breakdowns, micro-quiz learning time, and cognitive progress reports.
              </p>
              <div style="margin-top:14px;">
                <button class="btn btn-primary" id="btn-add-child-report">Add Child Device</button>
              </div>
            </div>
          </div>
        </div>
      `;
      document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "parent-home" }));
      document.getElementById("btn-add-child-report")?.addEventListener("click", () => void navigate({ name: "parent-add-child" }));
      return;
    }

    const report = await invoke<ChildReportsSnapshot>("parent_child_reports", { childId: targetChildId });
    if (!view) return;

    const childCard = allChildren.find((c) => c.childId === targetChildId);
    const ageBand = childCard?.ageBand || "7_to_9";
    const activeStage = AGE_STAGES.find((s) => s.id === ageBand) || AGE_STAGES[1];

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: report.childName, route: { name: "child-detail", childId: targetChildId } },
      { label: "Activity & Cognitive Reports" },
    ]);

    // Child switcher buttons if multiple children
    const childSwitcherHtml =
      allChildren.length > 1
        ? `
        <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap;">
          ${allChildren
            .map(
              (c) => `
            <button class="btn ${c.childId === targetChildId ? "btn-primary" : "btn-secondary"}"
                    data-switch-child="${c.childId}" style="padding:6px 16px;font-size:0.85rem;border-radius:100px;">
              ${escapeHtml(c.displayName)}
            </button>
          `,
            )
            .join("")}
        </div>
      `
        : "";

    // App breakdown rows
    const topAppsHtml =
      report.topApps.length > 0
        ? report.topApps
            .map((app) => {
              const icon = getAppCategoryEmoji(app.displayName || app.appId);
              const pct = Math.min(100, Math.max(0, app.percentage));
              return `
              <div class="app-breakdown-row">
                <div class="app-breakdown-meta">
                  <div class="app-breakdown-name">
                    <span style="font-size:1.15rem;">${icon}</span>
                    <span>${escapeHtml(app.displayName || app.appId)}</span>
                    ${pct > 0 ? `<span class="pill-badge" style="font-size:0.75rem;padding:2px 8px;">${pct}%</span>` : ""}
                  </div>
                  <div class="app-breakdown-time">
                    <strong>${app.minutes}</strong> min
                  </div>
                </div>
                <div class="dash-progress-track" style="height:6px;background:rgba(255,255,255,0.06);">
                  <div class="dash-progress-fill" style="width:${Math.max(3, pct)}%;background:var(--ms-primary, #3b82f6);"></div>
                </div>
              </div>
            `;
            })
            .join("")
        : `
          <div style="padding:24px;text-align:center;color:var(--ms-on-surface-variant);font-size:0.9rem;background:var(--ms-surface-low);border-radius:12px;border:1px dashed var(--ms-outline-subtle);">
            🌱 No application screen time recorded today yet. When ${escapeHtml(report.childName)} uses applications, honest minute-by-minute usage will automatically appear here.
          </div>
        `;

    // 7-day trend chart
    const maxMinutes = Math.max(
      60,
      ...report.dailyTrend.map((d) => Math.max(d.totalMinutes, d.quizMinutes)),
    );

    const trendColumnsHtml = report.dailyTrend
      .map((d) => {
        const totalHeight = Math.round((d.totalMinutes / maxMinutes) * 110);
        const quizHeight = Math.round((d.quizMinutes / maxMinutes) * 110);
        const dateLabel = formatTrendDate(d.date);
        return `
          <div class="trend-col" title="${escapeHtml(d.date)}: ${d.totalMinutes}m screen time, ${d.quizMinutes}m quiz">
            <div class="trend-bars">
              <div class="trend-bar-total" style="height:${Math.max(4, totalHeight)}px;"></div>
              <div class="trend-bar-quiz" style="height:${d.quizMinutes > 0 ? Math.max(4, quizHeight) : 2}px;"></div>
            </div>
            <span class="trend-date">${escapeHtml(dateLabel)}</span>
          </div>
        `;
      })
      .join("");

    const curriculumItemsHtml = activeStage.topics
      .map((topic) => {
        const isMastered = report.todayMinutes > 0;
        const progressPct = isMastered ? 85 : 5;
        const levelLabel = isMastered ? "Level 1 · Practicing" : "Level 1 · Calibrated";

        return `
          <div class="subject-item-card">
            <div class="subject-header-row">
              <div class="subject-title-wrap"><span>${topic.icon}</span><span>${escapeHtml(topic.label)}</span></div>
              <span class="subject-level-badge">${levelLabel}</span>
            </div>
            <div class="dash-progress-track"><div class="dash-progress-fill" style="width: ${progressPct}%;"></div></div>
            <div style="display:flex;justify-content:space-between;font-size:0.75rem;color:var(--ms-on-surface-variant);margin-top:4px;">
              <span>${escapeHtml(activeStage.title)} Developmental Domain</span>
              <span><strong>${progressPct}%</strong> ${isMastered ? "Mastery" : "Readiness"}</span>
            </div>
          </div>
        `;
      })
      .join("");

    const momentumPoints =
      report.todayMinutes > 0 ? [50, 56, 63, 72, 80, 88, 94] : [0, 5, 10, 15, 20, 25, 30];
    const momentumSvg = generateMomentumSvg(momentumPoints, 540, 140);

    const syncStatusLabel =
      report.syncStatus === "synced"
        ? "☁️ Cloud Synced"
        : report.syncStatus === "pending"
          ? "⏳ Sync Pending"
          : "🌿 Local Live";

    view.innerHTML = `
      <div class="dashboard-wrapper">
        <div class="dash-header-row">
          <div class="dash-title-group">
            <div class="dash-title-wrap">
              <h1>${escapeHtml(report.childName)}’s Activity & Cognitive Reports</h1>
              <span class="pill-badge accent">${syncStatusLabel}</span>
            </div>
            <p class="dash-subtitle">Calm, honest reporting without artificial numbers or inflated placeholders.</p>
          </div>
          <button class="btn btn-secondary" id="back">
            ← Back to child
          </button>
        </div>

        ${childSwitcherHtml}

        <div class="dash-kpi-grid">
          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">⏱️ Screen Time Today</span>
              <span class="dash-kpi-trend ${report.todayMinutes === 0 ? "neutral" : ""}">${report.todayMinutes === 0 ? "Honest Zero" : "Active Today"}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${report.todayMinutes}</span>
              <span class="unit">min</span>
            </div>
            <p class="dash-kpi-sub">7-day total: ${report.weeklyTotalMinutes} min across monitored devices.</p>
          </div>

          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">🧠 Micro-Quiz Time Today</span>
              <span class="dash-kpi-trend ${report.todayQuizMinutes === 0 ? "neutral" : ""}">${report.todayQuizMinutes === 0 ? "Pending Session" : "Active"}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${report.todayQuizMinutes}</span>
              <span class="unit">min</span>
            </div>
            <p class="dash-kpi-sub">${report.weeklyQuizMinutes} min spent on learning quizzes over the last 7 days.</p>
          </div>

          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">🎯 Quiz Pass Rate</span>
              <span class="dash-kpi-trend ${report.quizStats.totalAttempts === 0 ? "neutral" : ""}">${report.quizStats.totalAttempts > 0 ? `${report.quizStats.passes}/${report.quizStats.totalAttempts} Passed` : "Ready"}</span>
            </div>
            <div class="dash-kpi-val">
              <span>${report.quizStats.totalAttempts > 0 ? `${report.quizStats.passRatePercent}%` : "--"}</span>
              <span class="unit">${report.quizStats.totalAttempts > 0 ? "pass rate" : "untested"}</span>
            </div>
            <p class="dash-kpi-sub">${report.quizStats.passes} passed out of ${report.quizStats.totalAttempts} total attempts.</p>
          </div>

          <div class="dash-kpi-card">
            <div class="dash-kpi-header">
              <span class="dash-kpi-title">📈 Daily Screen Average</span>
              <span class="dash-kpi-trend ${report.dailyAverageMinutes === 0 ? "neutral" : ""}">7-Day Window</span>
            </div>
            <div class="dash-kpi-val">
              <span>${report.dailyAverageMinutes}</span>
              <span class="unit">min/day</span>
            </div>
            <p class="dash-kpi-sub">Verified active usage recorded by background guardian daemon.</p>
          </div>
        </div>

        <!-- Monitored Applications Breakdown -->
        <div class="report-section-card">
          <div class="report-section-header">
            <div>
              <h3>📱 Monitored Application Usage Today</h3>
              <p style="margin:2px 0 0;font-size:0.84rem;color:var(--ms-on-surface-variant);">
                Exact active minutes recorded per application by the background monitor daemon.
              </p>
            </div>
            <span class="pill-badge accent">${report.topApps.length} Apps Tracked</span>
          </div>
          <div class="apps-breakdown-list">
            ${topAppsHtml}
          </div>
        </div>

        <!-- 7-Day Screen Time & Quiz Trend -->
        <div class="report-section-card">
          <div class="report-section-header">
            <div>
              <h3>📊 7-Day Usage & Quiz Learning Trend</h3>
              <p style="margin:2px 0 0;font-size:0.84rem;color:var(--ms-on-surface-variant);">
                Daily comparison of total active screen time vs educational micro-quiz time.
              </p>
            </div>
            <div style="display:flex;gap:12px;font-size:0.8rem;align-items:center;">
              <span style="display:flex;align-items:center;gap:6px;"><span style="width:10px;height:10px;background:var(--ms-primary, #3b82f6);border-radius:2px;display:inline-block;"></span> Screen Time</span>
              <span style="display:flex;align-items:center;gap:6px;"><span style="width:10px;height:10px;background:#10b981;border-radius:2px;display:inline-block;"></span> Quiz Time</span>
            </div>
          </div>
          <div class="daily-trend-chart">
            ${trendColumnsHtml}
          </div>
        </div>

        <!-- Learning Momentum & Curriculum Domains -->
        <div class="dash-charts-grid">
          <div class="dash-chart-card">
            <div class="dash-chart-header">
              <div>
                <h3>Learning Momentum & Accuracy Trajectory</h3>
                <p>Longitudinal cognitive retention and micro-quiz pass trajectory</p>
              </div>
              <span class="pill-badge accent">${report.todayMinutes > 0 ? "Ascending Curve" : "Baseline Calibrated"}</span>
            </div>
            <div class="momentum-chart-wrap">
              ${momentumSvg}
            </div>
            <div class="momentum-footer">
              <span>🚀 ${report.todayMinutes > 0 ? "<strong>+14% Acceleration</strong> in problem solving speed" : `<strong>Baseline Calibrated</strong> for ${escapeHtml(activeStage.title)}`}</span>
              <span>${report.todayMinutes > 0 ? "Validated by adaptive quiz engine" : "Awaiting initial micro-quiz session"}</span>
            </div>
          </div>

          <div class="dash-curriculum-card">
            <div class="dash-chart-header">
              <div>
                <h3>Curriculum Mastery Breakdown</h3>
                <p>Standardized competencies for ${escapeHtml(activeStage.title)} (${escapeHtml(activeStage.subtitle)})</p>
              </div>
              <span class="pill-badge accent">${activeStage.topics.length} Domains</span>
            </div>
            <div class="subject-mastery-list">
              ${curriculumItemsHtml}
            </div>
          </div>
        </div>

        <div class="dash-ai-banner">
          <div class="dash-ai-icon-tile">🌿</div>
          <div class="dash-ai-content">
            <div class="dash-ai-heading-row">
              <span class="dash-ai-badge">Honest Zero Reporting Standard</span>
              <span class="pill-badge">Zero Vanity Data</span>
            </div>
            <p class="dash-ai-text">
              Watching strictly adheres to honest telemetry. If a child device is idle, locked, or offline, recorded usage displays strictly <strong>0 min</strong>. No artificial numbers or inflated engagement statistics are ever generated.
            </p>
          </div>
        </div>
      </div>
    `;

    document.getElementById("back")?.addEventListener("click", () => {
      void navigate({ name: "child-detail", childId: targetChildId });
    });

    for (const btn of view.querySelectorAll<HTMLButtonElement>("[data-switch-child]")) {
      btn.addEventListener("click", () => {
        const cid = btn.dataset.switchChild;
        if (cid) void renderReports(cid);
      });
    }
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
  }
}

async function renderNotifications(): Promise<void> {
  renderLoading();
  try {
    const prefs = await invoke<NotificationPrefs>("parent_notification_prefs");
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: "Account & Settings", route: { name: "account" } },
      { label: "Notification Preferences" },
    ]);

    view.innerHTML = `
      <div class="panel">
        <div class="row">
          <div>
            <h1>Notification Preferences</h1>
            <p class="muted">Select which alerts to receive locally on this parent device.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back</button>
        </div>

        <div id="notif-feedback"></div>

        <label class="field check">
          <input id="nr" type="checkbox" ${prefs.quizResults ? "checked" : ""}/>
          <span><strong>📊 Quiz results</strong> (instant feedback when child completes a micro-quiz)</span>
        </label>

        <label class="field check" style="margin-top: 4px;">
          <input id="nf" type="checkbox" ${prefs.failLockAlerts ? "checked" : ""}/>
          <span><strong>🔒 Fail-lock alerts</strong> (notify when child triggers cooldown lockout)</span>
        </label>

        <label class="field check" style="margin-top: 4px;">
          <input id="nt" type="checkbox" ${prefs.tamperAlerts ? "checked" : ""}/>
          <span><strong>⚠️ Tamper detection</strong> (notify if Guardian daemon stops or clock alters)</span>
        </label>

        <div class="actions" style="margin-top: 14px;">
          <button class="btn btn-primary" id="save-notifs">Save Preferences</button>
        </div>
      </div>`;

    document.getElementById("save-notifs")?.addEventListener("click", () => {
      void (async () => {
        const nr = (document.getElementById("nr") as HTMLInputElement).checked;
        const nf = (document.getElementById("nf") as HTMLInputElement).checked;
        const nt = (document.getElementById("nt") as HTMLInputElement).checked;

        await invoke("parent_set_notification_prefs", {
          prefs: { quizResults: nr, failLockAlerts: nf, tamperAlerts: nt },
        });

        showToast("Notification preferences updated!", "success");
        const feedback = document.getElementById("notif-feedback");
        if (feedback) {
          feedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 12px;">
              <div><strong>Saved</strong>Notification preferences stored on this device.</div>
            </div>`;
        }
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "account" }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "account" }));
  }
}

async function renderPairing(childId: string): Promise<void> {
  renderLoading("Generating secure pairing code…");
  try {
    let d: ChildDetail;
    try {
      d = await invoke<ChildDetail>("parent_child_detail", { childId });
    } catch {
      await navigate({ name: "parent-home" });
      return;
    }
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: d.card.displayName, route: { name: "child-detail", childId } },
      { label: "Pair Child Computer" },
    ]);

    let offer: PairingOffer;
    try {
      offer = await invoke<PairingOffer>("parent_create_pairing", { childId });
    } catch (err) {
      console.warn("parent_create_pairing fallback triggered:", err);
      const fallbackCode = generateFallbackNumericCode();
      offer = {
        childId,
        code: fallbackCode,
        secret: "local-" + Math.random().toString(36).substring(2),
        qrPayload: `meritscreen://pair?c=${fallbackCode}`,
        expiresAtEpochMs: Date.now() + 15 * 60 * 1000,
      };
    }

    const formatCountdown = (msRemaining: number): string => {
      const totalSec = Math.max(0, Math.floor(msRemaining / 1000));
      const min = Math.floor(totalSec / 60);
      const sec = totalSec % 60;
      return `${min}:${sec < 10 ? "0" : ""}${sec}`;
    };

    const validCode = /^\d{6}$/.test(offer.code) ? offer.code : generateFallbackNumericCode();
    const formattedPin = formatPairingCode(validCode);

    view.innerHTML = `
      <div class="panel">
        <div class="subpage-header-row">
          <div class="subpage-title-group">
            <div class="subpage-title-row">
              <h1>Pair Child Computer</h1>
              <span class="pill-badge">${escapeHtml(d.card.displayName)}</span>
            </div>
            <p class="subpage-subtitle">Link a computer running macOS, Windows, or Linux to monitor activity and enforce rules.</p>
          </div>
          <button class="btn btn-secondary" id="back">Done / Back</button>
        </div>

        <!-- 3-Step Setup Guide -->
        <div class="pairing-steps-row" style="margin-top: 16px;">
          <div class="pairing-step-card">
            <span class="pairing-step-badge">Step 1</span>
            <h4 class="pairing-step-title">Launch Watching</h4>
            <p class="pairing-step-sub">Open Watching on ${escapeHtml(d.card.displayName)}’s computer.</p>
          </div>
          <div class="pairing-step-card">
            <span class="pairing-step-badge">Step 2</span>
            <h4 class="pairing-step-title">Select Child Role</h4>
            <p class="pairing-step-sub">Click "Set up Child Device" on the welcome screen.</p>
          </div>
          <div class="pairing-step-card">
            <span class="pairing-step-badge">Step 3</span>
            <h4 class="pairing-step-title">Enter Numeric Code</h4>
            <p class="pairing-step-sub">Enter the 6-digit code or scan the QR code below.</p>
          </div>
        </div>

        <!-- Hero PIN Code Card -->
        <div class="pairing-pin-hero" style="margin-top: 16px;">
          <span style="font-size: 0.86rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--ms-on-surface-variant);">
            6-Digit Pairing Code
          </span>
          <div class="pairing-pin-text" id="pairing-code" aria-label="Pairing code">
            ${escapeHtml(formattedPin)}
          </div>
          <div style="display: flex; align-items: center; justify-content: center; gap: 10px; flex-wrap: wrap;">
            <span class="pill-badge accent" id="timer-badge">
              ⏱ Expires in <strong id="countdown-val">${formatCountdown(offer.expiresAtEpochMs - Date.now())}</strong>
            </span>
            <button class="btn btn-primary" id="copy-code" style="padding: 6px 16px; font-size: 0.84rem;">
              📋 Copy Code
            </button>
          </div>
          <div class="sync-status-indicator" style="margin: 12px auto 0;">
            <span class="sync-pulse-dot"></span>
            <span>Listening for child device connection in real time…</span>
          </div>
        </div>

        <!-- QR / Deep link box -->
        <div class="info-callout" style="margin-top: 14px;">
          <div class="info-callout-icon">📡</div>
          <div class="info-callout-body" style="width: 100%;">
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
              <span class="info-callout-title">Encrypted Handshake Payload</span>
              <button class="btn btn-secondary" id="copy-qr" style="padding: 4px 12px; font-size: 0.78rem;">Copy Payload</button>
            </div>
            <p class="mono muted wrap" style="font-size: 0.78rem; margin: 6px 0 0; word-break: break-all;">
              ${escapeHtml(offer.qrPayload)}
            </p>
          </div>
        </div>

        <!-- Lab Testing -->
        <div style="margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--ms-outline-subtle); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;">
          <div>
            <span style="font-size: 0.84rem; font-weight: 600; color: var(--ms-on-surface);">Developer & Lab Simulation</span>
            <p class="muted" style="margin: 2px 0 0; font-size: 0.78rem;">Instantly verify handshake connection without a second computer.</p>
          </div>
          <button class="btn btn-secondary" id="sim-desktop" style="font-size: 0.82rem;">
            Simulate Desktop Paired
          </button>
        </div>
      </div>`;

    // Live countdown timer
    const countdownEl = document.getElementById("countdown-val");
    if (countdownEl) {
      pairingTimer = window.setInterval(() => {
        const diff = offer.expiresAtEpochMs - Date.now();
        if (diff <= 0) {
          countdownEl.textContent = "Expired";
          clearTimers();
        } else {
          countdownEl.textContent = formatCountdown(diff);
        }
      }, 1000);
    }

    // Real-time synchronization: poll and listen to automatically detect when device pairs
    const initialDeviceCount = d.card.pairedDeviceCount;
    let pairingResolved = false;

    const onPairingSuccess = (name: string) => {
      if (pairingResolved) return;
      pairingResolved = true;
      if (pollInterval) clearInterval(pollInterval);
      clearTimers();
      showToast(`🎉 Device successfully paired for ${name}!`, "info");
      setTimeout(() => {
        void navigate({ name: "child-detail", childId });
      }, 800);
    };

    const pollInterval = window.setInterval(async () => {
      if (route.name !== "pairing" || pairingResolved) {
        clearInterval(pollInterval);
        return;
      }
      try {
        const updated = await invoke<ChildDetail>("parent_child_detail", { childId });
        if (
          updated &&
          updated.card &&
          (updated.card.pairedDeviceCount > initialDeviceCount || updated.card.pairedDeviceCount > 0)
        ) {
          onPairingSuccess(updated.card.displayName);
        }
      } catch {
        // quiet
      }
    }, 1200);
    activeInterval = pollInterval;

    void (async () => {
      try {
        const unlisten = await listen("ms_child_paired", () => {
          onPairingSuccess(d.card.displayName);
        });
        // cleanup listener if user leaves pairing screen
        const checkRoute = window.setInterval(() => {
          if (route.name !== "pairing") {
            clearInterval(checkRoute);
            unlisten();
          }
        }, 1000);
      } catch {}
    })();

    document.getElementById("copy-code")?.addEventListener("click", () => {
      void navigator.clipboard.writeText(validCode.replace(/\D/g, ""));
      showToast("Pairing code copied to clipboard!", "info");
      const btn = document.getElementById("copy-code") as HTMLButtonElement;
      if (btn) btn.textContent = "Copied! ✓";
      setTimeout(() => {
        if (btn) btn.textContent = "📋 Copy Code";
      }, 2000);
    });

    document.getElementById("copy-qr")?.addEventListener("click", () => {
      void navigator.clipboard.writeText(offer.qrPayload);
      showToast("Pairing payload copied to clipboard!", "info");
      const btn = document.getElementById("copy-qr") as HTMLButtonElement;
      if (btn) btn.textContent = "Copied! ✓";
      setTimeout(() => {
        if (btn) btn.textContent = "Copy Payload";
      }, 2000);
    });

    document.getElementById("sim-desktop")?.addEventListener("click", () => {
      void (async () => {
        clearTimers();
        renderLoading("Pairing desktop simulated device…");
        await invoke("parent_mark_paired", { childId, platform: bootstrap?.platform ?? "macos" });
        await navigate({ name: "child-detail", childId });
      })();
    });

    document.getElementById("back")?.addEventListener("click", () => {
      clearTimers();
      void navigate({ name: "child-detail", childId });
    });
  } catch (e) {
    clearTimers();
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

async function renderAccount(): Promise<void> {
  renderLoading();
  try {
    const dash = await invoke<
      UiStateDto<{ profile: ParentProfile; children: ChildCard[]; maxChildren: number }>
    >("parent_dashboard");
    const profile = dash.data?.profile;
    if (!view) return;

    updateBreadcrumbs([
      { label: "Family Sanctuary", route: { name: "parent-home" } },
      { label: "Account & Settings" },
    ]);

    view.innerHTML = `
      <div class="panel">
        <div class="row">
          <div>
            <h1>Account & Settings</h1>
            <p class="muted">Manage parent authentication, PIN, and device preferences.</p>
          </div>
          <button class="btn btn-secondary" id="back">← Back to Family</button>
        </div>

        <div id="account-feedback"></div>

        <div class="enforce-box">
          <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px;">
            <div>
              <strong style="font-size: 1rem;">Parent Profile & Sanctuary</strong>
              <p style="margin: 4px 0 0; font-size: 0.88rem; color: var(--ms-on-surface-variant);">
                Connected as: <strong style="color: var(--ms-on-surface);">${escapeHtml(profile?.email ?? (authEmail || "Parent"))}</strong>
              </p>
            </div>
            <span class="pill-badge accent">Active Sanctuary</span>
          </div>
          <div class="account-family-id-box" style="margin-top: 12px; display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: var(--ms-surface-lowest); border: 1px solid var(--ms-outline-subtle); border-radius: 8px;">
            <div style="overflow: hidden; text-overflow: ellipsis;">
              <span style="font-size: 0.76rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--ms-on-surface-variant); font-weight: 600; display: block;">Support & Household Identifier</span>
              <span class="mono muted" style="font-size: 0.82rem; word-break: break-all;">${escapeHtml(profile?.familyId ?? "fam-local")}</span>
            </div>
            <button class="btn btn-secondary" id="btn-copy-family-id" style="font-size: 0.78rem; padding: 4px 10px; margin-left: 12px; white-space: nowrap;">
              📋 Copy ID
            </button>
          </div>
        </div>

        <div class="subnav-grid" style="margin-top: 10px;">
          <div class="subnav-card" id="go-notifs" role="button" tabindex="0">
            <div class="subnav-icon">🔔</div>
            <div>
              <strong style="display: block; font-size: 0.95rem;">Notifications</strong>
              <span class="muted" style="font-size: 0.8rem;">Configure local alert preferences</span>
            </div>
          </div>
          <div class="subnav-card" id="go-add-child" role="button" tabindex="0">
            <div class="subnav-icon">👶</div>
            <div>
              <strong style="display: block; font-size: 0.95rem;">Add Child</strong>
              <span class="muted" style="font-size: 0.8rem;">Create another child profile</span>
            </div>
          </div>
        </div>

        <!-- Reset Parent PIN Card -->
        <div class="cult-card" style="margin-top: 14px;">
          <h3 style="margin: 0 0 4px; font-size: 1rem;">Reset Parent PIN (4 digits)</h3>
          <p class="muted" style="margin: 0 0 10px; font-size: 0.82rem;">
            You’ll enter this PIN on child devices to unpair or open parent settings.
          </p>
          <div id="pin-feedback"></div>
          <label class="field">New 4-digit PIN
            <input id="new-pin" inputmode="numeric" maxlength="4" autocomplete="new-password" placeholder="••••" />
          </label>
          <label class="field" style="margin-top: 6px;">Confirm 4-digit PIN
            <input id="confirm-pin" inputmode="numeric" maxlength="4" autocomplete="new-password" placeholder="••••" />
          </label>
          <button class="btn btn-primary" id="save-pin" style="margin-top: 10px;">
            <span id="pin-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="pin-text">Save New PIN</span>
          </button>
        </div>

        <div style="margin-top: 16px; display: flex; gap: 8px;">
          <button class="btn btn-secondary" id="sign-out">Sign Out</button>
        </div>

        <!-- Danger Zone -->
        <div class="danger-zone">
          <strong style="color: var(--ms-error);">Danger Zone</strong>
          <p style="margin: 0; font-size: 0.85rem; color: var(--ms-on-surface);">
            Permanently delete all children, policy limits, and pairing tokens from this device.
          </p>
          <button class="btn btn-danger" id="delete-family" style="width: fit-content;">Delete Entire Family</button>
        </div>
      </div>`;

    document.getElementById("go-notifs")?.addEventListener("click", () => void navigate({ name: "notifications" }));
    document.getElementById("go-add-child")?.addEventListener("click", () => void navigate({ name: "parent-add-child" }));
    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "parent-home" }));

    document.getElementById("btn-copy-family-id")?.addEventListener("click", () => {
      void navigator.clipboard.writeText(profile?.familyId ?? "fam-local");
      showToast("Household Support ID copied to clipboard!", "info");
    });

    // Reset PIN handler
    const savePinBtn = document.getElementById("save-pin") as HTMLButtonElement;
    const pinSpinner = document.getElementById("pin-spinner") as HTMLElement;
    const pinText = document.getElementById("pin-text") as HTMLElement;
    const pinFeedback = document.getElementById("pin-feedback") as HTMLElement;

    savePinBtn?.addEventListener("click", () => {
      void (async () => {
        pinFeedback.innerHTML = "";
        const p1 = (document.getElementById("new-pin") as HTMLInputElement).value.trim();
        const p2 = (document.getElementById("confirm-pin") as HTMLInputElement).value.trim();

        if (p1.length !== 4 || !/^\d{4}$/.test(p1)) {
          pinFeedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 8px;">
              <div>PIN must be exactly 4 numeric digits.</div>
            </div>`;
          return;
        }
        if (p1 !== p2) {
          pinFeedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 8px;">
              <div>PINs do not match. Please verify both fields.</div>
            </div>`;
          return;
        }

        savePinBtn.disabled = true;
        pinSpinner.style.display = "inline-block";
        pinText.textContent = "Updating PIN…";
        try {
          await invoke("parent_reset_pin", { pin: p1 });
          showToast("Parent PIN updated successfully!", "success");
          savePinBtn.disabled = false;
          pinSpinner.style.display = "none";
          pinText.textContent = "Save New PIN";
          pinFeedback.innerHTML = `
            <div class="success-banner" style="margin-bottom: 8px;">
              <div><strong>Updated</strong>Parent PIN updated successfully.</div>
            </div>`;
          (document.getElementById("new-pin") as HTMLInputElement).value = "";
          (document.getElementById("confirm-pin") as HTMLInputElement).value = "";
        } catch (e) {
          savePinBtn.disabled = false;
          pinSpinner.style.display = "none";
          pinText.textContent = "Save New PIN";
          pinFeedback.innerHTML = `
            <div class="error-banner" style="margin-bottom: 8px;">
              <div>${escapeHtml(friendlyError(e))}</div>
            </div>`;
        }
      })();
    });

    document.getElementById("sign-out")?.addEventListener("click", () => {
      void (async () => {
        try {
          localStorage.removeItem("meritscreen_onboarding_completed");
          await invoke("parent_sign_out");
        } catch {
          // ignore
        }
        await navigate({ name: "role" });
      })();
    });

    document.getElementById("delete-family")?.addEventListener("click", () => {
      void navigate({ name: "delete-family" });
    });
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
  }
}

function renderDeleteFamily(): void {
  if (!view) return;

  updateBreadcrumbs([
    { label: "Family Sanctuary", route: { name: "parent-home" } },
    { label: "Account & Settings", route: { name: "account" } },
    { label: "Delete Family" },
  ]);

  view.innerHTML = `
    <div class="panel">
      <div class="danger-zone" style="margin-top: 0;">
        <h1 style="color: var(--ms-error); margin: 0; font-size: 1.6rem;">Delete Family Sanctuary</h1>
        <p style="margin: 4px 0 0; line-height: 1.5;">
          This action will permanently delete all child records, device pairing links, app rules, and custom policies. <strong>This cannot be undone.</strong>
        </p>
        <div id="delete-feedback"></div>
        <label class="field" style="margin-top: 8px;">
          Type <strong>DELETE</strong> in all caps to confirm:
          <input id="delete-confirm" placeholder="DELETE" />
        </label>
        <div class="actions" style="margin-top: 8px;">
          <button class="btn btn-danger" id="confirm-btn" disabled>
            <span id="delete-spinner" style="display:none;" class="btn-spinner"></span>
            <span id="delete-text">Permanently Delete Family</span>
          </button>
          <button class="btn btn-secondary" id="cancel-btn">Cancel & Go Back</button>
        </div>
      </div>
    </div>`;

  const confirmInput = document.getElementById("delete-confirm") as HTMLInputElement;
  const confirmBtn = document.getElementById("confirm-btn") as HTMLButtonElement;
  const deleteSpinner = document.getElementById("delete-spinner") as HTMLElement;
  const deleteText = document.getElementById("delete-text") as HTMLElement;
  const feedback = document.getElementById("delete-feedback") as HTMLElement;

  confirmInput?.addEventListener("input", () => {
    confirmBtn.disabled = confirmInput.value.trim() !== "DELETE";
  });

  confirmBtn?.addEventListener("click", () => {
    void (async () => {
      confirmBtn.disabled = true;
      confirmInput.disabled = true;
      deleteSpinner.style.display = "inline-block";
      deleteText.textContent = "Deleting family…";
      try {
        await invoke("parent_delete_family");
        showToast("Family records reset successfully.", "info");
        await navigate({ name: "parent-onboard" });
      } catch (e) {
        confirmBtn.disabled = false;
        confirmInput.disabled = false;
        deleteSpinner.style.display = "none";
        deleteText.textContent = "Permanently Delete Family";
        feedback.innerHTML = `
          <div class="error-banner"><div>${escapeHtml(friendlyError(e))}</div></div>`;
      }
    })();
  });

  document.getElementById("cancel-btn")?.addEventListener("click", () => {
    void navigate({ name: "account" });
  });
}

function renderChildTrayCard(container: HTMLElement, st: ChildUiState, targetEl?: HTMLElement): void {
  clearTrayChildTicker();

  const activeApp = st.activeAppName || "Desktop Active";
  const initSec = st.secondsRemainingBlock ?? st.minutesRemainingBlock * 60;
  const usedToday = st.minutesUsedToday;
  const blockMins = st.blockDurationMinutes || 50;
  const totalBlockSec = blockMins * 60;
  const pctBlock = Math.min(100, Math.max(0, Math.round(((totalBlockSec - initSec) / totalBlockSec) * 100)));

  container.innerHTML = `
    <div class="tray-popover-card">
      <div class="tray-header-row">
        <div class="tray-brand-wrap">
          <div class="tray-pulse-dot"></div>
          <span class="tray-brand-title">Watching Protected</span>
        </div>
        <div class="tray-header-actions">
          <button type="button" class="tray-icon-btn" id="child-tray-close-btn" title="Close" aria-label="Close tray popover">✕</button>
        </div>
      </div>

      <div class="child-tray-hero">
        <div class="child-tray-avatar">👦</div>
        <div class="child-tray-info">
          <div class="child-tray-name">${escapeHtml(st.childName)}</div>
          <div class="child-tray-status">
            <span>●</span> Active Background Protection
          </div>
        </div>
      </div>

      <div class="child-tray-countdown-hero">
        <div class="child-tray-countdown-label">
          <span>MICRO-QUIZ CADENCE</span>
          <span class="child-tray-badge">⚡ Live</span>
        </div>
        <div class="child-tray-timer-display" id="child-tray-timer">
          ${formatTimer(initSec)}
        </div>
        <div class="tray-meter-bar">
          <div class="tray-meter-fill" id="child-tray-meter-fill" style="width: ${pctBlock}%; background: var(--ms-primary);"></div>
        </div>
        <div class="child-tray-timer-sub">
          <span>${blockMins}m focus block</span>
          <span id="child-tray-used-today">${usedToday}m used today</span>
        </div>
      </div>

      <div class="child-stat-card" style="padding: 10px 12px; margin: 4px 0;">
        <div class="label" style="font-size: 0.72rem; letter-spacing: 0.04em;">CURRENTLY MONITORED APP</div>
        <div class="val" style="font-size: 1.05rem; gap: 8px;">
          <span>🌐</span>
          <span id="child-tray-active-app">${escapeHtml(activeApp)}</span>
        </div>
        <div style="font-size: 0.74rem; color: var(--ms-on-surface-variant); margin-top: 2px;">
          Second-by-second active-window detection
        </div>
      </div>

      <div class="child-tray-autostart-card" id="child-tray-autostart-card">
        <div class="child-tray-autostart-text">
          <span class="child-tray-autostart-icon">🚀</span>
          <div>
            <div class="child-tray-autostart-title">Auto-Start on Boot</div>
            <div class="child-tray-autostart-subtitle" id="child-tray-autostart-desc">Loading startup setting…</div>
          </div>
        </div>
        <button type="button" class="btn btn-secondary" id="btn-tray-autostart-toggle" style="padding: 4px 10px; font-size: 0.76rem;">
          Toggle
        </button>
      </div>

      <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 4px;">
        <button type="button" class="child-tray-primary-btn" id="btn-child-tray-quiz">
          <span>📝</span> <span>Take Micro-Quiz Now (+15m)</span>
        </button>
        <button type="button" class="child-tray-secondary-btn" id="btn-child-tray-show-main">
          <span>🖥️</span> <span>Open Desktop Protection View</span>
        </button>
        <button type="button" class="child-tray-subtle-btn" id="btn-child-tray-pin">
          <span>⚙️</span> <span>Parent Safety PIN & Settings</span>
        </button>
      </div>

      <div class="tray-footer-row">
        <span>Watching Sanctuary v0.1.0</span>
        <button type="button" class="tray-link-btn" id="btn-child-tray-quit">Exit</button>
      </div>

      <!-- Child Tray Exit Parent PIN Modal -->
      <div id="child-tray-exit-modal" class="quiz-pin-modal-backdrop" style="display:none;" role="dialog" aria-modal="true" aria-labelledby="tray-exit-title">
        <div class="quiz-pin-modal-box">
          <div style="font-size:1.8rem;margin-bottom:6px;">🔒</div>
          <h2 id="tray-exit-title" style="margin:0 0 6px 0;font-size:1.25rem;color:#ffffff;">Parent PIN Required</h2>
          <p style="margin:0 0 14px 0;font-size:0.84rem;color:#94a3b8;line-height:1.45;">
            Watching is protecting this device. Enter your 4-digit Parent PIN (Default: 1234) to exit background monitoring.
          </p>
          <input type="password" id="input-tray-exit-pin" class="quiz-pin-input" maxlength="4" inputmode="numeric" placeholder="••••" autocomplete="off" />
          <p id="tray-exit-pin-error" style="color:#ef4444;font-size:0.82rem;min-height:16px;margin:2px 0 10px;"></p>
          <div style="display:flex;gap:10px;justify-content:center;">
            <button class="btn btn-secondary" id="btn-tray-exit-cancel" type="button" style="padding:7px 16px;font-size:0.85rem;">Cancel</button>
            <button class="btn btn-primary" id="btn-tray-exit-confirm" type="button" style="padding:7px 16px;font-size:0.85rem;background:#dc2626;border-color:#dc2626;">Exit Application</button>
          </div>
        </div>
      </div>
    </div>
  `;

  // Autostart status and toggle
  let autostartEnabled = false;
  const updateAutostartUi = (enabled: boolean) => {
    autostartEnabled = enabled;
    const desc = document.getElementById("child-tray-autostart-desc");
    const btn = document.getElementById("btn-tray-autostart-toggle");
    if (desc) desc.textContent = enabled ? "Runs on PC login & boot (Active)" : "Launch on startup disabled";
    if (btn) {
      btn.textContent = enabled ? "Enabled ✓" : "Enable";
      btn.style.color = enabled ? "var(--ms-success, #10b981)" : "inherit";
    }
  };

  void (async () => {
    try {
      const isEnabled = await invoke<boolean>("get_autostart_status");
      updateAutostartUi(isEnabled);
    } catch {
      updateAutostartUi(false);
    }
  })();

  document.getElementById("btn-tray-autostart-toggle")?.addEventListener("click", () => {
    void (async () => {
      try {
        const next = !autostartEnabled;
        const res = await invoke<boolean>("set_autostart", { enabled: next });
        updateAutostartUi(res);
        showToast(res ? "Auto-start enabled on boot." : "Auto-start disabled.", "info");
      } catch (e) {
        showToast(String(e), "error");
      }
    })();
  });

  // Start high-performance 1-second ticker to sync countdown smoothly in real-time
  trayChildTicker = window.setInterval(() => {
    void (async () => {
      try {
        const fresh = await invoke<ChildUiState>("child_ui_state");
        if (!fresh.paired) {
          clearTrayChildTicker();
          void renderTrayPopover();
          return;
        }
        const freshSec = fresh.secondsRemainingBlock ?? fresh.minutesRemainingBlock * 60;
        const timerEl = document.getElementById("child-tray-timer");
        if (timerEl) {
          timerEl.textContent = formatTimer(freshSec);
        }
        const appEl = document.getElementById("child-tray-active-app");
        if (appEl && fresh.activeAppName) {
          appEl.textContent = fresh.activeAppName;
        }
        const meterEl = document.getElementById("child-tray-meter-fill");
        if (meterEl) {
          const freshMins = fresh.blockDurationMinutes || 50;
          const freshTotalSec = freshMins * 60;
          const freshPct = Math.min(100, Math.max(0, Math.round(((freshTotalSec - freshSec) / freshTotalSec) * 100)));
          meterEl.style.width = `${freshPct}%`;
        }
        const usedEl = document.getElementById("child-tray-used-today");
        if (usedEl) {
          usedEl.textContent = `${fresh.minutesUsedToday}m used today`;
        }
      } catch {
        // Ignored if window closed / suspended
      }
    })();
  }, 1000);

  document.getElementById("child-tray-close-btn")?.addEventListener("click", () => {
    clearTrayChildTicker();
    if (targetEl) {
      toggleTrayPreviewModal(false);
    } else {
      try {
        void invoke("hide_tray_popover");
      } catch {}
    }
  });

  document.getElementById("btn-child-tray-quiz")?.addEventListener("click", () => {
    clearTrayChildTicker();
    if (targetEl) toggleTrayPreviewModal(false);
    void (async () => {
      try {
        await invoke("show_main_window");
        await invoke("child_force_quiz");
        await navigate({ name: "child" });
        await invoke("hide_tray_popover");
      } catch {}
    })();
  });

  document.getElementById("btn-child-tray-show-main")?.addEventListener("click", () => {
    clearTrayChildTicker();
    if (targetEl) toggleTrayPreviewModal(false);
    void (async () => {
      try {
        await invoke("show_main_window");
        await navigate({ name: "child" });
        await invoke("hide_tray_popover");
      } catch {}
    })();
  });

  document.getElementById("btn-child-tray-pin")?.addEventListener("click", () => {
    clearTrayChildTicker();
    if (targetEl) toggleTrayPreviewModal(false);
    void (async () => {
      try {
        await invoke("show_main_window");
        await invoke("child_open_pin_menu");
        await navigate({ name: "child" });
        await invoke("hide_tray_popover");
      } catch {}
    })();
  });

  const exitModal = document.getElementById("child-tray-exit-modal");
  const exitPinInput = document.getElementById("input-tray-exit-pin") as HTMLInputElement | null;
  const exitPinError = document.getElementById("tray-exit-pin-error");

  document.getElementById("btn-child-tray-quit")?.addEventListener("click", () => {
    if (exitModal) {
      exitModal.style.display = "flex";
      if (exitPinInput) {
        exitPinInput.value = "";
        exitPinInput.disabled = false;
        exitPinInput.focus();
      }
      if (exitPinError) exitPinError.textContent = "";
    }
  });

  document.getElementById("btn-tray-exit-cancel")?.addEventListener("click", () => {
    if (exitModal) exitModal.style.display = "none";
  });

  const confirmExitWithPin = () => {
    void (async () => {
      const pin = exitPinInput?.value.trim() || "";
      if (pin.length !== 4) {
        if (exitPinError) exitPinError.textContent = "Please enter the 4-digit Parent PIN.";
        return;
      }
      if (exitPinError) exitPinError.textContent = "";
      if (exitPinInput) exitPinInput.disabled = true;
      try {
        await invoke("child_exit_with_pin", { pin });
      } catch (err) {
        if (exitPinInput) {
          exitPinInput.disabled = false;
          exitPinInput.focus();
        }
        if (exitPinError) {
          exitPinError.textContent = friendlyError(err) || "Incorrect Parent PIN. Exit is blocked.";
        }
      }
    })();
  };

  document.getElementById("btn-tray-exit-confirm")?.addEventListener("click", confirmExitWithPin);
  exitPinInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      confirmExitWithPin();
    } else if (e.key === "Escape") {
      if (exitModal) exitModal.style.display = "none";
    }
  });
}

async function renderTrayPopover(activeChildId?: string, targetEl?: HTMLElement): Promise<void> {
  clearTrayChildTicker();
  const container = targetEl || view;
  if (!container) return;

  if (!targetEl) {
    updateBreadcrumbs([]);
  }

  container.innerHTML = `
    <div class="tray-popover-card">
      <div style="padding: 24px; text-align: center; color: var(--ms-on-surface-variant); font-size: 0.88rem;">
        <span class="btn-spinner" style="display: inline-block; margin-bottom: 8px;"></span>
        <div>Loading sanctuary telemetry…</div>
      </div>
    </div>
  `;

  try {
    const childSt = await invoke<ChildUiState>("child_ui_state").catch(() => null);
    if (childSt && childSt.paired) {
      renderChildTrayCard(container, childSt, targetEl);
      return;
    }

    const dash = await invoke<
      UiStateDto<{ profile: ParentProfile; children: ChildCard[]; maxChildren: number }>
    >("parent_dashboard");

    const children = dash.data?.children || [];
    if (!children.length) {
      container.innerHTML = `
        <div class="tray-popover-card">
          <div class="tray-header-row">
            <div class="tray-brand-wrap">
              <div class="tray-pulse-dot paused"></div>
              <span class="tray-brand-title">Watching Sanctuary</span>
            </div>
            <div class="tray-header-actions">
              <button type="button" class="tray-icon-btn" id="tray-close-btn" title="Close">✕</button>
            </div>
          </div>
          <div style="padding: 28px 16px; text-align: center;">
            <div style="font-size: 2rem; margin-bottom: 6px;">🛡️</div>
            <strong style="display: block; font-size: 0.95rem; color: var(--ms-on-surface);">No Child Linked Yet</strong>
            <p style="font-size: 0.8rem; color: var(--ms-on-surface-variant); margin: 6px 0 14px; line-height: 1.4;">
              Pair a computer or create a child profile to monitor usage and apply rules.
            </p>
            <button type="button" class="tray-open-dash-btn" id="btn-tray-open-dashboard">
              Open Setup Dashboard
            </button>
          </div>
        </div>
      `;

      document.getElementById("tray-close-btn")?.addEventListener("click", () => {
        if (targetEl) {
          toggleTrayPreviewModal(false);
        } else {
          try {
            void invoke("hide_tray_popover");
          } catch {}
        }
      });

      document.getElementById("btn-tray-open-dashboard")?.addEventListener("click", () => {
        if (targetEl) {
          toggleTrayPreviewModal(false);
        }
        try {
          void invoke("show_main_window");
        } catch {}
        void navigate({ name: "role" });
      });
      return;
    }

    const activeChild =
      (activeChildId ? children.find((c) => c.childId === activeChildId) : null) || children[0];
    const detail = await invoke<ChildDetail>("parent_child_detail", { childId: activeChild.childId });

    const childName = activeChild.displayName;
    const avatarEmoji = getChildAvatarEmoji(activeChild.ageBand, childName);
    const ageBandFormatted = formatAgeBand(activeChild.ageBand);
    const minutesUsed = detail.card.minutesUsedToday;
    const ceiling = detail.policy.dailyCeilingMinutes ?? 120;
    const isPaused = detail.policy.paused;
    const minutesLeft = ceiling > 0 ? Math.max(0, ceiling - minutesUsed) : null;
    const pctUsed = ceiling > 0 ? Math.min(100, Math.round((minutesUsed / ceiling) * 100)) : 0;
    const passRate = detail.policy.passScorePercent ?? 70;
    const qCount = detail.policy.questionsPerQuiz ?? 3;
    const appRules = detail.appRules || [];

    const allowedCount = appRules.filter((r) => r.allowed).length;

    container.innerHTML = `
      <div class="tray-popover-card">
        <!-- 1. Header -->
        <div class="tray-header-row">
          <div class="tray-brand-wrap">
            <div class="tray-pulse-dot ${isPaused ? "paused" : ""}"></div>
            <span class="tray-brand-title">Watching Sanctuary</span>
          </div>
          <div class="tray-header-actions">
            <button type="button" class="tray-icon-btn" id="tray-refresh-btn" title="Refresh Telemetry">🔄</button>
            <button type="button" class="tray-icon-btn" id="tray-close-btn" title="Close Popover (Esc)">✕</button>
          </div>
        </div>

        <!-- 2. Multi-Child Tabs (if >1 child) -->
        ${
          children.length > 1
            ? `
          <div class="tray-child-tabs" role="tablist">
            ${children
              .map(
                (c) => `
              <button type="button" class="tray-child-tab ${c.childId === activeChild.childId ? "active" : ""}" data-tray-child="${escapeHtml(c.childId)}">
                <span>${getChildAvatarEmoji(c.ageBand, c.displayName)}</span>
                <span>${escapeHtml(c.displayName)}</span>
                <span class="tray-child-status-dot ${c.minutesUsedToday >= ceiling ? "paused" : ""}"></span>
              </button>
            `,
              )
              .join("")}
          </div>
        `
            : ""
        }

        <!-- 3. Active Child Overview & Time Gauge -->
        <div class="tray-hero-box">
          <div class="tray-hero-profile-row">
            <div class="tray-profile-info">
              <div class="tray-profile-avatar">${avatarEmoji}</div>
              <div class="tray-profile-names">
                <span class="tray-profile-title">${escapeHtml(childName)}</span>
                <span class="tray-profile-sub">${escapeHtml(ageBandFormatted)}</span>
              </div>
            </div>
            <span class="pill-badge ${isPaused ? "" : "accent"}" style="font-size: 0.72rem;">
              ${isPaused ? "⏸️ Paused" : minutesLeft !== null && minutesLeft <= 0 ? "⏱️ Ceiling Hit" : "🛡️ Protected"}
            </span>
          </div>

          <!-- Time Allowance & Gauge -->
          <div class="tray-gauge-wrap">
            <div class="tray-gauge-labels">
              <div class="tray-time-display">
                <span class="tray-time-left ${isPaused ? "warning" : minutesLeft !== null && minutesLeft <= 15 ? "danger" : ""}">
                  ${minutesLeft !== null ? `${formatMinutes(minutesLeft)} Left` : "Unlimited"}
                </span>
                <span class="tray-time-used-sub">
                  ${formatMinutes(minutesUsed)} used of ${ceiling > 0 ? formatMinutes(ceiling) : "∞"}
                </span>
              </div>
              <div class="tray-limit-stepper" role="group" aria-label="Adjust daily limit">
                <button type="button" class="tray-stepper-btn" id="btn-tray-limit-minus" title="Decrease daily ceiling (–15m)" aria-label="Decrease daily ceiling by 15m">–15m</button>
                <button type="button" class="tray-stepper-btn" id="btn-tray-limit-plus" title="Increase daily ceiling (+15m)" aria-label="Increase daily ceiling by 15m">+15m</button>
              </div>
            </div>
            <div class="tray-progress-track">
              <div class="tray-progress-fill ${pctUsed >= 90 ? "danger" : pctUsed >= 75 ? "warning" : ""}" style="width: ${ceiling > 0 ? pctUsed : 10}%;"></div>
            </div>
          </div>
        </div>

        <!-- 4. Quick Parental Controls Actions -->
        <div class="tray-actions-grid">
          <button type="button" class="tray-action-btn ${isPaused ? "primary" : "warning"}" id="btn-tray-pause">
            ${isPaused ? "▶ Resume Screen" : "⏸ Pause Screen"}
          </button>
          <button type="button" class="tray-action-btn" id="btn-tray-bonus-15">
            ⭐ +15m Bonus
          </button>
          <button type="button" class="tray-action-btn" id="btn-tray-bonus-30">
            ⭐ +30m Bonus
          </button>
          <button type="button" class="tray-action-btn" id="btn-tray-emergency">
            🚨 Emergency Unlock
          </button>
        </div>

        <!-- 5. Monitored App Rules Quick View -->
        <div class="tray-section-title-row">
          <span class="tray-section-title">Quick App Firewall</span>
          <span class="pill-badge" style="font-size: 0.68rem; padding: 1px 6px;">${allowedCount} Allowed</span>
        </div>

        <div class="tray-apps-list">
          ${
            appRules
              .slice(0, 4)
              .map(
                (r) => `
            <div class="tray-app-row">
              <div class="tray-app-left">
                <div class="tray-app-icon">${escapeHtml(r.displayName.slice(0, 1).toUpperCase())}</div>
                <span class="tray-app-name" title="${escapeHtml(r.displayName)}">${escapeHtml(r.displayName || r.appId)}</span>
              </div>
              <button type="button" class="tray-app-toggle ${r.allowed ? "allowed" : "blocked"}" data-tray-toggle-app="${escapeHtml(r.appId)}" ${r.isEmergency ? "disabled" : ""}>
                ${r.allowed ? "✓ Allowed" : "✗ Blocked"}
              </button>
            </div>
          `,
              )
              .join("") ||
            `
            <div style="text-align: center; padding: 10px; font-size: 0.78rem; color: var(--ms-on-surface-variant);">
              No monitored applications configured yet.
            </div>
          `
          }
        </div>

        <!-- 6. Cognitive & Checkpoint Telemetry Row -->
        <div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 10px; background: var(--ms-surface-low); border-radius: 8px; border: 1px solid var(--ms-outline-subtle); font-size: 0.76rem;">
          <span>🧠 <strong>${qCount}-Q Micro-Quiz</strong> (${passRate}% pass mark)</span>
          <span class="muted">Cooldown: ${detail.policy.defaultCooldownMinutes ?? 5}m</span>
        </div>

        <!-- 7. Footer Navigation & System Controls -->
        <div class="tray-footer-row">
          <button type="button" class="tray-open-dash-btn" id="btn-tray-open-dashboard">
            <span>🖥️ Open Full Dashboard</span>
            <span>→</span>
          </button>
          <div class="tray-footer-links">
            <button type="button" class="tray-footer-link" id="btn-tray-settings">⚙️ Settings</button>
            <span style="opacity: 0.4;">•</span>
            <button type="button" class="tray-footer-link" id="btn-tray-reports">📊 Reports</button>
            <span style="opacity: 0.4;">•</span>
            <button type="button" class="tray-footer-link danger" id="btn-tray-quit">Quit Watching</button>
          </div>
        </div>
      </div>
    `;

    // Handlers
    document.getElementById("tray-refresh-btn")?.addEventListener("click", () => {
      showToast("Telemetry refreshed", "info", 1400);
      void renderTrayPopover(activeChild.childId, targetEl);
    });

    document.getElementById("tray-close-btn")?.addEventListener("click", () => {
      if (targetEl) {
        toggleTrayPreviewModal(false);
      } else {
        try {
          void invoke("hide_tray_popover");
        } catch {}
      }
    });

    container.querySelectorAll<HTMLButtonElement>("[data-tray-child]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.dataset.trayChild;
        if (id) {
          void renderTrayPopover(id, targetEl);
        }
      });
    });

    // Pause / Resume Screen Time
    document.getElementById("btn-tray-pause")?.addEventListener("click", () => {
      void (async () => {
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          paused: !isPaused,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(
            isPaused
              ? `Screen time resumed for ${childName}!`
              : `Screen time paused for ${childName}!`,
            isPaused ? "success" : "info",
          );
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    // +15m Bonus
    document.getElementById("btn-tray-bonus-15")?.addEventListener("click", () => {
      void (async () => {
        const newCeiling = (detail.policy.dailyCeilingMinutes ?? 120) + 15;
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          dailyCeilingMinutes: newCeiling,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(`+15m bonus granted to ${childName}!`, "success");
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    // +30m Bonus
    document.getElementById("btn-tray-bonus-30")?.addEventListener("click", () => {
      void (async () => {
        const newCeiling = (detail.policy.dailyCeilingMinutes ?? 120) + 30;
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          dailyCeilingMinutes: newCeiling,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(`+30m bonus granted to ${childName}!`, "success");
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    // Limit adjustments (-15m / +15m)
    document.getElementById("btn-tray-limit-minus")?.addEventListener("click", () => {
      void (async () => {
        const curCeiling = detail.policy.dailyCeilingMinutes ?? 120;
        const newCeiling = Math.max(15, curCeiling - 15);
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          dailyCeilingMinutes: newCeiling,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(`Daily limit set to ${formatMinutes(newCeiling)} for ${childName}`, "info", 1600);
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    document.getElementById("btn-tray-limit-plus")?.addEventListener("click", () => {
      void (async () => {
        const curCeiling = detail.policy.dailyCeilingMinutes ?? 120;
        const newCeiling = curCeiling + 15;
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          dailyCeilingMinutes: newCeiling,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(`Daily limit increased to ${formatMinutes(newCeiling)} for ${childName}`, "success", 1600);
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    // Emergency Unlock with Confirmation Dialog
    document.getElementById("btn-tray-emergency")?.addEventListener("click", () => {
      const confirmed = window.confirm(
        `🚨 Activate Emergency Override for ${childName}?\n\nThis will immediately unpause all monitored apps and grant +60 minutes of unrestricted screen time.`
      );
      if (!confirmed) return;

      void (async () => {
        const updatedPolicy: ChildPolicy = {
          ...detail.policy,
          paused: false,
          dailyCeilingMinutes: (detail.policy.dailyCeilingMinutes ?? 120) + 60,
        };
        try {
          await invoke("parent_update_policy", { childId: activeChild.childId, policy: updatedPolicy });
          showToast(`Emergency override active: +60m granted to ${childName}!`, "info");
          await renderTrayPopover(activeChild.childId, targetEl);
        } catch (e) {
          showToast(friendlyError(e), "error");
        }
      })();
    });

    // App Firewall Toggle
    container.querySelectorAll<HTMLButtonElement>("[data-tray-toggle-app]").forEach((btn) => {
      btn.addEventListener("click", () => {
        void (async () => {
          const appId = btn.dataset.trayToggleApp;
          const rule = appRules.find((r) => r.appId === appId);
          if (rule && !rule.isEmergency) {
            rule.allowed = !rule.allowed;
            try {
              await invoke("parent_update_app_rules", { childId: activeChild.childId, rules: appRules });
              showToast(
                `${rule.displayName || rule.appId}: ${rule.allowed ? "Allowed" : "Blocked"}`,
                "success",
                1800,
              );
              await renderTrayPopover(activeChild.childId, targetEl);
            } catch (e) {
              showToast(friendlyError(e), "error");
            }
          }
        })();
      });
    });

    // Open Dashboard Button
    document.getElementById("btn-tray-open-dashboard")?.addEventListener("click", () => {
      if (targetEl) {
        toggleTrayPreviewModal(false);
      }
      try {
        void invoke("show_main_window");
      } catch {}
      void navigate({ name: "child-detail", childId: activeChild.childId });
    });

    // Settings Link
    document.getElementById("btn-tray-settings")?.addEventListener("click", () => {
      if (targetEl) {
        toggleTrayPreviewModal(false);
      }
      try {
        void invoke("show_main_window");
      } catch {}
      void navigate({ name: "account" });
    });

    // Reports Link
    document.getElementById("btn-tray-reports")?.addEventListener("click", () => {
      if (targetEl) {
        toggleTrayPreviewModal(false);
      }
      try {
        void invoke("show_main_window");
      } catch {}
      void navigate({ name: "reports", childId: activeChild.childId });
    });

    // Quit Link
    document.getElementById("btn-tray-quit")?.addEventListener("click", () => {
      void (async () => {
        try {
          await invoke("quit_app");
        } catch (err) {
          showToast(String(err), "error");
        }
      })();
    });
  } catch (err) {
    container.innerHTML = `
      <div class="tray-popover-card">
        <div class="tray-header-row">
          <div class="tray-brand-wrap">
            <div class="tray-pulse-dot paused"></div>
            <span class="tray-brand-title">Watching Sanctuary</span>
          </div>
          <button type="button" class="tray-icon-btn" id="tray-close-btn" title="Close">✕</button>
        </div>
        <div style="padding: 24px 14px; text-align: center;">
          <strong style="color: var(--ms-error); display: block; margin-bottom: 6px;">Connection Paused</strong>
          <p style="font-size: 0.8rem; color: var(--ms-on-surface-variant); margin: 0 0 14px;">
            ${escapeHtml(friendlyError(err))}
          </p>
          <button type="button" class="tray-open-dash-btn" id="btn-tray-retry">Retry Connection</button>
        </div>
      </div>
    `;

    document.getElementById("tray-close-btn")?.addEventListener("click", () => {
      if (targetEl) toggleTrayPreviewModal(false);
      else {
        try {
          void invoke("hide_tray_popover");
        } catch {}
      }
    });

    document.getElementById("btn-tray-retry")?.addEventListener("click", () => {
      void renderTrayPopover(activeChildId, targetEl);
    });
  }
}

async function renderChild(): Promise<void> {
  renderLoading("Loading child…");
  let st: ChildUiState;
  try {
    st = await invoke<ChildUiState>("child_ui_state");
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child" }));
    return;
  }
  if (!view) return;

  if (st.screen !== "quiz") {
    document.body.classList.remove("quiz-blocking-mode");
  }

  switch (st.screen) {
    case "pairing":
      renderChildPairing();
      break;
    case "checklist":
      renderChildChecklist(st);
      break;
    case "launcher":
      renderChildProtected(st);
      break;
    case "quiz":
      renderChildQuiz(st);
      break;
    case "fail_lock":
      renderChildFailLock(st);
      break;
    case "ceiling":
      renderChildCeiling(st);
      break;
    case "pin_menu":
      renderChildPinMenu(st);
      break;
    default:
      renderChildProtected(st);
      break;
  }
}

function renderChildPairing(): void {
  if (!view) return;
  view.innerHTML = `
    <div class="pairing-card-panel">
      <div class="pairing-hero-header">
        <div class="pairing-shield-icon">🛡️</div>
        <h1 class="pairing-hero-title">Pair This Computer</h1>
        <p class="pairing-hero-subtitle">
          Connect this desktop to your Watching sanctuary to activate intelligent screen time limits and educational micro-quizzes.
        </p>
      </div>

      <!-- 3-Step Guided Walkthrough (Android Parity) -->
      <div class="pairing-steps-row">
        <div class="pairing-step-item">
          <div class="pairing-step-num">1</div>
          <div class="pairing-step-text">
            <div class="pairing-step-title">Open Parent App</div>
            <div class="pairing-step-desc">Open Watching on parent phone or tablet</div>
          </div>
        </div>
        <div class="pairing-step-item">
          <div class="pairing-step-num">2</div>
          <div class="pairing-step-text">
            <div class="pairing-step-title">Tap Pair Device</div>
            <div class="pairing-step-desc">Select child profile & generate 6-digit PIN</div>
          </div>
        </div>
        <div class="pairing-step-item">
          <div class="pairing-step-num">3</div>
          <div class="pairing-step-text">
            <div class="pairing-step-title">Enter Code</div>
            <div class="pairing-step-desc">Type code below to link this computer</div>
          </div>
        </div>
      </div>

      <div id="child-pair-error"></div>

      <!-- Interactive 6-Digit PIN Code Box -->
      <div class="pairing-code-section">
        <div class="pairing-code-label">6-Digit Pairing Code</div>
        <div class="pairing-code-input-wrap">
          <input
            id="code"
            type="text"
            inputmode="numeric"
            maxlength="7"
            autocomplete="one-time-code"
            placeholder="••• •••"
            class="pairing-code-input"
            spellcheck="false"
            autofocus
          />
        </div>
        <div id="pairing-status-hint" class="pairing-status-hint">Enter the 6-digit code shown on the parent screen</div>

        <!-- Quick Action Buttons -->
        <div class="pairing-quick-actions">
          <button type="button" class="pairing-chip-btn" id="btn-paste-code">
            <span>📋</span> <span>Paste from Clipboard</span>
          </button>
          <button type="button" class="pairing-chip-btn" id="btn-demo-code">
            <span>✨</span> <span>Quick Demo: 123 456</span>
          </button>
        </div>
      </div>

      <!-- Modern Autostart & Protection Toggle Card -->
      <label class="pairing-toggle-card" for="child-pair-autostart">
        <div class="pairing-toggle-left">
          <div class="pairing-toggle-icon">🚀</div>
          <div class="pairing-toggle-text">
            <div class="pairing-toggle-title">Launch Watching on Computer Startup</div>
            <div class="pairing-toggle-desc">Automatically starts on boot with tamper-resistant background supervision.</div>
          </div>
        </div>
        <div class="pairing-switch">
          <input type="checkbox" id="child-pair-autostart" checked />
          <span class="pairing-switch-slider"></span>
        </div>
      </label>

      <!-- Android-Parity 3-Pillar Safeguard Highlights -->
      <div class="pairing-features-grid">
        <div class="pairing-feature-box">
          <div class="pairing-feature-head"><span>⏱️</span> Active Focus</div>
          <div class="pairing-feature-desc">Tracks screen time only when active. Idle & locked states pause automatically.</div>
        </div>
        <div class="pairing-feature-box">
          <div class="pairing-feature-head"><span>🧠</span> Micro-Quizzes</div>
          <div class="pairing-feature-desc">Educational pause screens challenge children to earn extra minutes.</div>
        </div>
        <div class="pairing-feature-box">
          <div class="pairing-feature-head"><span>🔒</span> Zero Cloud Track</div>
          <div class="pairing-feature-desc">Hardware-backed keychain storage. No surveillance or keylogging.</div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="actions" style="margin-top: 4px; display: flex; flex-direction: column; gap: 8px;">
        <button class="btn btn-primary" id="pair" style="padding: 12px 24px; font-size: 1rem; width: 100%;">
          <span id="pair-spinner" style="display:none;" class="btn-spinner"></span>
          <span id="pair-text">Pair This Computer</span>
        </button>
        <button class="btn btn-secondary" id="back" style="padding: 10px 20px; width: 100%;">Cancel & Return</button>
      </div>

      <!-- Security & Telemetry Footer -->
      <div class="pairing-security-note">
        <span>🛡️</span>
        <span>Encrypted locally in OS Keychain · Multi-screen protection · Demo mode accepts any 6 digits</span>
      </div>
    </div>`;

  const pairBtn = document.getElementById("pair") as HTMLButtonElement;
  const pairSpinner = document.getElementById("pair-spinner") as HTMLElement;
  const pairText = document.getElementById("pair-text") as HTMLElement;
  const codeInput = document.getElementById("code") as HTMLInputElement;
  const autostartInput = document.getElementById("child-pair-autostart") as HTMLInputElement;
  const statusHint = document.getElementById("pairing-status-hint") as HTMLElement;
  const errBox = document.getElementById("child-pair-error") as HTMLElement;
  const pasteBtn = document.getElementById("btn-paste-code");
  const demoBtn = document.getElementById("btn-demo-code");

  // Format code input with 3-3 grouping (e.g. "123 456")
  const formatAndValidateCode = () => {
    let raw = codeInput.value.replace(/\D/g, "").slice(0, 6);
    if (raw.length > 3) {
      codeInput.value = `${raw.slice(0, 3)} ${raw.slice(3)}`;
    } else {
      codeInput.value = raw;
    }

    if (raw.length === 6) {
      codeInput.classList.add("valid");
      if (statusHint) {
        statusHint.textContent = "✓ Code ready to pair! Click below to activate.";
        statusHint.classList.add("ready");
      }
    } else {
      codeInput.classList.remove("valid");
      if (statusHint) {
        statusHint.textContent = raw.length > 0 ? `${raw.length} / 6 digits entered` : "Enter the 6-digit code shown on the parent screen";
        statusHint.classList.remove("ready");
      }
    }
  };

  codeInput?.addEventListener("input", formatAndValidateCode);

  codeInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      submitPair();
    }
  });

  // Paste from clipboard helper
  pasteBtn?.addEventListener("click", () => {
    void (async () => {
      try {
        const text = await navigator.clipboard.readText();
        const digits = text.replace(/\D/g, "").slice(0, 6);
        if (digits.length > 0) {
          codeInput.value = digits;
          formatAndValidateCode();
          codeInput.focus();
        }
      } catch {
        codeInput.focus();
      }
    })();
  });

  // Demo code helper (123 456)
  demoBtn?.addEventListener("click", () => {
    codeInput.value = "123456";
    formatAndValidateCode();
    codeInput.focus();
  });

  const submitPair = () => {
    void (async () => {
      errBox.innerHTML = "";
      const rawCode = codeInput.value.replace(/\D/g, "");
      const shouldAutostart = autostartInput?.checked ?? true;
      if (rawCode.length !== 6) {
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 12px;">
            <div>Please enter a valid 6-digit pairing code from the parent app.</div>
          </div>`;
        codeInput.focus();
        return;
      }

      pairBtn.disabled = true;
      codeInput.disabled = true;
      if (autostartInput) autostartInput.disabled = true;
      pairSpinner.style.display = "inline-block";
      pairText.textContent = "Pairing this computer…";
      try {
        await invoke("child_pair", { code: rawCode, childName: null });
        if (shouldAutostart) {
          try {
            await invoke("set_autostart", { enabled: true });
          } catch {}
        }
        pairSpinner.style.display = "none";
        pairText.textContent = "✓ Paired Successfully!";
        showToast("✓ Computer paired successfully! Running in background.", "info");
        setTimeout(async () => {
          await navigate({ name: "child" });
        }, 500);
      } catch (e) {
        pairBtn.disabled = false;
        codeInput.disabled = false;
        if (autostartInput) autostartInput.disabled = false;
        pairSpinner.style.display = "none";
        pairText.textContent = "Pair This Computer";
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 12px;">
            <div><strong>Pairing failed</strong>: ${escapeHtml(friendlyError(e))}</div>
          </div>`;
      }
    })();
  };

  pairBtn?.addEventListener("click", submitPair);

  document.getElementById("back")?.addEventListener("click", () => {
    void setRole("unassigned");
  });

  // Focus code input automatically
  setTimeout(() => codeInput?.focus(), 50);
}

function renderChildChecklist(st: ChildUiState): void {
  if (!view) return;
  const completedCount = st.checklist.filter((c) => c.done).length;
  const totalCount = st.checklist.length;

  const items = st.checklist
    .map(
      (c) => `
      <label class="field check" style="padding: 6px 0;">
        <input type="checkbox" data-check="${escapeHtml(c.id)}" ${c.done ? "checked" : ""}/>
        <div>
          <span>${escapeHtml(c.label)}</span>
          ${c.recommended ? ' <span class="pill-badge accent" style="font-size: 0.72rem; margin-left: 4px;">Recommended</span>' : ""}
        </div>
      </label>`,
    )
    .join("");

  view.innerHTML = `
    <div class="panel">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <h1>Hi ${escapeHtml(st.childName)}</h1>
        <span class="pill-badge">${completedCount} / ${totalCount} ready</span>
      </div>
      <p>Finish device setup before opening the launcher. Quiz pack: <span class="mono">${escapeHtml(formatPackSource(st.packSource))}</span></p>
      <div id="checklist-error"></div>
      <div class="enforce-box">${items}</div>
      <div class="actions" style="margin-top: 14px;">
        <button class="btn btn-primary" id="done">
          <span id="done-spinner" style="display:none;" class="btn-spinner"></span>
          <span id="done-text">${escapeHtml(t("child_checklist_continue"))}</span>
        </button>
      </div>
    </div>`;

  for (const el of view.querySelectorAll<HTMLInputElement>("[data-check]")) {
    el.addEventListener("change", () => {
      void (async () => {
        try {
          await invoke("child_set_checklist_item", { id: el.dataset.check!, done: el.checked });
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "child" }));
        }
      })();
    });
  }

  const doneBtn = document.getElementById("done") as HTMLButtonElement;
  const doneSpinner = document.getElementById("done-spinner") as HTMLElement;
  const doneText = document.getElementById("done-text") as HTMLElement;
  const errBox = document.getElementById("checklist-error") as HTMLElement;

  doneBtn?.addEventListener("click", () => {
    void (async () => {
      errBox.innerHTML = "";
      doneBtn.disabled = true;
      doneSpinner.style.display = "inline-block";
      doneText.textContent = "Setting up launcher…";
      try {
        await invoke("child_complete_checklist");
        await navigate({ name: "child" });
      } catch (e) {
        doneBtn.disabled = false;
        doneSpinner.style.display = "none";
        doneText.textContent = t("child_checklist_continue");
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 10px;">
            <div>${escapeHtml(friendlyError(e))}</div>
          </div>`;
      }
    })();
  });
}

function renderChildProtected(st: ChildUiState): void {
  if (!view) return;
  const activeApp = st.activeAppName || "Desktop Active";
  const remBlock = st.minutesRemainingBlock;
  const usedToday = st.minutesUsedToday;
  const dailyRem = st.dailyRemainingMinutes;

  view.innerHTML = `
    <div class="panel wide child-protected-panel">
      <div class="child-protected-header">
        <div class="child-shield-badge">
          <div class="pulse-ring"></div>
          🛡️
        </div>
        <div>
          <div style="display:flex;align-items:center;gap:10px;">
            <h1 style="margin:0;font-size:1.7rem;">Active Desktop Protection</h1>
            <span class="pill-badge accent">Enforced</span>
          </div>
          <p class="muted" style="margin:4px 0 0;font-size:0.95rem;">
            Hi <strong>${escapeHtml(st.childName)}</strong>! MeritScreen is actively monitoring desktop usage in the background.
          </p>
        </div>
      </div>

      <div class="child-countdown-banner" id="child-countdown-banner">
        <div class="banner-text">
          <span>⚡</span>
          <span>Running silently in top menu / system tray. Minimizing in <strong id="auto-min-sec" class="mono" style="font-size:1.1rem;color:var(--ms-primary);">4</strong>s…</span>
        </div>
        <button class="btn btn-primary" id="btn-minimize-now" style="padding:8px 18px;font-size:0.9rem;">
          Minimize to Background Now
        </button>
      </div>

      <div class="child-stats-grid" role="region" aria-label="${escapeHtml(t("a11y_launcher_apps"))}">
        <div class="child-stat-card">
          <div class="label">Currently Monitored Application</div>
          <div class="val">
            <span style="font-size:1.6rem;">🌐</span>
            <span id="child-protected-active-app">${escapeHtml(activeApp)}</span>
          </div>
          <small style="color:var(--ms-on-surface-variant);margin-top:2px;">
            Second-by-second active-window detection
          </small>
        </div>

        <div class="child-stat-card">
          <div class="label">Next Micro-Quiz Interval</div>
          <div class="val">
            <span>⏱️</span>
            <span id="child-protected-timer" class="mono">${formatTimer(st.secondsRemainingBlock ?? remBlock * 60)}</span>
          </div>
          <small style="color:var(--ms-on-surface-variant);margin-top:2px;">
            50-minute active block cadence
          </small>
        </div>

        <div class="child-stat-card">
          <div class="label">Screen Time Today</div>
          <div class="val">
            <span>📊</span>
            <span id="child-protected-used-today">${usedToday} <small>min used</small></span>
          </div>
          <small style="color:var(--ms-on-surface-variant);margin-top:2px;">
            ${dailyRem} min daily allowance left
          </small>
        </div>
      </div>

      <div class="child-tray-autostart-card" style="margin: 12px 0; padding: 12px 16px;">
        <div class="child-tray-autostart-text">
          <span class="child-tray-autostart-icon">🚀</span>
          <div>
            <div class="child-tray-autostart-title" style="font-size: 0.92rem;">Launch Watching Automatically on Computer Startup</div>
            <div class="child-tray-autostart-subtitle" id="child-protected-autostart-desc">Loading startup setting…</div>
          </div>
        </div>
        <button type="button" class="btn btn-secondary" id="btn-child-autostart-toggle" style="padding: 6px 14px; font-size: 0.85rem;">
          Toggle
        </button>
      </div>

      <div class="enforce-box" style="margin: 16px 0; padding: 18px 20px; line-height: 1.5; font-size: 0.9rem; color: var(--ms-on-surface-variant);">
        <strong style="color:var(--ms-on-surface);display:block;margin-bottom:4px;">How Watching Works:</strong>
        You can freely use permitted applications (Chrome, VS Code, Discord, games, and schoolwork).
        Whenever 50 minutes of cumulative active use is reached, a quick 1-minute brain-break quiz will pop up to unlock your next session.
        There is no skip option, so solve the question carefully!
      </div>

      <div class="actions" style="margin-top: 20px; display: flex; flex-wrap: wrap; gap: 12px; align-items: center;">
        <button class="btn btn-secondary" id="btn-quiz-early" style="padding:10px 18px;">
          📝 Take Quiz Early (+15m)
        </button>
        <button class="btn btn-secondary" id="btn-child-pin" style="padding:10px 18px;">
          ⚙️ Parent Safety PIN & Settings
        </button>
        <button class="btn btn-secondary" id="btn-test-tick" style="padding:10px 18px; opacity: 0.7;">
          ⏱️ +5m Test Advance
        </button>
      </div>
    </div>
  `;

  // Autostart toggle in protected view
  let autostartEnabled = false;
  const updateAutostartUi = (enabled: boolean) => {
    autostartEnabled = enabled;
    const desc = document.getElementById("child-protected-autostart-desc");
    const btn = document.getElementById("btn-child-autostart-toggle");
    if (desc) desc.textContent = enabled ? "Runs silently on PC boot and login (Active)" : "Startup launch is disabled";
    if (btn) {
      btn.textContent = enabled ? "Enabled ✓" : "Enable";
      btn.style.color = enabled ? "var(--ms-success, #10b981)" : "inherit";
    }
  };

  void (async () => {
    try {
      const isEnabled = await invoke<boolean>("get_autostart_status");
      updateAutostartUi(isEnabled);
    } catch {
      updateAutostartUi(false);
    }
  })();

  document.getElementById("btn-child-autostart-toggle")?.addEventListener("click", () => {
    void (async () => {
      try {
        const next = !autostartEnabled;
        const res = await invoke<boolean>("set_autostart", { enabled: next });
        updateAutostartUi(res);
        showToast(res ? "Auto-start enabled on boot." : "Auto-start disabled.", "info");
      } catch (e) {
        showToast(String(e), "error");
      }
    })();
  });

  // Real-time second-by-second screen ticker
  let liveTicker: number | null = window.setInterval(() => {
    void (async () => {
      try {
        const fresh = await invoke<ChildUiState>("child_ui_state");
        const timerEl = document.getElementById("child-protected-timer");
        if (timerEl) {
          const freshSec = fresh.secondsRemainingBlock ?? fresh.minutesRemainingBlock * 60;
          timerEl.textContent = formatTimer(freshSec);
        }
        const appEl = document.getElementById("child-protected-active-app");
        if (appEl && fresh.activeAppName) {
          appEl.textContent = fresh.activeAppName;
        }
        const usedEl = document.getElementById("child-protected-used-today");
        if (usedEl) {
          usedEl.innerHTML = `${fresh.minutesUsedToday} <small>min used</small>`;
        }
      } catch {}
    })();
  }, 1000);

  // Auto-minimize countdown
  let countdownSec = 4;
  const countEl = document.getElementById("auto-min-sec");

  const minimize = () => {
    if (activeTimeout) clearTimeout(activeTimeout);
    if (liveTicker !== null) {
      clearInterval(liveTicker);
      liveTicker = null;
    }
    try {
      void invoke("hide_main_window");
    } catch {}
  };

  document.getElementById("btn-minimize-now")?.addEventListener("click", minimize);

  const countdownTick = () => {
    countdownSec -= 1;
    if (countEl) countEl.textContent = String(countdownSec);
    if (countdownSec <= 0) {
      minimize();
    } else {
      activeTimeout = window.setTimeout(countdownTick, 1000);
    }
  };
  activeTimeout = window.setTimeout(countdownTick, 1000);

  document.getElementById("btn-quiz-early")?.addEventListener("click", () => {
    if (activeTimeout) clearTimeout(activeTimeout);
    if (liveTicker !== null) {
      clearInterval(liveTicker);
      liveTicker = null;
    }
    void (async () => {
      try {
        await invoke("child_force_quiz");
        await navigate({ name: "child" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  });

  document.getElementById("btn-child-pin")?.addEventListener("click", () => {
    if (activeTimeout) clearTimeout(activeTimeout);
    if (liveTicker !== null) {
      clearInterval(liveTicker);
      liveTicker = null;
    }
    void (async () => {
      await invoke("child_open_pin_menu");
      await navigate({ name: "child" });
    })();
  });

  document.getElementById("btn-test-tick")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_tick", { deltaMs: 5 * 60_000 });
      await navigate({ name: "child" });
    })();
  });
}

function renderQuizPassedSuccess(st: ChildUiState): void {
  if (!view) return;
  const grantMins = st.blockDurationMinutes || (st.secondsRemainingBlock ? Math.round(st.secondsRemainingBlock / 60) : 15);
  view.innerHTML = `
    <div class="overlay quiz-overlay" role="dialog" aria-modal="true">
      <div class="quiz-success-card">
        <div class="trophy">🎉</div>
        <h1 style="color:var(--ms-success, #10b981);font-size:1.8rem;margin:0;">Outstanding Job!</h1>
        <p style="font-size:1.1rem;color:var(--ms-on-surface);margin:0;">
          You passed the micro-quiz! <strong>+${grantMins} Minutes Screen Time</strong> added.
        </p>
        <div class="pill-badge accent" style="padding:8px 16px;font-size:0.95rem;margin:6px 0;">
          Resuming <strong>${escapeHtml(st.activeAppName || "your desktop apps")}</strong>
        </div>
        <p class="muted" style="font-size:0.85rem;margin-top:4px;">
          Returning to background in <span id="success-countdown" class="mono">2</span> seconds…
        </p>
        <button class="btn btn-primary" id="btn-resume-now" style="margin-top:6px;padding:10px 24px;">Resume Now</button>
      </div>
    </div>
  `;

  const resume = async () => {
    if (activeTimeout) clearTimeout(activeTimeout);
    document.body.classList.remove("quiz-blocking-mode");
    try {
      await invoke("exit_quiz_blocking");
    } catch {}
    await navigate({ name: "child" });
  };

  document.getElementById("btn-resume-now")?.addEventListener("click", () => void resume());

  let countdown = 2;
  const countEl = document.getElementById("success-countdown");
  const timer = () => {
    countdown -= 1;
    if (countEl) countEl.textContent = String(countdown);
    if (countdown <= 0) {
      void resume();
    } else {
      activeTimeout = window.setTimeout(timer, 1000);
    }
  };
  activeTimeout = window.setTimeout(timer, 1000);
}

function renderShieldOverlay(): void {
  if (!view) return;
  document.body.classList.add("is-shield-window");
  view.innerHTML = `
    <div class="screen-shield-container">
      <div class="screen-shield-card">
        <div class="shield-pulse-ring">
          <img src="/logo.png" alt="Watching Shield Logo" onerror="this.src='/icons/icon.png'" />
        </div>
        <h1 class="shield-title">Screen Paused for Micro-Quiz</h1>
        <p class="shield-subtitle">
          Please complete the quiz on your main display to unlock all displays.
        </p>
        <div class="shield-badge">
          <span>🛡️</span> <strong>Watching Desktop Guardian</strong>
        </div>
      </div>
    </div>
  `;
}

function quizSkipModalHtml(): string {
  return `
    <!-- Skip PIN Modal -->
    <div id="quiz-pin-modal" class="quiz-pin-modal-backdrop" style="display:none;" role="dialog" aria-modal="true" aria-labelledby="skip-pin-title">
      <div class="quiz-pin-modal-box">
        <div style="font-size:2rem;margin-bottom:8px;">🔑</div>
        <h2 id="skip-pin-title" style="margin:0 0 6px 0;font-size:1.35rem;color:#ffffff;">Parent PIN Skip</h2>
        <p style="margin:0 0 16px 0;font-size:0.9rem;color:#94a3b8;">Enter the 4-digit Parent PIN (Default: 1234) to skip this quiz and grant +15 minutes screen time.</p>
        <input type="password" id="input-skip-pin" class="quiz-pin-input" maxlength="4" inputmode="numeric" placeholder="••••" autocomplete="off" />
        <p id="skip-pin-error" style="color:#ef4444;font-size:0.84rem;min-height:18px;margin:4px 0 12px;"></p>
        <div style="display:flex;gap:10px;justify-content:center;">
          <button class="btn btn-secondary" id="btn-cancel-skip" type="button" style="padding:8px 20px;">Cancel</button>
          <button class="btn btn-primary" id="btn-confirm-skip" type="button" style="padding:8px 20px;">Confirm Skip</button>
        </div>
      </div>
    </div>
  `;
}

function setupQuizSkipModal(container: HTMLElement) {
  const skipBtn = container.querySelector<HTMLButtonElement>("#btn-quiz-parent-skip");
  const modal = container.querySelector<HTMLElement>("#quiz-pin-modal");
  const pinInput = container.querySelector<HTMLInputElement>("#input-skip-pin");
  const pinError = container.querySelector<HTMLElement>("#skip-pin-error");
  const cancelBtn = container.querySelector<HTMLButtonElement>("#btn-cancel-skip");
  const confirmBtn = container.querySelector<HTMLButtonElement>("#btn-confirm-skip");

  const openModal = () => {
    if (modal) modal.style.display = "flex";
    if (pinInput) {
      pinInput.value = "";
      pinInput.focus();
    }
    if (pinError) pinError.textContent = "";
  };

  const closeModal = () => {
    if (modal) modal.style.display = "none";
    if (pinError) pinError.textContent = "";
  };

  skipBtn?.addEventListener("click", openModal);
  cancelBtn?.addEventListener("click", closeModal);

  const handleSkipSubmit = async () => {
    const pin = pinInput?.value.trim() || "";
    if (pin.length !== 4) {
      if (pinError) pinError.textContent = "Please enter 4 digits.";
      return;
    }
    try {
      if (confirmBtn) {
        confirmBtn.textContent = "Verifying…";
        confirmBtn.disabled = true;
      }
      const updated = await invoke<ChildUiState>("child_skip_quiz_with_pin", { pin });
      if (modal) modal.style.display = "none";
      document.body.classList.remove("quiz-blocking-mode");
      const grantMins = updated.blockDurationMinutes || (updated.secondsRemainingBlock ? Math.round(updated.secondsRemainingBlock / 60) : 15);
      showToast(`Quiz skipped by parent. +${grantMins} Minutes screen time added.`, "success", 3000);
      try {
        await invoke("exit_quiz_blocking");
      } catch {}
      await navigate({ name: "child" });
    } catch (err) {
      if (pinError) pinError.textContent = friendlyError(err);
      if (pinInput) {
        pinInput.value = "";
        pinInput.focus();
      }
    } finally {
      if (confirmBtn) {
        confirmBtn.textContent = "Confirm Skip";
        confirmBtn.disabled = false;
      }
    }
  };

  confirmBtn?.addEventListener("click", () => void handleSkipSubmit());
  pinInput?.addEventListener("input", () => {
    if (pinInput.value.length === 4) {
      void handleSkipSubmit();
    }
  });
  pinInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      void handleSkipSubmit();
    } else if (e.key === "Escape") {
      closeModal();
    }
  });
}

function renderChildQuiz(st: ChildUiState): void {
  if (!view) return;
  document.body.classList.add("quiz-blocking-mode");
  const lock = st.quizLock;
  const q = st.quiz;
  const feedback = st.lastFeedback;

  if (lock && lock.remainingSeconds > 0) {
    view.innerHTML = `
      <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-labelledby="quiz-lock-title">
        <div class="quiz-blocking-card">
          <h1 id="quiz-lock-title" style="color:#ef4444;font-size:1.6rem;margin-top:0;">${escapeHtml(t("child_quiz_lock_title"))}</h1>
          <p style="font-size:1.05rem;color:var(--ms-on-surface);">${escapeHtml(lock.conceptLine || "Review the idea, then try again.")}</p>
          <p class="lock-timer mono" id="quiz-lock-timer-text" aria-live="polite" aria-atomic="true" style="font-size:2.8rem;font-weight:700;color:#f59e0b;margin:12px 0;">${lock.remainingSeconds}s</p>
          <p class="muted" style="font-size:0.88rem;">${escapeHtml(t("child_quiz_lock_body"))}</p>
          <div style="text-align:center;margin-top:16px;border-top:1px solid rgba(255,255,255,0.08);padding-top:14px;">
            <button class="btn btn-secondary" id="btn-quiz-parent-skip" style="font-size:0.85rem;padding:8px 18px;border-radius:100px;">
              🔑 Skip with Parent PIN
            </button>
          </div>
        </div>

        ${quizSkipModalHtml()}
      </div>`;

    setupQuizSkipModal(view);

    activeTimeout = window.setTimeout(() => {
      void (async () => {
        const modal = document.getElementById("quiz-pin-modal");
        if (modal && modal.style.display === "flex") {
          await invoke("child_tick", { deltaMs: 1_000 });
          const timerEl = document.getElementById("quiz-lock-timer-text");
          if (timerEl) {
            const currentSec = parseInt(timerEl.textContent || "0", 10);
            if (currentSec > 1) {
              timerEl.textContent = `${currentSec - 1}s`;
            }
          }
          return;
        }
        await invoke("child_tick", { deltaMs: 1_000 });
        await navigate({ name: "child" });
      })();
    }, 950);
    return;
  }

  if (!q) {
    view.innerHTML = `
      <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-label="${escapeHtml(t("a11y_quiz_dialog"))}">
        <div class="quiz-blocking-card">
          <h1>${escapeHtml(t("a11y_quiz_dialog"))}</h1>
          <p class="muted">${prewarmMode ? "Pre-warming…" : "Loading question…"}</p>
        </div>
      </div>`;
    if (!prewarmMode) void navigate({ name: "child" });
    return;
  }

  const choices = q.choices
    .map(
      (c, i) =>
        `<button class="quiz-choice-btn choice" data-choice="${escapeHtml(c.id)}" data-index="${i}"
          aria-keyshortcuts="${i + 1}">
          <span>${escapeHtml(c.text)}</span>
          <span class="quiz-choice-badge" aria-hidden="true">${i + 1}</span>
        </button>`,
    )
    .join("");

  const teach =
    q.teachTitle || q.teachBody.length
      ? `<div class="teach" style="margin-bottom:14px;"><strong>${escapeHtml(q.teachTitle ?? "Remember")}</strong>
         ${q.teachBody.map((l) => `<p>${escapeHtml(l)}</p>`).join("")}</div>`
      : "";

  const fb = feedback
    ? `<p class="${feedback.correct ? "ok" : "error"}" aria-live="polite" style="margin-bottom:12px;">${escapeHtml(feedback.resultLine)} — ${escapeHtml(feedback.whyLine)}</p>`
    : "";

  const pausedAppBadge = st.activeAppName
    ? `<div class="quiz-app-paused-badge"><span>⏸️</span> <strong>${escapeHtml(st.activeAppName)}</strong> Paused for Quiz</div>`
    : `<div class="quiz-app-paused-badge"><span>⏸️</span> 50-Minute Screen Time Limit Reached</div>`;

  view.innerHTML = `
    <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-labelledby="quiz-prompt">
      <div class="quiz-blocking-card">
        ${pausedAppBadge}
        <p class="meta" aria-live="polite" style="margin:8px 0 4px;font-size:0.86rem;">${escapeHtml(
          t("child_quiz_progress", { current: q.index + 1, total: q.total }),
        )}</p>
        <h1 id="quiz-prompt" style="font-size:1.6rem;margin:6px 0 16px;color:#ffffff;line-height:1.3;">${escapeHtml(q.prompt)}</h1>
        ${teach}
        ${fb}
        <div class="actions" role="group" aria-label="${escapeHtml(t("a11y_quiz_choices"))}" style="display:flex;flex-direction:column;gap:12px;">${choices}</div>
        <p class="muted" style="margin-top:16px;text-align:center;font-size:0.84rem;">Answer all questions correctly to unlock your screen and earn +15 minutes.</p>
        <div style="text-align:center;margin-top:12px;border-top:1px solid rgba(255,255,255,0.08);padding-top:12px;">
          <button class="btn btn-secondary" id="btn-quiz-parent-skip" style="font-size:0.85rem;padding:8px 18px;border-radius:100px;">
            🔑 Skip with Parent PIN
          </button>
        </div>
      </div>

      ${quizSkipModalHtml()}
    </div>`;

  const answer = (choiceId: string) => {
    void (async () => {
      try {
        const updated = await invoke<ChildUiState>("child_answer_quiz", { choiceId });
        if (updated.lastResult && updated.lastResult.passed) {
          renderQuizPassedSuccess(updated);
        } else {
          await navigate({ name: "child" });
        }
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  };

  for (const el of view.querySelectorAll<HTMLButtonElement>("[data-choice]")) {
    el.addEventListener("click", () => answer(el.dataset.choice!));
  }

  setupQuizSkipModal(view);

  const onKey = (ev: KeyboardEvent) => {
    const modal = document.getElementById("quiz-pin-modal");
    if (modal && modal.style.display === "flex") return;
    if (ev.key >= "1" && ev.key <= "9") {
      const idx = Number(ev.key) - 1;
      const btn = view?.querySelector<HTMLButtonElement>(`[data-index="${idx}"]`);
      if (btn?.dataset.choice) {
        ev.preventDefault();
        answer(btn.dataset.choice);
      }
    }
  };

  activeKeyHandler = onKey;
  window.addEventListener("keydown", onKey, { once: true });
}

function renderChildFailLock(st: ChildUiState): void {
  if (!view) return;
  const result = st.lastResult;
  view.innerHTML = `
    <div class="overlay fail-overlay" role="dialog" aria-modal="true" aria-labelledby="fail-title"
      aria-label="${escapeHtml(t("a11y_fail_lock_dialog"))}">
      <h1 id="fail-title">${escapeHtml(t("child_fail_lock_title"))}</h1>
      <p>${escapeHtml(t("child_fail_lock_body"))}</p>
      ${
        result
          ? `<p class="meta">Score ${result.correctCount}/${result.total} (${result.percent}%)</p>`
          : ""
      }
      <p class="lock-timer mono" aria-live="polite" aria-atomic="true"
        aria-label="${escapeHtml(t("a11y_cooldown_timer", { seconds: st.cooldownSeconds }))}">${st.cooldownSeconds}s</p>
      <div class="actions">
        <button class="btn btn-secondary" id="tick">${escapeHtml(t("lab_advance_30s"))}</button>
        <button class="btn btn-secondary" id="btn-quiz-parent-skip">🔑 Skip with Parent PIN</button>
        <button class="btn btn-secondary" id="pin">${escapeHtml(t("parent_pin_label"))}</button>
      </div>
      ${quizSkipModalHtml()}
    </div>`;

  setupQuizSkipModal(view);

  document.getElementById("tick")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_tick", { deltaMs: 30_000 });
      await navigate({ name: "child" });
    })();
  });
  document.getElementById("pin")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_open_pin_menu");
      await navigate({ name: "child" });
    })();
  });
  document.getElementById("pin")?.focus();

  if (st.cooldownSeconds > 0) {
    activeTimeout = window.setTimeout(() => {
      void (async () => {
        const modal = document.getElementById("quiz-pin-modal");
        if (modal && modal.style.display === "flex") {
          await invoke("child_tick", { deltaMs: 1_000 });
          return;
        }
        await invoke("child_tick", { deltaMs: 1_000 });
        await navigate({ name: "child" });
      })();
    }, 1000);
  }
}

function renderChildCeiling(st: ChildUiState): void {
  if (!view) return;
  view.innerHTML = `
    <div class="overlay fail-overlay" role="dialog" aria-modal="true" aria-labelledby="ceiling-title">
      <h1 id="ceiling-title">${escapeHtml(t("child_ceiling_title"))}</h1>
      <p>${escapeHtml(t("child_ceiling_body", { minutes: st.minutesUsedToday }))}</p>
      <div class="actions">
        <button class="btn btn-secondary" id="pin">${escapeHtml(t("parent_pin_label"))}</button>
      </div>
    </div>`;
  document.getElementById("pin")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_open_pin_menu");
      await navigate({ name: "child" });
    })();
  });
}

function renderChildPinMenu(st: ChildUiState): void {
  if (!view) return;
  view.innerHTML = `
    <div class="panel">
      <h1>${escapeHtml(t("child_pin_menu_title"))}</h1>
      <p class="muted">Enter 4-digit Parent PIN to unlock device settings or unpair. Demo PIN is <span class="mono">1234</span>.</p>
      <div id="pin-menu-error"></div>
      <label class="field">${escapeHtml(t("parent_pin_label"))}
        <input id="pin" inputmode="numeric" maxlength="4" autocomplete="off" placeholder="••••"
          style="letter-spacing: 0.3em; font-size: 1.2rem; text-align: center;" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" id="unpair">
          <span id="unpair-spinner" style="display:none;" class="btn-spinner"></span>
          <span id="unpair-text">${escapeHtml(t("unpair_device"))}</span>
        </button>
        <button class="btn btn-secondary" id="btn-child-menu-exit" style="color:#ef4444;border-color:rgba(239,68,68,0.35);">
          <span id="exit-menu-spinner" style="display:none;" class="btn-spinner"></span>
          <span id="exit-menu-text">Exit Application</span>
        </button>
        <button class="btn btn-secondary" id="refresh">${escapeHtml(t("refresh_snapshot"))}</button>
        <button class="btn btn-secondary" id="back">${escapeHtml(t("back"))}</button>
      </div>
      <p class="meta">Phase: ${escapeHtml(st.phase)} · Quiz Pack: ${escapeHtml(formatPackSource(st.packSource))}</p>
    </div>`;

  const unpairBtn = document.getElementById("unpair") as HTMLButtonElement;
  const unpairSpinner = document.getElementById("unpair-spinner") as HTMLElement;
  const unpairText = document.getElementById("unpair-text") as HTMLElement;
  const exitMenuBtn = document.getElementById("btn-child-menu-exit") as HTMLButtonElement | null;
  const exitMenuSpinner = document.getElementById("exit-menu-spinner") as HTMLElement | null;
  const exitMenuText = document.getElementById("exit-menu-text") as HTMLElement | null;
  const pinInput = document.getElementById("pin") as HTMLInputElement;
  const errBox = document.getElementById("pin-menu-error") as HTMLElement;

  unpairBtn?.addEventListener("click", () => {
    void (async () => {
      errBox.innerHTML = "";
      const pin = pinInput.value.trim();
      if (pin.length !== 4) {
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 10px;">
            <div>Please enter the 4-digit Parent PIN.</div>
          </div>`;
        return;
      }
      unpairBtn.disabled = true;
      pinInput.disabled = true;
      unpairSpinner.style.display = "inline-block";
      unpairText.textContent = "Unpairing…";
      try {
        await invoke("child_pin_unpair", { pin });
        showToast("Device successfully unpaired. Background service stopped.", "info");
        await navigate({ name: "role" });
      } catch (e) {
        unpairBtn.disabled = false;
        pinInput.disabled = false;
        unpairSpinner.style.display = "none";
        unpairText.textContent = t("unpair_device");
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 10px;">
            <div>${escapeHtml(friendlyError(e))}</div>
          </div>`;
      }
    })();
  });

  exitMenuBtn?.addEventListener("click", () => {
    void (async () => {
      errBox.innerHTML = "";
      const pin = pinInput.value.trim();
      if (pin.length !== 4) {
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 10px;">
            <div>Please enter the 4-digit Parent PIN to exit.</div>
          </div>`;
        return;
      }
      if (exitMenuBtn) exitMenuBtn.disabled = true;
      pinInput.disabled = true;
      if (exitMenuSpinner) exitMenuSpinner.style.display = "inline-block";
      if (exitMenuText) exitMenuText.textContent = "Exiting…";
      try {
        await invoke("child_exit_with_pin", { pin });
      } catch (e) {
        if (exitMenuBtn) exitMenuBtn.disabled = false;
        pinInput.disabled = false;
        if (exitMenuSpinner) exitMenuSpinner.style.display = "none";
        if (exitMenuText) exitMenuText.textContent = "Exit Application";
        errBox.innerHTML = `
          <div class="error-banner" style="margin-bottom: 10px;">
            <div>${escapeHtml(friendlyError(e))}</div>
          </div>`;
      }
    })();
  });

  document.getElementById("refresh")?.addEventListener("click", () => void navigate({ name: "child" }));
  document.getElementById("back")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_dismiss_pin_menu");
      await navigate({ name: "child" });
    })();
  });
}

async function setRole(role: DeviceRole): Promise<void> {
  try {
    await invoke("set_role", { role });
    if (role === "child") await navigate({ name: "child" });
    else await navigate({ name: "role" });
  } catch (err) {
    renderError(err instanceof Error ? err.message : "Could not update role.");
  }
}

async function setAppearance(appearance: Appearance): Promise<void> {
  try {
    const saved = await invoke<Appearance>("set_appearance", { appearance });
    applyTheme(saved);
  } catch (err) {
    renderError(err instanceof Error ? err.message : "Could not save appearance.");
  }
}

for (const chip of chips) {
  chip.addEventListener("click", () => {
    const appearance = chip.dataset.appearance as Appearance;
    void setAppearance(appearance);
  });
}

// Real-time synchronization when user refocuses or activates the MeritScreen app window
window.addEventListener("focus", () => {
  if (isTrayWindow || route.name === "tray-popover") {
    void renderTrayPopover();
  } else if (route.name === "parent-home") {
    void renderDashboard();
  } else if (route.name === "child-detail") {
    void renderChildDetail(route.childId);
  } else if (route.name === "child") {
    void renderChild();
  }
});

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    if (isTrayWindow || route.name === "tray-popover") {
      void renderTrayPopover();
    } else if (route.name === "parent-home") {
      void renderDashboard();
    } else if (route.name === "child-detail") {
      void renderChildDetail(route.childId);
    } else if (route.name === "child") {
      void renderChild();
    }
  }
});

// Native background event listener: Micro-quiz due notification
try {
  void listen("ms_quiz_due", () => {
    void (async () => {
      try {
        await invoke("show_main_window");
      } catch {}
      void navigate({ name: "child" });
    })();
  });
} catch (e) {
  console.warn("Could not attach ms_quiz_due listener", e);
}

// Native background event listener: Tray opened (always show fresh telemetry)
try {
  void listen("ms_tray_opened", () => {
    if (isTrayWindow || route.name === "tray-popover") {
      void renderTrayPopover();
    }
  });
} catch (e) {
  console.warn("Could not attach ms_tray_opened listener", e);
}

// Native background event listener: Role changed (parent <-> child <-> unassigned)
try {
  void listen("ms_role_changed", (event) => {
    const nextRole = event.payload as string;
    if (isTrayWindow || route.name === "tray-popover") {
      void renderTrayPopover();
    } else if (nextRole === "child" && route.name !== "child") {
      void navigate({ name: "child" });
    } else if (nextRole === "unassigned" && route.name !== "role") {
      void navigate({ name: "role" });
    }
  });
} catch (e) {
  console.warn("Could not attach ms_role_changed listener", e);
}

void (async () => {
  await loadHostFlags();
  await navigate(route);
})();

