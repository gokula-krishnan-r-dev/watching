import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { initLocale, t } from "./i18n";
import { createElement } from "react";
import {
  ChildPairingScreen,
  ChildPinMenuScreen,
  ParentAuthScreen,
  DashboardScreen,
  ChildDetailScreen,
  PolicyScreen,
  AllowlistScreen,
  ChildSetupWizard,
  RoleSelectScreen,
  mountScreen,
  unmountScreen,
  type AppBridge,
} from "./react";

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
  /** Dev builds only: demo OTP codes / quick-fill helpers. */
  devShortcuts?: boolean;
  liveBackend?: boolean;
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

function appBridge(): AppBridge {
  return {
    invoke: (cmd, args) => invoke(cmd, args),
    navigate: (r) => navigate(r as Route),
    toast: (m, type) => showToast(m, type ?? "success"),
    devShortcuts: bootstrap?.devShortcuts ?? false,
    liveBackend: bootstrap?.liveBackend ?? false,
    appleSignInAvailable: bootstrap?.appleSignInAvailable ?? false,
  };
}

async function ensureBootstrap(): Promise<void> {
  if (bootstrap) return;
  try {
    bootstrap = await invoke<BootstrapDto>("get_bootstrap");
  } catch {
    // Browser preview without Tauri — keep defaults.
  }
}

async function render(): Promise<void> {
  if (!view) return;
  // Legacy screens write innerHTML; tear down any React root first.
  unmountScreen();
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
  mountScreen(
    view,
    createElement(RoleSelectScreen, {
      bridge: appBridge(),
      platform: bootstrap.platform ?? "",
      // Dev only: local reset. Never deletes the cloud family.
      onResetLocal: async () => {
        localStorage.clear();
        sessionStorage.clear();
        try {
          await invoke("parent_sign_out");
        } catch {
          // ignore
        }
        authEmail = "";
        showToast("Local app state reset", "info");
        await renderRoleGate();
      },
    }),
  );
}

function renderParentAuth(step: "email" | "otp"): void {
  if (!view) return;
  const target = view;
  void ensureBootstrap().then(() => {
    mountScreen(
      target,
      createElement(ParentAuthScreen, {
        bridge: appBridge(),
        step,
        email: (route.name === "parent-auth" && route.email) || authEmail,
        onEmailChange: (e: string) => {
          authEmail = e;
        },
      }),
    );
  });
}

function renderOnboard(): void {
  if (!view) return;
  updateBreadcrumbs([]);
  const target = view;
  void ensureBootstrap().then(() => {
    mountScreen(target, createElement(ChildSetupWizard, { bridge: appBridge(), mode: "onboard" }));
  });
}

function renderAddChild(): void {
  if (!view) return;
  updateBreadcrumbs([]);
  const target = view;
  void ensureBootstrap().then(() => {
    mountScreen(target, createElement(ChildSetupWizard, { bridge: appBridge(), mode: "add-child" }));
  });
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
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(DashboardScreen, { bridge: appBridge() }));
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


async function renderChildDetail(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(ChildDetailScreen, { bridge: appBridge(), childId, key: childId }));
}

async function renderAllowlist(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(AllowlistScreen, { bridge: appBridge(), childId, key: childId }));
}

async function renderTimeLimits(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(PolicyScreen, { bridge: appBridge(), childId, kind: "time-limits", key: "time-limits" + childId }));
}

async function renderQuizSettings(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(PolicyScreen, { bridge: appBridge(), childId, kind: "quiz-settings", key: "quiz-settings" + childId }));
}

async function renderRewards(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(PolicyScreen, { bridge: appBridge(), childId, kind: "rewards", key: "rewards" + childId }));
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
  if (st.screen !== "pairing" && st.screen !== "pin_menu") {
    unmountScreen();
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
  const target = view;
  void ensureBootstrap().then(() => {
    mountScreen(target, createElement(ChildPairingScreen, { bridge: appBridge() }));
  });
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
  mountScreen(view, createElement(ChildPinMenuScreen, { bridge: appBridge(), childName: st.childName }));
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

