import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { initLocale } from "./i18n";
import { createElement } from "react";
import {
  ChildPairingScreen,
  ChildPinMenuScreen,
  ChildSessionScreen,
  ParentAuthScreen,
  DashboardScreen,
  ChildDetailScreen,
  PolicyScreen,
  AllowlistScreen,
  ReportsScreen,
  NotificationsScreen,
  PairingScreen,
  AccountScreen,
  DeleteFamilyScreen,
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


interface UiStateDto<T> {
  state: "loading" | "empty" | "success" | "error";
  data: T | null;
  error: string | null;
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



async function renderReports(childId?: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(ReportsScreen, { bridge: appBridge(), childId, key: childId ?? "all" }));
}

async function renderNotifications(): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(NotificationsScreen, { bridge: appBridge() }));
}

async function renderPairing(childId: string): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(PairingScreen, { bridge: appBridge(), childId, key: childId }));
}

async function renderAccount(): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(AccountScreen, { bridge: appBridge() }));
}

async function renderDeleteFamily(): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(DeleteFamilyScreen, { bridge: appBridge() }));
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

  switch (st.screen) {
    case "pairing":
      renderChildPairing();
      break;
    case "pin_menu":
      renderChildPinMenu(st);
      break;
    default: {
      // checklist / launcher / quiz / fail_lock / ceiling — one React session view.
      const target = view;
      await ensureBootstrap();
      mountScreen(target, createElement(ChildSessionScreen, { bridge: appBridge(), initial: st, autoMinimize: !prewarmMode && overlayMode == null }));
      break;
    }
  }
}

function renderChildPairing(): void {
  if (!view) return;
  const target = view;
  void ensureBootstrap().then(() => {
    mountScreen(target, createElement(ChildPairingScreen, { bridge: appBridge() }));
  });
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

