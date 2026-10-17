import * as React from "react";
import { BarChart3, ExternalLink, Gift, Siren, KeyRound, LogOut, Minus, Pause, Play, Plus, RefreshCw, Rocket, Settings, ShieldCheck, X } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildDetail, ChildUiState, DashboardSnapshot, UiStateDto } from "../lib/types";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { Dialog } from "../ui/dialog";
import { PinInput } from "../components/code-input";
import { Spinner } from "../ui/spinner";
import { activeBonus, avatarFor, ageLabel, BONUS_MAX_PER_DAY, formatMinutes } from "../lib/catalog";
import { friendlyError } from "../lib/errors";
import { cn } from "../lib/utils";

export interface TrayActions {
  /** Close the popover (hide tray window, or close the in-app preview). */
  close: () => void;
  /** Bring the main window forward and route it. */
  openMain: (route: { name: string; childId?: string }) => Promise<void>;
}

function Shell({ title, live, onClose, onRefresh, refreshing, children }: { title: string; live: boolean; onClose: () => void; onRefresh?: () => void; refreshing?: boolean; children: React.ReactNode }) {
  return (
    <div className="flex w-full max-w-[380px] flex-col gap-3 rounded-xl border border-border bg-card p-4 text-card-foreground shadow-lg">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className={cn("h-2.5 w-2.5 rounded-full", live ? "bg-emerald-500" : "bg-amber-500")} aria-hidden="true" />
          <span className="text-sm font-bold">{title}</span>
        </div>
        <div className="flex gap-1">
          {onRefresh && (
            <Button size="icon" variant="ghost" aria-label="Refresh" onClick={onRefresh} loading={refreshing}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
            </Button>
          )}
          <Button size="icon" variant="ghost" aria-label="Close" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
      {children}
    </div>
  );
}

/** Menu-bar / system-tray popover. Shows the child card on a paired child device, otherwise the parent controls. */
export function TrayPopover({ bridge, actions, initialChildId, reloadKey = 0 }: { bridge: AppBridge; actions: TrayActions; initialChildId?: string; reloadKey?: number }) {
  const [mode, setMode] = React.useState<"loading" | "child" | "parent" | "error">("loading");
  const [childSt, setChildSt] = React.useState<ChildUiState | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    setError(null);
    try {
      const st = await bridge.invoke<ChildUiState>("child_ui_state").catch(() => null);
      if (st?.paired) {
        setChildSt(st);
        setMode("child");
      } else setMode("parent");
    } catch (e) {
      setError(friendlyError(e));
      setMode("error");
    }
  }, [bridge]);
  React.useEffect(() => void load(), [load, reloadKey]);

  if (mode === "loading")
    return (
      <Shell title="MeritScreen" live={false} onClose={actions.close}>
        <div className="flex justify-center py-6">
          <Spinner label="Loading…" />
        </div>
      </Shell>
    );
  if (mode === "error")
    return (
      <Shell title="MeritScreen" live={false} onClose={actions.close}>
        <Alert variant="destructive">{error}</Alert>
        <Button onClick={() => void load()}>Try again</Button>
      </Shell>
    );
  if (mode === "child" && childSt) return <ChildTrayCard bridge={bridge} actions={actions} initial={childSt} onUnpaired={() => void load()} />;
  return <ParentTray bridge={bridge} actions={actions} initialChildId={initialChildId} reloadKey={reloadKey} />;
}

function ParentTray({ bridge, actions, initialChildId, reloadKey }: { bridge: AppBridge; actions: TrayActions; initialChildId?: string; reloadKey: number }) {
  const [snap, setSnap] = React.useState<DashboardSnapshot | null>(null);
  const [active, setActive] = React.useState<string | undefined>(initialChildId);
  const [detail, setDetail] = React.useState<ChildDetail | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [busy, setBusy] = React.useState<string | null>(null);
  const [confirmEmergency, setConfirmEmergency] = React.useState(false);

  const load = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_dashboard");
      if (d.state === "error") throw d.error;
      setSnap(d.data);
      const kids = d.data?.children ?? [];
      const id = kids.find((k) => k.childId === active)?.childId ?? kids[0]?.childId;
      setActive(id);
      setDetail(id ? await bridge.invoke<ChildDetail>("parent_child_detail", { childId: id }) : null);
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [bridge, active]);
  React.useEffect(() => void load(), [load, reloadKey]);

  if (loading && !snap)
    return (
      <Shell title="MeritScreen" live={false} onClose={actions.close}>
        <div className="flex justify-center py-6">
          <Spinner label="Loading…" />
        </div>
      </Shell>
    );
  if (error)
    return (
      <Shell title="MeritScreen" live={false} onClose={actions.close}>
        <Alert variant="destructive" title="Couldn’t connect">
          {error}
        </Alert>
        <Button onClick={() => void load()}>Try again</Button>
      </Shell>
    );

  const kids = snap?.children ?? [];
  if (kids.length === 0 || !detail) {
    return (
      <Shell title="MeritScreen" live={false} onClose={actions.close}>
        <div className="flex flex-col items-center gap-2 py-4 text-center">
          <ShieldCheck className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
          <strong className="text-sm">No children yet</strong>
          <p className="m-0 text-xs text-muted-foreground">Open MeritScreen to add a child and pair their device.</p>
          <Button size="sm" onClick={() => void actions.openMain({ name: "role" })}>
            Open MeritScreen
          </Button>
        </div>
      </Shell>
    );
  }

  const { card, policy } = detail;
  const ceiling = policy.dailyCeilingMinutes;
  const bonus = activeBonus(policy);
  const total = ceiling == null ? null : ceiling + bonus;
  const left = total == null ? null : Math.max(0, total - card.minutesUsedToday);
  const pct = total ? Math.min(100, Math.round((card.minutesUsedToday / total) * 100)) : 0;
  const bonusRoom = BONUS_MAX_PER_DAY - bonus;

  const grant = async (key: string, minutes: number) => {
    setBusy(key);
    try {
      const p = await bridge.invoke<typeof policy>("parent_grant_bonus", { childId: card.childId, minutes });
      setDetail({ ...detail, policy: { ...policy, ...p } });
      bridge.toast(`+${minutes} min for ${card.displayName} today`, "success");
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(null);
      setConfirmEmergency(false);
    }
  };

  const updatePolicy = async (key: string, patch: Partial<typeof policy>, msg: string) => {
    setBusy(key);
    try {
      await bridge.invoke("parent_update_policy", { childId: card.childId, policy: { ...policy, ...patch } });
      setDetail({ ...detail, policy: { ...policy, ...patch } });
      bridge.toast(msg, "success");
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(null);
    }
  };

  return (
    <Shell title="MeritScreen" live={!policy.paused} onClose={actions.close} onRefresh={() => void load()} refreshing={loading}>
      {kids.length > 1 && (
        <div role="tablist" aria-label="Child" className="flex gap-1 overflow-x-auto">
          {kids.map((k) => (
            <Button key={k.childId} role="tab" aria-selected={k.childId === active} size="sm" variant="tab" onClick={() => setActive(k.childId)}>
              {k.displayName}
            </Button>
          ))}
        </div>
      )}
      <div className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-full bg-secondary text-xl" aria-hidden="true">
          {avatarFor(card.childId, card.displayName)}
        </span>
        <div className="min-w-0 flex-1">
          <p className="m-0 truncate text-sm font-bold">{card.displayName}</p>
          <p className="m-0 text-xs text-muted-foreground">{ageLabel(card.ageBand)}</p>
        </div>
        {policy.paused ? <Badge variant="destructive">Paused</Badge> : card.pairedDeviceCount ? <Badge variant="success">Protected</Badge> : <Badge variant="outline">Not paired</Badge>}
      </div>

      <div className="rounded-lg bg-muted p-3">
        <div className="flex items-end justify-between gap-2">
          <div>
            <p className="m-0 text-lg font-bold">{left == null ? "No daily limit" : `${formatMinutes(left)} left`}</p>
            <p className="m-0 text-xs text-muted-foreground">
              {formatMinutes(card.minutesUsedToday)} used{ceiling != null && ` of ${formatMinutes(ceiling)}`}
              {bonus > 0 && ` + ${formatMinutes(bonus)} bonus`}
            </p>
          </div>
          {ceiling != null && (
            <div className="flex gap-1" role="group" aria-label="Daily limit">
              <Button size="icon" variant="secondary" aria-label="Lower daily limit by 15 minutes" disabled={ceiling <= 15 || busy != null} onClick={() => void updatePolicy("minus", { dailyCeilingMinutes: Math.max(15, ceiling - 15) }, `Daily limit: ${formatMinutes(Math.max(15, ceiling - 15))}`)}>
                <Minus className="h-4 w-4" aria-hidden="true" />
              </Button>
              <Button size="icon" variant="secondary" aria-label="Raise daily limit by 15 minutes" disabled={ceiling >= 480 || busy != null} onClick={() => void updatePolicy("plus", { dailyCeilingMinutes: Math.min(480, ceiling + 15) }, `Daily limit: ${formatMinutes(Math.min(480, ceiling + 15))}`)}>
                <Plus className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          )}
        </div>
        {ceiling != null && (
          <div className="mt-2 h-2 rounded-full bg-background" aria-hidden="true">
            <div className={cn("h-2 rounded-full", pct >= 90 ? "bg-destructive" : pct >= 75 ? "bg-amber-500" : "bg-primary")} style={{ width: `${pct}%` }} />
          </div>
        )}
      </div>

      {ceiling != null && (
        <section aria-label="Bonus time today" className="flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs">
            <span className="font-semibold">Bonus time (today only)</span>
            <span className="text-muted-foreground" aria-live="polite">
              {bonus > 0 ? `${formatMinutes(bonus)} granted · resets at midnight` : "None today"}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-1">
            <Button size="sm" variant="secondary" disabled={busy != null || bonusRoom <= 0} loading={busy === "b15"} onClick={() => void grant("b15", 15)} aria-label="Give 15 bonus minutes today">
              <Gift className="h-4 w-4" aria-hidden="true" /> +15 min
            </Button>
            <Button size="sm" variant="secondary" disabled={busy != null || bonusRoom <= 0} loading={busy === "b30"} onClick={() => void grant("b30", 30)} aria-label="Give 30 bonus minutes today">
              <Gift className="h-4 w-4" aria-hidden="true" /> +30 min
            </Button>
            <Button size="sm" variant="outline" className="text-destructive" disabled={busy != null || bonusRoom <= 0} onClick={() => setConfirmEmergency(true)} aria-label="Emergency unlock: 1 hour today">
              <Siren className="h-4 w-4" aria-hidden="true" /> +1 h
            </Button>
          </div>
          {bonusRoom <= 0 && <p className="m-0 text-xs text-muted-foreground">Today’s bonus maximum ({formatMinutes(BONUS_MAX_PER_DAY)}) reached.</p>}
        </section>
      )}

      <Dialog
        open={confirmEmergency}
        onClose={() => setConfirmEmergency(false)}
        title={`Emergency unlock for ${card.displayName}?`}
        description="Adds 1 hour of screen time for today only. The daily limit stays the same and the bonus ends at midnight."
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmEmergency(false)}>
              Cancel
            </Button>
            <Button variant="destructive" loading={busy === "b60"} onClick={() => void grant("b60", 60)}>
              Unlock 1 hour
            </Button>
          </>
        }
      />

      <Button
        variant={policy.paused ? "default" : "secondary"}
        loading={busy === "pause"}
        onClick={() => void updatePolicy("pause", { paused: !policy.paused }, policy.paused ? `${card.displayName} can use the computer again` : `${card.displayName}’s screen time is paused`)}
      >
        {policy.paused ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
        {policy.paused ? "Resume screen time" : "Pause screen time"}
      </Button>

      <p className="m-0 text-xs text-muted-foreground">
        Quiz every {policy.quizIntervalMinutes} min · {policy.questionsPerQuiz} questions · pass {policy.passScorePercent}%
      </p>

      <div className="flex flex-wrap gap-1 border-t border-border pt-3">
        <Button size="sm" onClick={() => void actions.openMain({ name: "child-detail", childId: card.childId })}>
          <ExternalLink className="h-4 w-4" aria-hidden="true" /> Open
        </Button>
        <Button size="sm" variant="ghost" onClick={() => void actions.openMain({ name: "reports", childId: card.childId })}>
          <BarChart3 className="h-4 w-4" aria-hidden="true" /> Reports
        </Button>
        <Button size="sm" variant="ghost" onClick={() => void actions.openMain({ name: "account" })}>
          <Settings className="h-4 w-4" aria-hidden="true" /> Settings
        </Button>
        <Button size="sm" variant="ghost" className="ml-auto text-destructive" onClick={() => void bridge.invoke("quit_app").catch((e) => bridge.toast(friendlyError(e), "error"))}>
          Quit
        </Button>
      </div>
    </Shell>
  );
}

function fmtTimer(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/** Child-device tray card: live countdown, quick quiz, and PIN-gated exit. */
export function ChildTrayCard({ bridge, actions, initial, onUnpaired }: { bridge: AppBridge; actions: TrayActions; initial: ChildUiState; onUnpaired: () => void }) {
  const [st, setSt] = React.useState(initial);
  const [autostart, setAutostart] = React.useState<boolean | null>(null);
  const [exitOpen, setExitOpen] = React.useState(false);

  React.useEffect(() => {
    bridge.invoke<boolean>("get_autostart_status").then(setAutostart, () => setAutostart(null));
    const id = window.setInterval(() => {
      bridge.invoke<ChildUiState>("child_ui_state").then(
        (fresh) => (fresh.paired ? setSt(fresh) : onUnpaired()),
        () => undefined,
      );
    }, 1000);
    return () => window.clearInterval(id);
  }, [bridge, onUnpaired]);

  const sec = st.secondsRemainingBlock ?? st.minutesRemainingBlock * 60;
  const block = (st.blockDurationMinutes || 15) * 60;
  const pct = Math.min(100, Math.max(0, Math.round(((block - sec) / block) * 100)));

  const go = async (cmd?: string) => {
    if (cmd) await bridge.invoke(cmd).catch((e) => bridge.toast(friendlyError(e), "error"));
    await actions.openMain({ name: "child" });
  };

  return (
    <Shell title="MeritScreen" live onClose={actions.close}>
      <div className="flex items-center gap-3">
        <ShieldCheck className="h-8 w-8 text-primary" aria-hidden="true" />
        <div>
          <p className="m-0 text-sm font-bold">{st.childName}</p>
          <p className="m-0 text-xs text-muted-foreground">Protected</p>
        </div>
      </div>
      <div className="rounded-lg bg-muted p-3">
        <p className="m-0 text-xs font-semibold text-muted-foreground">Next quiz in</p>
        <p className="m-0 font-mono text-3xl font-bold">{fmtTimer(sec)}</p>
        <div className="mt-2 h-2 rounded-full bg-background" aria-hidden="true">
          <div className="h-2 rounded-full bg-primary" style={{ width: `${pct}%` }} />
        </div>
        <p className="m-0 mt-1 text-xs text-muted-foreground">
          {st.minutesUsedToday} min used today ·{" "}
          {st.dailyLimitMinutes == null ? "No daily limit" : `${st.dailyRemainingMinutes} min left today`}
          {(st.bonusMinutesToday ?? 0) > 0 && ` (includes ${st.bonusMinutesToday} min bonus)`}
        </p>
      </div>
      {st.activeAppName && <p className="m-0 text-xs text-muted-foreground">Using now: <strong className="text-foreground">{st.activeAppName}</strong></p>}
      {autostart != null && <p className="m-0 text-xs text-muted-foreground">Starts with the computer: {autostart ? "on" : "off"}</p>}
      <div className="flex flex-col gap-2">
        <Button onClick={() => void go("child_force_quiz")}>
          <Rocket className="h-4 w-4" aria-hidden="true" /> Take a quiz now
        </Button>
        <Button variant="secondary" onClick={() => void go()}>
          <ExternalLink className="h-4 w-4" aria-hidden="true" /> Open MeritScreen
        </Button>
        <Button variant="ghost" onClick={() => void go("child_open_pin_menu")}>
          <KeyRound className="h-4 w-4" aria-hidden="true" /> Parent PIN
        </Button>
      </div>
      <div className="flex justify-end border-t border-border pt-2">
        <Button size="sm" variant="ghost" className="text-destructive" onClick={() => setExitOpen(true)}>
          <LogOut className="h-4 w-4" aria-hidden="true" /> Exit
        </Button>
      </div>
      <ExitWithPinDialog bridge={bridge} open={exitOpen} onClose={() => setExitOpen(false)} />
    </Shell>
  );
}

function ExitWithPinDialog({ bridge, open, onClose }: { bridge: AppBridge; open: boolean; onClose: () => void }) {
  const [pin, setPin] = React.useState("");
  const [err, setErr] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    if (open) {
      setPin("");
      setErr(null);
    }
  }, [open]);
  const submit = async (v: string) => {
    if (v.length !== 4 || busy) return;
    setBusy(true);
    setErr(null);
    try {
      await bridge.invoke("child_exit_with_pin", { pin: v });
    } catch (e) {
      setErr(friendlyError(e) || "Incorrect parent PIN.");
      setPin("");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Parent PIN required"
      description="MeritScreen is protecting this computer. A parent must enter their PIN to quit."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="destructive" loading={busy} disabled={pin.length !== 4} onClick={() => void submit(pin)}>
            Quit MeritScreen
          </Button>
        </>
      }
    >
      <PinInput value={pin} onChange={setPin} onComplete={(v) => void submit(v)} invalid={!!err} autoFocus disabled={busy} />
      {err && (
        <p className="m-0 text-sm font-semibold text-destructive" role="alert">
          {err}
        </p>
      )}
    </Dialog>
  );
}

/** Secondary-display shield shown while a quiz is due on the main display. */
export function ShieldOverlay() {
  return (
    <div className="fixed inset-0 flex items-center justify-center bg-slate-950 p-6 text-slate-50" role="alert" aria-live="assertive">
      <div className="flex max-w-md flex-col items-center gap-4 text-center">
        <span className="flex h-20 w-20 items-center justify-center rounded-full bg-emerald-500/10 ring-8 ring-emerald-500/10">
          <ShieldCheck className="h-10 w-10 text-emerald-400" aria-hidden="true" />
        </span>
        <h1 className="m-0 text-3xl font-bold">Screen paused for a quiz</h1>
        <p className="m-0 text-base text-slate-300">Finish the quiz on your main display to unlock all screens.</p>
        <Badge variant="outline" className="border-white/20 text-slate-300">
          MeritScreen
        </Badge>
      </div>
    </div>
  );
}
