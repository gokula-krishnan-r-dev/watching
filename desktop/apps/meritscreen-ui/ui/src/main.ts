import { invoke } from "@tauri-apps/api/core";
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

interface ChildDetail {
  card: ChildCard;
  policy: ChildPolicy;
  appRules: unknown[];
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

type Route =
  | { name: "role" }
  | { name: "parent-auth"; email?: string; step: "email" | "otp" }
  | { name: "parent-onboard" }
  | { name: "parent-home" }
  | { name: "child-detail"; childId: string }
  | { name: "pairing"; childId: string }
  | { name: "policy"; childId: string }
  | { name: "account" }
  | { name: "child" };

const view = document.getElementById("view");
const chips = Array.from(
  document.querySelectorAll<HTMLButtonElement>("[data-appearance]"),
);

let route: Route = { name: "role" };
let bootstrap: BootstrapDto | null = null;
let authEmail = "";
let overlayMode: string | null = null;
let prewarmMode = false;

initLocale();

async function loadHostFlags(): Promise<void> {
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
  view.innerHTML = `<div class="panel"><p class="muted">${escapeHtml(label)}</p></div>`;
}

/** Strip Tauri IPC framing so parents see the actionable message only. */
function friendlyError(err: unknown): string {
  let raw = err instanceof Error ? err.message : String(err);
  // e.g. "invalid args `args` for command `parent_send_otp`: …"
  const ipc = raw.match(/command [`']?\w+[`']?: (.+)$/i);
  if (ipc?.[1]) raw = ipc[1];
  raw = raw.replace(/^error:\s*/i, "").trim();
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

async function navigate(next: Route): Promise<void> {
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
      case "parent-home":
        await renderDashboard();
        break;
      case "child-detail":
        await renderChildDetail(route.childId);
        break;
      case "pairing":
        await renderPairing(route.childId);
        break;
      case "policy":
        await renderPolicy(route.childId);
        break;
      case "account":
        await renderAccount();
        break;
      case "child":
        await renderChild();
        break;
    }
  } catch (err) {
    renderError(err instanceof Error ? err.message : "Could not render screen.");
  }
}

async function renderRoleGate(): Promise<void> {
  renderLoading();
  bootstrap = await invoke<BootstrapDto>("get_bootstrap");
  applyTheme(bootstrap.appearance);

  if (bootstrap.role === "parent" && bootstrap.parentSignedIn) {
    await navigate({ name: "parent-home" });
    return;
  }

  if (!view) return;
  if (bootstrap.role === "child") {
    await navigate({ name: "child" });
    return;
  }

  view.innerHTML = `
    <div class="hero">
      <h1>Who is using this computer?</h1>
      <p>Parents manage rules. A child device pairs with a QR or 6-digit code — no child password.</p>
      <div class="actions">
        <button class="btn btn-primary" id="as-parent">Continue as Parent</button>
        <button class="btn btn-secondary" id="as-child">Set up Child device</button>
      </div>
      <p class="meta">Platform: ${escapeHtml(bootstrap.platform)}</p>
    </div>`;
  document.getElementById("as-parent")?.addEventListener("click", () => {
    void navigate({ name: "parent-auth", step: "email" });
  });
  document.getElementById("as-child")?.addEventListener("click", () => {
    void setRole("child");
  });
}

function renderParentAuth(step: "email" | "otp"): void {
  if (!view) return;
  const apple = bootstrap?.appleSignInAvailable ?? false;
  if (step === "email") {
    view.innerHTML = `
      <div class="panel">
        <h1>Parent sign in</h1>
        <p>We’ll email a 6-digit code. Demo builds accept code <span class="mono">424242</span>.</p>
        <label class="field">Email
          <input id="email" type="email" autocomplete="username" placeholder="you@example.com" />
        </label>
        <div class="actions">
          <button class="btn btn-primary" id="send">Send code</button>
          <button class="btn btn-secondary" id="google">Continue with Google</button>
          ${apple ? `<button class="btn btn-secondary" id="apple">Sign in with Apple</button>` : ""}
          <button class="btn btn-secondary" id="back">Back</button>
        </div>
      </div>`;
    document.getElementById("send")?.addEventListener("click", () => {
      void (async () => {
        const email = (document.getElementById("email") as HTMLInputElement).value.trim();
        if (!email.includes("@") || email.length < 5) {
          renderError("Enter a valid email address.", () =>
            void navigate({ name: "parent-auth", step: "email" }),
          );
          return;
        }
        authEmail = email;
        renderLoading("Sending code…");
        try {
          await invoke("parent_send_otp", { email });
          await navigate({ name: "parent-auth", step: "otp", email });
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "parent-auth", step: "email" }));
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

  view.innerHTML = `
    <div class="panel">
      <h1>Enter code</h1>
      <p>Code sent to ${escapeHtml(authEmail || "your email")}.</p>
      <label class="field">6-digit code
        <input id="otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" id="verify">Verify & continue</button>
        <button class="btn btn-secondary" id="back">Back</button>
      </div>
    </div>`;
  document.getElementById("verify")?.addEventListener("click", () => {
    void (async () => {
      const code = (document.getElementById("otp") as HTMLInputElement).value;
      renderLoading("Signing in…");
      try {
        await invoke<ParentProfile>("parent_verify_otp", { email: authEmail, code });
        const dash = await invoke<UiStateDto<{ children: ChildCard[] }>>("parent_dashboard");
        if (dash.state === "empty") {
          await navigate({ name: "parent-onboard" });
        } else {
          await navigate({ name: "parent-home" });
        }
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "parent-auth", step: "otp" }));
      }
    })();
  });
  document.getElementById("back")?.addEventListener("click", () =>
    void navigate({ name: "parent-auth", step: "email" }),
  );
}

function renderOnboard(): void {
  if (!view) return;
  view.innerHTML = `
    <div class="panel">
      <h1>Add your first child</h1>
      <p>Set a 4-digit parent PIN. You’ll use it to unlock settings on child devices.</p>
      <label class="field">Child’s name
        <input id="name" maxlength="40" />
      </label>
      <label class="field">Age band
        <select id="age">
          <option value="3_to_6">3–6</option>
          <option value="7_to_9" selected>7–9</option>
          <option value="10_to_12">10–12</option>
          <option value="13_plus">13+</option>
        </select>
      </label>
      <label class="field">Parent PIN (4 digits)
        <input id="pin" inputmode="numeric" maxlength="4" autocomplete="new-password" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" id="save">Save & open dashboard</button>
      </div>
    </div>`;
  document.getElementById("save")?.addEventListener("click", () => {
    void (async () => {
      const childName = (document.getElementById("name") as HTMLInputElement).value;
      const ageBand = (document.getElementById("age") as HTMLSelectElement).value;
      const pin = (document.getElementById("pin") as HTMLInputElement).value;
      renderLoading("Saving…");
      try {
        await invoke("parent_onboard", { childName, ageBand, pin });
        await navigate({ name: "parent-home" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "parent-onboard" }));
      }
    })();
  });
}

async function renderDashboard(): Promise<void> {
  renderLoading("Loading family…");
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
      "Add a child to set screen-time rules and pairing.",
      "Add child",
      () => void navigate({ name: "parent-onboard" }),
    );
    return;
  }

  if (!view || !dash.data) return;
  const cards = dash.data.children
    .map(
      (c) => `
      <button class="card" data-child="${escapeHtml(c.childId)}">
        <strong>${escapeHtml(c.displayName)}</strong>
        <span class="muted">${escapeHtml(c.ageBand.replace(/_/g, "–"))}</span>
        <span>${c.minutesUsedToday} min today · ${c.pairedDeviceCount} device(s)</span>
        <span class="meta">${escapeHtml(c.quizMode)} · ${escapeHtml(c.platformHint ?? "no device yet")}</span>
      </button>`,
    )
    .join("");

  view.innerHTML = `
    <div class="panel wide">
      <div class="row">
        <h1>Family</h1>
        <div class="row-actions">
          <button class="btn btn-secondary" id="account">Account</button>
          <button class="btn btn-secondary" id="add">Add child</button>
        </div>
      </div>
      <p class="muted">Signed in as ${escapeHtml(dash.data.profile.email)}</p>
      <div class="card-grid">${cards}</div>
    </div>`;

  for (const el of view.querySelectorAll<HTMLButtonElement>("[data-child]")) {
    el.addEventListener("click", () => {
      void navigate({ name: "child-detail", childId: el.dataset.child! });
    });
  }
  document.getElementById("account")?.addEventListener("click", () => void navigate({ name: "account" }));
  document.getElementById("add")?.addEventListener("click", () => void navigate({ name: "parent-onboard" }));
}

async function renderChildDetail(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    if (!view) return;
    const desktop = !!d.card.platformHint && ["windows", "macos", "linux"].includes(d.card.platformHint);
    const enf = d.enforcement;
    const enfBlock = enf
      ? `<div class="enforce-box" aria-label="Device health">
          <h2>Device health</h2>
          <p><strong>Enforcement:</strong> ${escapeHtml(enf.enforcementTier)} · Guardian ${escapeHtml(enf.guardianState)}</p>
          ${
            enf.degradedMessage
              ? `<p class="error">${escapeHtml(enf.degradedMessage)}</p>`
              : `<p class="muted">Strict (L2): ${enf.strictAvailable ? "available (opt-in)" : "not available on this device"}</p>`
          }
          ${
            enf.tamperFlags.length
              ? `<p class="error">Tamper: ${escapeHtml(enf.tamperFlags.join(", "))}</p>`
              : `<p class="muted">No tamper flags</p>`
          }
          <p class="meta">Installed apps (allowlist): ${enf.installedApps.length}</p>
          <ul class="app-list">${enf.installedApps
            .map(
              (a) =>
                `<li><span class="mono">${escapeHtml(a.appId)}</span> — ${escapeHtml(a.label)} ${
                  a.allowed ? "✓" : "✗"
                }</li>`,
            )
            .join("")}</ul>
        </div>`
      : desktop
        ? `<p class="muted">Pair a desktop device to see inventory and enforcement status.</p>`
        : "";
    view.innerHTML = `
      <div class="panel wide">
        <h1>${escapeHtml(d.card.displayName)}</h1>
        <p>Used today: <strong>${d.card.minutesUsedToday}</strong> min (honest zero until sync).</p>
        <p class="meta">Fail lock: ${escapeHtml(d.failLockScope)} (always all non-emergency)</p>
        <p class="meta">Quiz mode: ${escapeHtml(d.policy.quizMode)}${desktop ? " · device interval available" : ""}</p>
        ${enfBlock}
        <div class="actions">
          <button class="btn btn-primary" id="pair">Pair device</button>
          <button class="btn btn-secondary" id="policy">Time & quiz settings</button>
          <button class="btn btn-secondary" id="back">Back to family</button>
        </div>
      </div>`;
    document.getElementById("pair")?.addEventListener("click", () =>
      void navigate({ name: "pairing", childId }),
    );
    document.getElementById("policy")?.addEventListener("click", () =>
      void navigate({ name: "policy", childId }),
    );
    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "parent-home" }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
  }
}

async function renderPairing(childId: string): Promise<void> {
  renderLoading("Creating pairing code…");
  try {
    const offer = await invoke<PairingOffer>("parent_create_pairing", { childId });
    if (!view) return;
    view.innerHTML = `
      <div class="panel">
        <h1>Pair a device</h1>
        <p>On the child computer, choose <strong>Set up Child device</strong> and enter this code.</p>
        <p class="code-display" aria-label="Pairing code">${escapeHtml(offer.code)}</p>
        <p class="muted mono wrap">${escapeHtml(offer.qrPayload)}</p>
        <div class="actions">
          <button class="btn btn-primary" id="sim-desktop">Simulate desktop paired</button>
          <button class="btn btn-secondary" id="back">Done</button>
        </div>
      </div>`;
    document.getElementById("sim-desktop")?.addEventListener("click", () => {
      void (async () => {
        await invoke("parent_mark_paired", { childId, platform: bootstrap?.platform ?? "macos" });
        await navigate({ name: "child-detail", childId });
      })();
    });
    document.getElementById("back")?.addEventListener("click", () =>
      void navigate({ name: "child-detail", childId }),
    );
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "child-detail", childId }));
  }
}

async function renderPolicy(childId: string): Promise<void> {
  renderLoading();
  try {
    const d = await invoke<ChildDetail>("parent_child_detail", { childId });
    const desktop =
      !!d.card.platformHint && ["windows", "macos", "linux"].includes(d.card.platformHint);
    if (!view) return;
    view.innerHTML = `
      <div class="panel">
        <h1>Time & quiz</h1>
        <p class="muted">Fail lock is always all non-emergency apps — not configurable per app.</p>
        <label class="field">Quiz mode
          <select id="mode">
            <option value="app_block" ${d.policy.quizMode === "app_block" ? "selected" : ""}>Per app block</option>
            <option value="device_interval" ${d.policy.quizMode === "device_interval" ? "selected" : ""} ${desktop ? "" : ""}>Device interval ${desktop ? "(recommended)" : ""}</option>
            <option value="every_session">Every session</option>
            <option value="daily_ceiling">Daily ceiling</option>
          </select>
        </label>
        <label class="field">Quiz interval (minutes)
          <input id="interval" type="number" min="2" max="240" value="${d.policy.quizIntervalMinutes}" />
        </label>
        <label class="field">Block minutes
          <input id="block" type="number" min="2" max="240" value="${d.policy.defaultBlockMinutes}" />
        </label>
        <label class="field">Cooldown minutes
          <input id="cooldown" type="number" min="2" max="240" value="${d.policy.defaultCooldownMinutes}" />
        </label>
        <label class="field">Questions per quiz
          <input id="q" type="number" min="1" max="10" value="${d.policy.questionsPerQuiz}" />
        </label>
        <label class="field check"><input id="rewards" type="checkbox" ${d.policy.rewardsEnabled ? "checked" : ""}/> Rewards enabled</label>
        <div class="actions">
          <button class="btn btn-primary" id="save">Save</button>
          <button class="btn btn-secondary" id="back">Back</button>
        </div>
      </div>`;
    document.getElementById("save")?.addEventListener("click", () => {
      void (async () => {
        const policy: ChildPolicy = {
          ...d.policy,
          quizMode: (document.getElementById("mode") as HTMLSelectElement).value,
          quizIntervalMinutes: Number((document.getElementById("interval") as HTMLInputElement).value),
          defaultBlockMinutes: Number((document.getElementById("block") as HTMLInputElement).value),
          defaultCooldownMinutes: Number(
            (document.getElementById("cooldown") as HTMLInputElement).value,
          ),
          questionsPerQuiz: Number((document.getElementById("q") as HTMLInputElement).value),
          rewardsEnabled: (document.getElementById("rewards") as HTMLInputElement).checked,
        };
        renderLoading("Saving…");
        try {
          await invoke("parent_update_policy", { childId, policy });
          await navigate({ name: "child-detail", childId });
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "policy", childId }));
        }
      })();
    });
    document.getElementById("back")?.addEventListener("click", () =>
      void navigate({ name: "child-detail", childId }),
    );
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
  }
}

async function renderAccount(): Promise<void> {
  renderLoading();
  try {
    const prefs = await invoke<NotificationPrefs>("parent_notification_prefs");
    if (!view) return;
    view.innerHTML = `
      <div class="panel">
        <h1>Account</h1>
        <p class="muted">Appearance is in the header. Notifications are stored on this device.</p>
        <label class="field check"><input id="nr" type="checkbox" ${prefs.quizResults ? "checked" : ""}/> Quiz results</label>
        <label class="field check"><input id="nf" type="checkbox" ${prefs.failLockAlerts ? "checked" : ""}/> Fail-lock alerts</label>
        <label class="field check"><input id="nt" type="checkbox" ${prefs.tamperAlerts ? "checked" : ""}/> Tamper alerts</label>
        <label class="field">Reset parent PIN
          <input id="pin" inputmode="numeric" maxlength="4" />
        </label>
        <div class="actions">
          <button class="btn btn-primary" id="save">Save prefs / PIN</button>
          <button class="btn btn-secondary" id="delete">Delete family</button>
          <button class="btn btn-secondary" id="out">Sign out</button>
          <button class="btn btn-secondary" id="back">Back</button>
        </div>
      </div>`;
    document.getElementById("save")?.addEventListener("click", () => {
      void (async () => {
        await invoke("parent_set_notification_prefs", {
          prefs: {
            quizResults: (document.getElementById("nr") as HTMLInputElement).checked,
            failLockAlerts: (document.getElementById("nf") as HTMLInputElement).checked,
            tamperAlerts: (document.getElementById("nt") as HTMLInputElement).checked,
          },
        });
        const pin = (document.getElementById("pin") as HTMLInputElement).value;
        if (pin) await invoke("parent_reset_pin", { pin });
        await navigate({ name: "parent-home" });
      })();
    });
    document.getElementById("delete")?.addEventListener("click", () => {
      void (async () => {
        if (!confirm("Delete this family on this device? This cannot be undone in the demo store.")) return;
        await invoke("parent_delete_family");
        await navigate({ name: "parent-onboard" });
      })();
    });
    document.getElementById("out")?.addEventListener("click", () => {
      void (async () => {
        await invoke("parent_sign_out");
        await navigate({ name: "role" });
      })();
    });
    document.getElementById("back")?.addEventListener("click", () => void navigate({ name: "parent-home" }));
  } catch (e) {
    renderError(String(e), () => void navigate({ name: "parent-home" }));
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
    case "checklist":
      renderChildChecklist(st);
      break;
    case "launcher":
      renderChildLauncher(st);
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
  }
}

function renderChildPairing(): void {
  if (!view) return;
  view.innerHTML = `
    <div class="panel">
      <h1>Pair this computer</h1>
      <p>Ask a parent for the 6-digit code from Family → Pair device. Demo accepts any 6 digits.</p>
      <label class="field">Pairing code
        <input id="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
      </label>
      <label class="field">Your name (optional)
        <input id="name" maxlength="40" placeholder="Friend" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" id="pair">Pair</button>
        <button class="btn btn-secondary" id="back">Back</button>
      </div>
      <p class="meta">Local only — no cloud calls on this screen.</p>
    </div>`;
  document.getElementById("pair")?.addEventListener("click", () => {
    void (async () => {
      const code = (document.getElementById("code") as HTMLInputElement).value;
      const childName = (document.getElementById("name") as HTMLInputElement).value;
      renderLoading("Pairing…");
      try {
        await invoke("child_pair", { code, childName: childName || null });
        await navigate({ name: "child" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  });
  document.getElementById("back")?.addEventListener("click", () => {
    void setRole("unassigned");
  });
}

function renderChildChecklist(st: ChildUiState): void {
  if (!view) return;
  const items = st.checklist
    .map(
      (c) => `
      <label class="field check">
        <input type="checkbox" data-check="${escapeHtml(c.id)}" ${c.done ? "checked" : ""}/>
        ${escapeHtml(c.label)}${c.recommended ? ' <span class="muted">(recommended)</span>' : ""}
      </label>`,
    )
    .join("");
  view.innerHTML = `
    <div class="panel">
      <h1>Hi ${escapeHtml(st.childName)}</h1>
      <p>Finish setup before the launcher opens. Pack: <span class="mono">${escapeHtml(st.packSource)}</span></p>
      ${items}
      <div class="actions">
        <button class="btn btn-primary" id="done">Continue to launcher</button>
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
  document.getElementById("done")?.addEventListener("click", () => {
    void (async () => {
      renderLoading();
      try {
        await invoke("child_complete_checklist");
        await navigate({ name: "child" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  });
}

function renderChildLauncher(st: ChildUiState): void {
  if (!view) return;
  const apps = st.apps
    .map(
      (a) => `
      <button class="app-tile tone-${escapeHtml(a.iconTone)}" data-app="${escapeHtml(a.appId)}"
        aria-label="${escapeHtml(a.displayName)}${a.isEmergency ? ` (${t("child_emergency")})` : ""}">
        <span class="app-glyph" aria-hidden="true">${escapeHtml(a.displayName.slice(0, 1))}</span>
        <strong>${escapeHtml(a.displayName)}</strong>
        ${a.isEmergency ? `<span class="muted">${escapeHtml(t("child_emergency"))}</span>` : ""}
      </button>`,
    )
    .join("");
  view.innerHTML = `
    <div class="panel wide">
      <div class="row">
        <div>
          <h1 id="launcher-title">${escapeHtml(t("child_launcher_title", { name: st.childName }))}</h1>
          <p class="muted">${escapeHtml(
            t("child_launcher_meta", {
              block: st.minutesRemainingBlock,
              today: st.minutesUsedToday,
              pack: st.packSource,
            }),
          )}</p>
        </div>
        <div class="row-actions">
          <button class="btn btn-secondary" id="tick">${escapeHtml(t("lab_plus_5_min"))}</button>
          <button class="btn btn-secondary" id="quiz-now">${escapeHtml(t("lab_quiz_now"))}</button>
          <button class="btn btn-secondary" id="pin">${escapeHtml(t("parent_button"))}</button>
        </div>
      </div>
      <div class="launcher-grid" role="list" aria-label="${escapeHtml(t("a11y_launcher_apps"))}">${apps}</div>
    </div>`;
  for (const el of view.querySelectorAll<HTMLButtonElement>("[data-app]")) {
    el.addEventListener("click", () => {
      void (async () => {
        try {
          await invoke("child_launch_app", { appId: el.dataset.app! });
          await navigate({ name: "child" });
        } catch (e) {
          renderError(String(e), () => void navigate({ name: "child" }));
        }
      })();
    });
  }
  document.getElementById("tick")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_tick", { deltaMs: 5 * 60_000 });
      await navigate({ name: "child" });
    })();
  });
  document.getElementById("quiz-now")?.addEventListener("click", () => {
    void (async () => {
      try {
        await invoke("child_force_quiz");
        await navigate({ name: "child" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  });
  document.getElementById("pin")?.addEventListener("click", () => {
    void (async () => {
      await invoke("child_open_pin_menu");
      await navigate({ name: "child" });
    })();
  });
}

function renderChildQuiz(st: ChildUiState): void {
  if (!view) return;
  const lock = st.quizLock;
  const q = st.quiz;
  const feedback = st.lastFeedback;
  if (lock && lock.remainingSeconds > 0) {
    view.innerHTML = `
      <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-labelledby="quiz-lock-title">
        <h1 id="quiz-lock-title">${escapeHtml(t("child_quiz_lock_title"))}</h1>
        <p>${escapeHtml(lock.conceptLine || "Review the idea, then try again.")}</p>
        <p class="lock-timer mono" aria-live="polite" aria-atomic="true">${lock.remainingSeconds}s</p>
        <p class="muted">${escapeHtml(t("child_quiz_lock_body"))}</p>
      </div>`;
    window.setTimeout(() => {
      void (async () => {
        await invoke("child_tick", { deltaMs: 1_000 });
        await navigate({ name: "child" });
      })();
    }, 900);
    return;
  }
  if (!q) {
    view.innerHTML = `
      <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-label="${escapeHtml(t("a11y_quiz_dialog"))}">
        <h1>${escapeHtml(t("a11y_quiz_dialog"))}</h1>
        <p class="muted">${prewarmMode ? "Pre-warming…" : "Loading question…"}</p>
      </div>`;
    if (!prewarmMode) void navigate({ name: "child" });
    return;
  }
  const choices = q.choices
    .map(
      (c, i) =>
        `<button class="btn btn-secondary choice" data-choice="${escapeHtml(c.id)}" data-index="${i}"
          aria-keyshortcuts="${i + 1}">${escapeHtml(c.text)} <span class="muted mono" aria-hidden="true">${i + 1}</span></button>`,
    )
    .join("");
  const teach =
    q.teachTitle || q.teachBody.length
      ? `<div class="teach"><strong>${escapeHtml(q.teachTitle ?? "Remember")}</strong>
         ${q.teachBody.map((l) => `<p>${escapeHtml(l)}</p>`).join("")}</div>`
      : "";
  const fb = feedback
    ? `<p class="${feedback.correct ? "ok" : "error"}" aria-live="polite">${escapeHtml(feedback.resultLine)} — ${escapeHtml(feedback.whyLine)}</p>`
    : "";
  view.innerHTML = `
    <div class="overlay quiz-overlay" role="dialog" aria-modal="true" aria-labelledby="quiz-prompt">
      <p class="meta" aria-live="polite">${escapeHtml(
        t("child_quiz_progress", { current: q.index + 1, total: q.total }),
      )}</p>
      <h1 id="quiz-prompt">${escapeHtml(q.prompt)}</h1>
      ${teach}
      ${fb}
      <div class="actions" role="group" aria-label="${escapeHtml(t("a11y_quiz_choices"))}">${choices}</div>
      <p class="muted">${escapeHtml(t("child_quiz_no_skip"))}</p>
    </div>`;
  const answer = (choiceId: string) => {
    void (async () => {
      try {
        await invoke("child_answer_quiz", { choiceId });
        await navigate({ name: "child" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
      }
    })();
  };
  for (const el of view.querySelectorAll<HTMLButtonElement>("[data-choice]")) {
    el.addEventListener("click", () => answer(el.dataset.choice!));
  }
  const onKey = (ev: KeyboardEvent) => {
    if (ev.key >= "1" && ev.key <= "9") {
      const idx = Number(ev.key) - 1;
      const btn = view.querySelector<HTMLButtonElement>(`[data-index="${idx}"]`);
      if (btn?.dataset.choice) {
        ev.preventDefault();
        answer(btn.dataset.choice);
      }
    }
  };
  window.addEventListener("keydown", onKey, { once: true });
  view.querySelector<HTMLButtonElement>("[data-choice]")?.focus();
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
        <button class="btn btn-secondary" id="pin">${escapeHtml(t("parent_pin_label"))}</button>
      </div>
    </div>`;
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
    window.setTimeout(() => {
      void (async () => {
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
      <p class="muted">Demo PIN is <span class="mono">1234</span>. Appearance is in the header.</p>
      <label class="field">${escapeHtml(t("parent_pin_label"))}
        <input id="pin" inputmode="numeric" maxlength="4" autocomplete="off" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" id="unpair">${escapeHtml(t("unpair_device"))}</button>
        <button class="btn btn-secondary" id="refresh">${escapeHtml(t("refresh_snapshot"))}</button>
        <button class="btn btn-secondary" id="back">${escapeHtml(t("back"))}</button>
      </div>
      <p class="meta">Phase: ${escapeHtml(st.phase)} · Pack: ${escapeHtml(st.packSource)}</p>
    </div>`;
  document.getElementById("unpair")?.addEventListener("click", () => {
    void (async () => {
      const pin = (document.getElementById("pin") as HTMLInputElement).value;
      renderLoading();
      try {
        await invoke("child_pin_unpair", { pin });
        await navigate({ name: "role" });
      } catch (e) {
        renderError(String(e), () => void navigate({ name: "child" }));
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

void (async () => {
  await loadHostFlags();
  await navigate(route);
})();
