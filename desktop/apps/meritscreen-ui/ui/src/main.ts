import { invoke } from "@tauri-apps/api/core";
import { isSessionExpired, SESSION_EXPIRED_MSG } from "./react/lib/errors";
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
  mountAux,
  unmountAux,
  TrayPopover,
  ShieldOverlay,
  type TrayActions,
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
  | "nursery"
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
  const target = document.getElementById("tray-popover-modal-content");
  if (!target) return;
  if (next) void renderTrayPopover(undefined, target);
  else unmountAux(target);
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
      toggleTrayPreviewModal(false);
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

let sessionRedirecting = false;
/** The backend already tried refreshing the ID token; send the parent back to sign-in once. */
async function handleSessionExpired(): Promise<void> {
  if (sessionRedirecting || route.name === "parent-auth") return;
  sessionRedirecting = true;
  try {
    showToast(SESSION_EXPIRED_MSG, "info", 5000);
    await navigate({ name: "parent-auth", step: "email" });
  } finally {
    sessionRedirecting = false;
  }
}

function appBridge(): AppBridge {
  return {
    invoke: async (cmd, args) => {
      try {
        return await invoke(cmd, args);
      } catch (e) {
        if (cmd.startsWith("parent_") && isSessionExpired(e)) void handleSessionExpired();
        throw e;
      }
    },
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
    // The tray/shield windows reach screens through here, not renderRoleGate:
    // apply the saved appearance so popovers don't fall back to OS media defaults.
    applyTheme(bootstrap.appearance);
    if (bootstrap.platform) document.documentElement.classList.add("platform-" + bootstrap.platform);
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



async function renderDashboard(): Promise<void> {
  if (!view) return;
  updateBreadcrumbs([]);
  await ensureBootstrap();
  mountScreen(view, createElement(DashboardScreen, { bridge: appBridge() }));
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


let trayReloadKey = 0;
async function renderTrayPopover(activeChildId?: string, targetEl?: HTMLElement): Promise<void> {
  const container = targetEl || view;
  if (!container) return;
  if (!targetEl) updateBreadcrumbs([]);
  await ensureBootstrap();
  const actions: TrayActions = {
    close: () => {
      if (targetEl) toggleTrayPreviewModal(false);
      else void invoke("hide_tray_popover").catch(() => undefined);
    },
    openMain: async (next) => {
      if (targetEl) toggleTrayPreviewModal(false);
      await invoke("show_main_window").catch(() => undefined);
      await navigate(next as Route);
      if (!targetEl) await invoke("hide_tray_popover").catch(() => undefined);
    },
  };
  trayReloadKey += 1;
  const node = createElement(TrayPopover, { bridge: appBridge(), actions, initialChildId: activeChildId, reloadKey: trayReloadKey });
  if (targetEl) mountAux(targetEl, node);
  else mountScreen(container, node);
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
  mountScreen(view, createElement(ShieldOverlay));
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

