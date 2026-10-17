import * as React from "react";
import { createPortal } from "react-dom";
import { BookOpen, CheckCircle2, Flame, KeyRound, Minimize2, PartyPopper, Play, RotateCcw, Rocket, ShieldCheck, Sparkles, Timer, Trophy, Volume2 } from "lucide-react";
import { visualEmoji, visualLabel } from "../lib/visual";
import type { AppBridge } from "../bridge";
import type { ChildUiState } from "../lib/types";
import { t } from "../../i18n";
import { Button } from "../ui/button";
import { Card } from "../ui/card";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { Switch } from "../ui/form-controls";
import { Dialog } from "../ui/dialog";
import { PinInput } from "../components/code-input";
import { ErrorState } from "../components/states";
import { friendlyError } from "../lib/errors";
import { cn } from "../lib/utils";

export const SESSION_SCREENS = ["checklist", "launcher", "quiz", "nursery", "fail_lock", "ceiling", "paused"] as const;

/** Read-aloud (Android QuizTtsNarrator: offline TTS, rate 0.85, pitch 1.05). */
export function speak(text: string): void {
  try {
    const synth = window.speechSynthesis;
    if (!synth || !text.trim()) return;
    synth.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 0.85;
    u.pitch = 1.05;
    synth.speak(u);
  } catch {
    /* TTS unavailable — text stays on screen */
  }
}

function fmtTimer(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  const m = Math.floor(s / 60);
  return `${m}:${String(s % 60).padStart(2, "0")}`;
}

function grantedMinutes(st: ChildUiState): number {
  return st.blockDurationMinutes || (st.secondsRemainingBlock ? Math.round(st.secondsRemainingBlock / 60) : st.minutesRemainingBlock);
}

/**
 * Child session: checklist → launcher (protected) → quiz → fail lock / daily limit.
 * Owns polling and the 1 s cooldown ticks the legacy view used to drive.
 */
export function ChildSessionScreen({ bridge, initial, autoMinimize = true }: { bridge: AppBridge; initial: ChildUiState; autoMinimize?: boolean }) {
  const [st, setSt] = React.useState<ChildUiState>(initial);
  const [error, setError] = React.useState<string | null>(null);
  const [passed, setPassed] = React.useState<ChildUiState | null>(null);
  const [skipOpen, setSkipOpen] = React.useState(false);

  React.useEffect(() => setSt(initial), [initial]);

  const refresh = React.useCallback(async () => {
    try {
      const next = await bridge.invoke<ChildUiState>("child_ui_state");
      if (!(SESSION_SCREENS as readonly string[]).includes(next.screen)) {
        await bridge.navigate({ name: "child" });
        return;
      }
      setSt(next);
      setError(null);
    } catch (e) {
      setError(friendlyError(e));
    }
  }, [bridge]);

  const openPinMenu = async () => {
    try {
      await bridge.invoke("child_open_pin_menu");
    } finally {
      await bridge.navigate({ name: "child" });
    }
  };

  // Block other screens behind the quiz in the window chrome.
  React.useEffect(() => {
    // Lock screens take over the whole window (chrome hidden) so nothing else is reachable.
    const blocking = st.screen === "quiz" || st.screen === "fail_lock" || st.screen === "ceiling" || st.screen === "paused" || passed != null;
    document.body.classList.toggle("quiz-blocking-mode", blocking);
    return () => document.body.classList.remove("quiz-blocking-mode");
  }, [st.screen, passed]);

  // Timers: locks advance by 1 s ticks; launcher/ceiling poll for live state.
  const locked = (st.screen === "quiz" && (st.quizLock?.remainingSeconds ?? 0) > 0) || (st.screen === "fail_lock" && st.cooldownSeconds > 0);
  React.useEffect(() => {
    if (passed) return;
    let cancelled = false;
    const period = locked || st.screen === "launcher" ? 1000 : st.screen === "ceiling" || st.screen === "paused" ? 2000 : 0;
    if (!period) return;
    const id = window.setInterval(() => {
      void (async () => {
        if (locked) await bridge.invoke("child_tick", { deltaMs: 1000 }).catch(() => undefined);
        if (!cancelled) await refresh();
      })();
    }, period);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [bridge, refresh, locked, st.screen, passed]);

  const finishQuiz = React.useCallback(async () => {
    setPassed(null);
    await bridge.invoke("exit_quiz_blocking").catch(() => undefined);
    await refresh();
  }, [bridge, refresh]);

  if (error && !st) return <ErrorState error={error} onRetry={() => void refresh()} />;

  const skip = (
    <SkipWithPinDialog
      bridge={bridge}
      open={skipOpen}
      onClose={() => setSkipOpen(false)}
      onSkipped={(u) => {
        setSkipOpen(false);
        bridge.toast(`Unlocked by a parent. +${grantedMinutes(u)} min added for today.`, "success");
        void finishQuiz();
      }}
    />
  );

  let body: React.ReactNode;
  if (passed) body = <QuizPassed st={passed} onResume={() => void finishQuiz()} />;
  else if (st.screen === "checklist") body = <Checklist bridge={bridge} st={st} setSt={setSt} onDone={() => void bridge.navigate({ name: "child" })} />;
  else if (st.screen === "launcher") body = <Launcher bridge={bridge} st={st} autoMinimize={autoMinimize} onPin={() => void openPinMenu()} onRefresh={refresh} />;
  else if (st.screen === "nursery") body = <Nursery bridge={bridge} st={st} onDone={(u) => setPassed(u)} />;
  else if (st.screen === "quiz")
    body = (
      <Quiz
        bridge={bridge}
        st={st}
        skipOpen={skipOpen}
        onSkip={() => setSkipOpen(true)}
        onAnswered={(u) => {
          if (u.lastResult?.passed) setPassed(u);
          else if (!(SESSION_SCREENS as readonly string[]).includes(u.screen)) void bridge.navigate({ name: "child" });
          else setSt(u);
        }}
      />
    );
  else if (st.screen === "fail_lock") body = <FailLock bridge={bridge} st={st} onSkip={() => setSkipOpen(true)} onPin={() => void openPinMenu()} onRefresh={refresh} />;
  else if (st.screen === "paused") body = <Paused st={st} onPin={() => void openPinMenu()} />;
  else body = <Ceiling bridge={bridge} st={st} onPin={() => void openPinMenu()} onSkip={() => setSkipOpen(true)} onRefresh={refresh} />;

  return (
    <>
      {error && (
        <div className="fixed left-1/2 top-3 z-[60] w-[min(92vw,480px)] -translate-x-1/2">
          <Alert variant="destructive">{error}</Alert>
        </div>
      )}
      {body}
      {skip}
    </>
  );
}

/* ───────────────────────── Checklist ───────────────────────── */

function Checklist({ bridge, st, setSt, onDone }: { bridge: AppBridge; st: ChildUiState; setSt: (s: ChildUiState) => void; onDone: () => void }) {
  const [busy, setBusy] = React.useState(false);
  const [err, setErr] = React.useState<string | null>(null);
  const done = st.checklist.filter((c) => c.done).length;
  const toggle = async (id: string, v: boolean) => {
    setSt({ ...st, checklist: st.checklist.map((c) => (c.id === id ? { ...c, done: v } : c)) });
    try {
      await bridge.invoke("child_set_checklist_item", { id, done: v });
    } catch (e) {
      setSt(st);
      setErr(friendlyError(e));
    }
  };
  const finish = async () => {
    setBusy(true);
    setErr(null);
    try {
      await bridge.invoke("child_complete_checklist");
      onDone();
    } catch (e) {
      setErr(friendlyError(e));
      setBusy(false);
    }
  };
  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-4 px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="m-0 text-2xl font-bold">Hi {st.childName}</h1>
        <Badge variant="outline" aria-live="polite">
          {done} / {st.checklist.length} ready
        </Badge>
      </div>
      <p className="m-0 text-sm text-muted-foreground">Finish setting up this computer. A parent may need to help with system permissions.</p>
      {err && <Alert variant="destructive">{err}</Alert>}
      <Card className="flex flex-col gap-1 p-4">
        {st.checklist.map((c) => (
          <Switch key={c.id} checked={c.done} onCheckedChange={(v) => void toggle(c.id, v)} label={c.label} description={c.recommended ? "Recommended" : undefined} />
        ))}
      </Card>
      <Button className="self-end" loading={busy} loadingText="Setting up…" onClick={() => void finish()}>
        {t("child_checklist_continue")}
      </Button>
    </div>
  );
}

/* ───────────────────────── Launcher ───────────────────────── */

function Launcher({ bridge, st, autoMinimize, onPin, onRefresh }: { bridge: AppBridge; st: ChildUiState; autoMinimize: boolean; onPin: () => void; onRefresh: () => Promise<void> }) {
  const [countdown, setCountdown] = React.useState<number | null>(autoMinimize ? 5 : null);
  const [autostart, setAutostart] = React.useState<boolean | null>(null);
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => {
    bridge.invoke<boolean>("get_autostart_status").then(setAutostart, () => setAutostart(false));
  }, [bridge]);

  const minimize = React.useCallback(() => {
    setCountdown(null);
    void bridge.invoke("hide_main_window").catch(() => undefined);
  }, [bridge]);

  React.useEffect(() => {
    if (countdown == null) return;
    if (countdown <= 0) {
      minimize();
      return;
    }
    const id = window.setTimeout(() => setCountdown((c) => (c == null ? c : c - 1)), 1000);
    return () => window.clearTimeout(id);
  }, [countdown, minimize]);

  const secLeft = st.secondsRemainingBlock ?? st.minutesRemainingBlock * 60;
  const quizEarly = async () => {
    setBusy(true);
    setCountdown(null);
    try {
      await bridge.invoke("child_force_quiz");
      await onRefresh();
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-6">
      <div className="flex items-center gap-3">
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
          <ShieldCheck className="h-7 w-7" aria-hidden="true" />
        </span>
        <div>
          <h1 className="m-0 text-2xl font-bold">{t("child_launcher_title", { name: st.childName })}</h1>
          <p className="m-0 text-sm text-muted-foreground">MeritScreen is protecting this computer in the background.</p>
        </div>
      </div>

      {countdown != null && (
        <Alert>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span aria-live="polite">Moving to the menu bar in {countdown}s…</span>
            <span className="flex gap-2">
              <Button size="sm" variant="ghost" onClick={() => setCountdown(null)}>
                Stay open
              </Button>
              <Button size="sm" onClick={minimize}>
                <Minimize2 className="h-4 w-4" aria-hidden="true" /> Minimize now
              </Button>
            </span>
          </div>
        </Alert>
      )}

      <section aria-label={t("a11y_launcher_apps")} className="grid gap-3 sm:grid-cols-3">
        <Card className="p-4">
          <p className="m-0 text-xs font-semibold text-muted-foreground">Next quiz in</p>
          <p className="m-0 mt-1 font-mono text-2xl font-bold" aria-live="off">
            {fmtTimer(secLeft)}
          </p>
        </Card>
        <Card className="p-4">
          <p className="m-0 text-xs font-semibold text-muted-foreground">Today</p>
          <p className="m-0 mt-1 text-2xl font-bold">{st.minutesUsedToday} min</p>
          <p className="m-0 text-xs text-muted-foreground">
            {st.dailyLimitMinutes == null
              ? "No daily limit"
              : `${st.dailyRemainingMinutes} min left of ${st.dailyLimitMinutes} today`}
            {(st.bonusMinutesToday ?? 0) > 0 && ` · incl. ${st.bonusMinutesToday} min bonus`}
          </p>
        </Card>
        <Card className="p-4">
          <p className="m-0 text-xs font-semibold text-muted-foreground">Using now</p>
          <p className="m-0 mt-1 truncate text-lg font-bold">{st.activeAppName || "—"}</p>
        </Card>
      </section>

      {st.apps.length > 0 && (
        <Card className="p-4">
          <h2 className="m-0 mb-2 text-sm font-bold">Allowed apps</h2>
          <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
            {st.apps.map((a) => (
              <li key={a.appId}>
                <Badge variant={a.isEmergency ? "success" : "outline"}>
                  {a.displayName}
                  {a.isEmergency && ` · ${t("child_emergency")}`}
                </Badge>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="flex flex-col gap-2 p-4">
        <p className="m-0 text-sm text-muted-foreground">
          Use your allowed apps as usual. Every {st.blockDurationMinutes || "few"} minutes a short quiz appears — pass it to keep going.
        </p>
        {autostart != null && (
          // Read-only for the child: changing start-up behaviour is a parent action (PIN menu / parent device).
          <p className="m-0 text-xs text-muted-foreground">Starts automatically with the computer: {autostart ? "on" : "off"}</p>
        )}
      </Card>

      <StickerBook st={st} />

      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" loading={busy} onClick={() => void quizEarly()}>
          <Rocket className="h-4 w-4" aria-hidden="true" /> Take a quiz early
        </Button>
        <Button variant="secondary" onClick={onPin}>
          <KeyRound className="h-4 w-4" aria-hidden="true" /> {t("parent_pin_label")}
        </Button>
        {bridge.devShortcuts && (
          <Button
            variant="ghost"
            onClick={() => void bridge.invoke("child_tick", { deltaMs: 5 * 60_000 }).then(onRefresh)}
          >
            <Timer className="h-4 w-4" aria-hidden="true" /> {t("lab_plus_5_min")}
          </Button>
        )}
      </div>
    </div>
  );
}

/* ───────────────────────── Quiz ───────────────────────── */

function Overlay({ labelledBy, label, tone = "quiz", children }: { labelledBy?: string; label?: string; tone?: "quiz" | "fail"; children: React.ReactNode }) {
  const ref = React.useRef<HTMLDivElement>(null);
  React.useEffect(() => {
    const first = ref.current?.querySelector<HTMLElement>("button:not([disabled])");
    first?.focus();
  }, []);
  // Portal to <body>: ancestors in the app shell create a containing block that would clip a fixed overlay.
  return createPortal(
    <div
      ref={ref}
      role="dialog"
      aria-modal="true"
      aria-labelledby={labelledBy}
      aria-label={label}
      className={cn("ms-react fixed inset-0 z-50 flex items-center justify-center overflow-y-auto p-4", tone === "quiz" ? "bg-slate-950/95" : "bg-red-950/95")}
    >
      <div className="w-full max-w-xl rounded-2xl border border-white/10 bg-slate-900 p-6 text-slate-50 shadow-2xl sm:p-8">{children}</div>
    </div>,
    document.body,
  );
}

function Quiz({ bridge, st, skipOpen, onSkip, onAnswered }: { bridge: AppBridge; st: ChildUiState; skipOpen: boolean; onSkip: () => void; onAnswered: (u: ChildUiState) => void }) {
  const [busy, setBusy] = React.useState<string | null>(null);
  const q = st.quiz;
  const lock = st.quizLock;

  const answer = React.useCallback(
    async (choiceId: string) => {
      if (busy) return;
      setBusy(choiceId);
      try {
        onAnswered(await bridge.invoke<ChildUiState>("child_answer_quiz", { choiceId }));
      } catch (e) {
        bridge.toast(friendlyError(e), "error");
      } finally {
        setBusy(null);
      }
    },
    [bridge, busy, onAnswered],
  );

  React.useEffect(() => {
    if (!q || skipOpen || (lock && lock.remainingSeconds > 0)) return;
    const onKey = (ev: KeyboardEvent) => {
      if (ev.key >= "1" && ev.key <= "9") {
        const c = q.choices[Number(ev.key) - 1];
        if (c) {
          ev.preventDefault();
          void answer(c.id);
        }
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [q, lock, skipOpen, answer]);

  // Early learners hear the question (Android auto-narrates 3–6 prompts + lock lessons).
  const promptId = q?.id;
  const lockSeconds = lock?.remainingSeconds ?? 0;
  React.useEffect(() => {
    if (st.earlyLearner && promptId && lockSeconds === 0) speak(q?.prompt ?? "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [promptId, st.earlyLearner]);
  React.useEffect(() => {
    if (lockSeconds > 0 && lock) speak(`Not quite. ${lock.lessonBody?.[0] ?? lock.conceptLine} The correct answer is ${lock.correctChoiceText ?? ""}.`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lock?.conceptLine]);

  const skipBtn = (
    <div className="mt-5 border-t border-white/10 pt-4 text-center">
      <Button variant="ghost" size="sm" className="text-slate-300 hover:bg-white/10 hover:text-white" onClick={onSkip}>
        <KeyRound className="h-4 w-4" aria-hidden="true" /> Skip with parent PIN
      </Button>
    </div>
  );

  if (lock && lock.remainingSeconds > 0) {
    return (
      <Overlay labelledBy="quiz-lock-title">
        <h1 id="quiz-lock-title" className="m-0 flex items-center gap-2 text-2xl font-bold text-amber-300">
          <BookOpen className="h-6 w-6" aria-hidden="true" /> {t("child_quiz_lock_title")}
        </h1>
        {lock.whyLine && <p className="mt-2 text-base text-red-300">{lock.whyLine}</p>}
        <div className="mt-3 rounded-lg bg-white/5 p-3">
          <strong>{lock.lessonTitle || "Remember"}</strong>
          {(lock.lessonBody ?? [lock.conceptLine]).map((l, i) => (
            <p key={i} className="m-0 mt-1 text-slate-200">{l}</p>
          ))}
          {lock.correctChoiceText && (
            <p className="m-0 mt-2 font-semibold text-emerald-300">Correct answer: {lock.correctChoiceText}</p>
          )}
        </div>
        <button type="button" onClick={() => speak(`${lock.lessonTitle ?? ""}. ${(lock.lessonBody ?? []).join(" ")} The correct answer is ${lock.correctChoiceText ?? ""}.`)} className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-sm font-semibold text-white hover:bg-white/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400">
          <Volume2 className="h-4 w-4" aria-hidden="true" /> Listen
        </button>
        <p className="my-4 font-mono text-5xl font-bold text-amber-400" aria-live="polite" aria-atomic="true" aria-label={t("a11y_cooldown_timer", { seconds: lock.remainingSeconds })}>
          {lock.remainingSeconds}s
        </p>
        <p className="m-0 text-sm text-slate-400">{t("child_quiz_lock_body")}</p>
        {skipBtn}
      </Overlay>
    );
  }

  if (!q) {
    return (
      <Overlay label={t("a11y_quiz_dialog")}>
        <h1 className="m-0 text-xl font-bold">{t("a11y_quiz_dialog")}</h1>
        <p className="text-slate-400">{st.quizError ?? "Loading question…"}</p>
      </Overlay>
    );
  }

  const visual = q.interactionType && q.interactionType !== "TAP_TEXT";
  const promptVisual = q.promptTag ? visualEmoji(q.promptTag) : "";

  const fb = st.lastFeedback;
  return (
    <Overlay labelledBy="quiz-prompt">
      <Badge variant="outline" className="border-white/20 text-slate-200">
        {st.activeAppName ? `${st.activeAppName} is paused for a quiz` : "Time for a quick quiz"}
      </Badge>
      <p className="mb-1 mt-3 text-sm text-slate-400" aria-live="polite">
        {t("child_quiz_progress", { current: q.index + 1, total: q.total })}
      </p>
      <div className="mb-4 h-1.5 rounded-full bg-white/10" aria-hidden="true">
        <div className="h-1.5 rounded-full bg-emerald-400" style={{ width: `${(q.index / Math.max(1, q.total)) * 100}%` }} />
      </div>
      <h1 id="quiz-prompt" className="m-0 mb-4 text-2xl font-bold leading-snug text-white">
        {q.prompt}
        <button type="button" onClick={() => speak(q.prompt)} aria-label="Listen to the question" className="ml-2 inline-flex h-8 w-8 items-center justify-center rounded-full bg-white/10 align-middle text-white hover:bg-white/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400">
          <Volume2 className="h-4 w-4" aria-hidden="true" />
        </button>
      </h1>
      {visual && (promptVisual || (q.promptCount ?? 0) > 0) && (
        <div className="mb-4 flex flex-wrap items-center gap-1 text-4xl" aria-hidden="true">
          {promptVisual && q.promptCount ? Array.from({ length: q.promptCount }, (_, i) => <span key={i}>{promptVisual}</span>) : promptVisual}
        </div>
      )}
      {(q.teachTitle || q.teachBody.length > 0) && (
        <div className="mb-4 rounded-lg bg-white/5 p-3 text-sm">
          <strong>{q.teachTitle ?? "Remember"}</strong>
          {q.teachBody.map((l, i) => (
            <p key={i} className="m-0 mt-1 text-slate-300">
              {l}
            </p>
          ))}
        </div>
      )}
      {fb && st.showExplanations !== false && (
        <p className={cn("mb-3 text-sm font-semibold", fb.correct ? "text-emerald-400" : "text-red-400")} aria-live="polite">
          {fb.resultLine} — {fb.whyLine}
        </p>
      )}
      <div role="group" aria-label={t("a11y_quiz_choices")} className="flex flex-col gap-3">
        {q.choices.map((c, i) => (
          <button
            key={c.id}
            type="button"
            aria-keyshortcuts={String(i + 1)}
            disabled={busy != null}
            onClick={() => void answer(c.id)}
            className="flex cursor-pointer items-center justify-between gap-3 rounded-xl border border-white/15 bg-white/5 px-4 py-3.5 text-left text-base font-semibold text-white transition hover:border-emerald-400 hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 disabled:opacity-60"
          >
            <span className="flex items-center gap-3">
              {visualEmoji(c.imageTag) && <span className="text-4xl" aria-hidden="true">{visualEmoji(c.imageTag)}</span>}
              {c.text !== visualEmoji(c.imageTag) && <span>{c.text || visualLabel(c.imageTag)}</span>}
            </span>
            <span aria-hidden="true" className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white/10 font-mono text-sm">
              {i + 1}
            </span>
          </button>
        ))}
      </div>
      <p className="mt-4 text-center text-xs text-slate-400">Press 1–{q.choices.length} to answer. {t("child_quiz_no_skip")}</p>
      {skipBtn}
    </Overlay>
  );
}

function QuizPassed({ st, onResume }: { st: ChildUiState; onResume: () => void }) {
  const [n, setN] = React.useState(3);
  React.useEffect(() => {
    if (n <= 0) {
      onResume();
      return;
    }
    const id = window.setTimeout(() => setN(n - 1), 1000);
    return () => window.clearTimeout(id);
  }, [n, onResume]);
  return (
    <Overlay labelledBy="quiz-passed-title">
      <div className="flex flex-col items-center gap-3 text-center">
        <PartyPopper className="h-12 w-12 text-emerald-400" aria-hidden="true" />
        <h1 id="quiz-passed-title" className="m-0 text-3xl font-bold text-emerald-400">
          Great job!
        </h1>
        <p className="m-0 text-lg">
          You passed. <strong>+{grantedMinutes(st)} minutes</strong> of screen time.
        </p>
        {st.lastResult && st.lastResult.total > 0 && (
          <p className="m-0 text-sm text-slate-400">
            {st.lastResult.correctCount} of {st.lastResult.total} correct ({st.lastResult.percent}%)
          </p>
        )}
        {st.lastAward && (st.lastAward.xpGained > 0 || st.lastAward.stickerTitle) && (
          <div className="flex flex-col items-center gap-1" aria-live="polite">
            {st.lastAward.stickerTitle && (
              <p className="m-0 rounded-full bg-amber-400/20 px-4 py-1.5 text-lg font-bold text-amber-300">
                {st.lastAward.stickerEmoji} New sticker: {st.lastAward.stickerTitle}
                {st.lastAward.stickerStageLabel && <span className="text-sm font-semibold"> · {st.lastAward.stickerStageLabel}</span>}
              </p>
            )}
            <p className="m-0 text-sm text-slate-300">
              +{st.lastAward.xpGained} XP{st.explorer && ` · Explorer level ${st.explorer.explorerLevel}`}
              {st.lastAward.leveledUp && " · Level up! 🎉"}
              {st.lastAward.weekendBonusApplied && " · Weekend bonus"}
            </p>
          </div>
        )}
        <p className="m-0 text-sm text-slate-400" aria-live="polite">
          Continuing in {n}s…
        </p>
        <Button onClick={onResume}>
          <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> Continue now
        </Button>
      </div>
    </Overlay>
  );
}

/* ───────────────────────── Fail lock & daily limit ───────────────────────── */

function FailLock({ bridge, st, onSkip, onPin, onRefresh }: { bridge: AppBridge; st: ChildUiState; onSkip: () => void; onPin: () => void; onRefresh: () => Promise<void> }) {
  const r = st.lastResult;
  return (
    <Overlay tone="fail" labelledBy="fail-title" label={t("a11y_fail_lock_dialog")}>
      <h1 id="fail-title" className="m-0 text-2xl font-bold text-red-300">
        {t("child_fail_lock_title")}
      </h1>
      <p className="mt-2">{t("child_fail_lock_body")}</p>
      {r && (
        <p className="m-0 text-sm text-slate-400">
          Score {r.correctCount}/{r.total} ({r.percent}%)
        </p>
      )}
      <p className="my-4 font-mono text-5xl font-bold text-red-300" aria-live="polite" aria-atomic="true" aria-label={t("a11y_cooldown_timer", { seconds: st.cooldownSeconds })}>
        {fmtTimer(st.cooldownSeconds)}
      </p>
      <div className="flex flex-wrap gap-2">
        {st.canRetryDuringCooldown && (
          <Button
            onClick={() => void bridge.invoke("child_retry_quiz").then(() => onRefresh()).catch((e) => bridge.toast(friendlyError(e), "error"))}
          >
            <RotateCcw className="h-4 w-4" aria-hidden="true" /> Try the quiz again
          </Button>
        )}
        <Button variant="secondary" onClick={onSkip}>
          <KeyRound className="h-4 w-4" aria-hidden="true" /> Skip with parent PIN
        </Button>
        <Button variant="ghost" className="text-slate-300 hover:bg-white/10 hover:text-white" onClick={onPin}>
          {t("parent_pin_label")}
        </Button>
        {bridge.devShortcuts && (
          <Button variant="ghost" className="text-slate-300" onClick={() => void bridge.invoke("child_tick", { deltaMs: 30_000 }).then(onRefresh)}>
            {t("lab_advance_30s")}
          </Button>
        )}
      </div>
    </Overlay>
  );
}

function Ceiling({ bridge, st, onPin, onSkip, onRefresh }: { bridge: AppBridge; st: ChildUiState; onPin: () => void; onSkip: () => void; onRefresh: () => Promise<void> }) {
  const [busy, setBusy] = React.useState(false);
  const earn = async () => {
    setBusy(true);
    try {
      await bridge.invoke("child_start_ceiling_quiz");
      await onRefresh();
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
      setBusy(false);
    }
  };
  return (
    <Overlay tone="fail" labelledBy="ceiling-title">
      <h1 id="ceiling-title" className="m-0 text-2xl font-bold">
        {t("child_ceiling_title")}
      </h1>
      <p className="mt-2">{t("child_ceiling_body", { minutes: st.minutesUsedToday })}</p>
      {st.dailyLimitMinutes != null && (
        <p className="text-sm text-slate-400">
          {st.minutesUsedToday} of {st.dailyLimitMinutes} minutes used today
          {(st.bonusMinutesToday ?? 0) > 0 && ` · includes a ${st.bonusMinutesToday} min parent bonus`}
        </p>
      )}
      <p className="text-sm text-slate-400">Emergency apps still work. If a parent gives you more time, this screen will open by itself.</p>
      <div className="flex flex-wrap gap-2">
        {st.canEarnBeyondCeiling && (
          <Button loading={busy} loadingText="Starting quiz…" onClick={() => void earn()}>
            <BookOpen className="h-4 w-4" aria-hidden="true" /> Take a quiz to earn more time
          </Button>
        )}
        <Button variant="secondary" onClick={onSkip}>
          <KeyRound className="h-4 w-4" aria-hidden="true" /> Unlock with parent PIN
        </Button>
        <Button variant="ghost" className="text-slate-300 hover:bg-white/10 hover:text-white" onClick={onPin}>
          {t("parent_pin_label")}
        </Button>
      </div>
    </Overlay>
  );
}

function Paused({ st, onPin }: { st: ChildUiState; onPin: () => void }) {
  return (
    <Overlay tone="fail" labelledBy="paused-title">
      <h1 id="paused-title" className="m-0 text-2xl font-bold">
        Paused by your parent
      </h1>
      <p className="mt-2">Screen time is paused for {st.childName} right now. Emergency apps still work.</p>
      <Button variant="secondary" onClick={onPin}>
        <KeyRound className="h-4 w-4" aria-hidden="true" /> {t("parent_pin_label")}
      </Button>
    </Overlay>
  );
}

/* ───────────────────────── Parent PIN skip ───────────────────────── */

function SkipWithPinDialog({ bridge, open, onClose, onSkipped }: { bridge: AppBridge; open: boolean; onClose: () => void; onSkipped: (u: ChildUiState) => void }) {
  const [pin, setPin] = React.useState("");
  const [err, setErr] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    if (open) {
      setPin("");
      setErr(null);
    }
  }, [open]);
  const submit = async (value: string) => {
    if (value.length !== 4 || busy) return;
    setBusy(true);
    setErr(null);
    try {
      onSkipped(await bridge.invoke<ChildUiState>("child_skip_quiz_with_pin", { pin: value }));
    } catch (e) {
      setErr(friendlyError(e));
      setPin("");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Parent PIN"
      description="A parent can enter their PIN to skip this quiz."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} disabled={pin.length !== 4} onClick={() => void submit(pin)}>
            Skip quiz
          </Button>
        </>
      }
    >
      <PinInput value={pin} onChange={setPin} onComplete={(v) => void submit(v)} invalid={!!err} autoFocus disabled={busy} />
      {err && <p className="m-0 text-sm font-semibold text-destructive" role="alert">{err}</p>}
    </Dialog>
  );
}

/* ───────────────────────── Nursery (early learners, teach first) ───────────────────────── */

function Nursery({ bridge, st, onDone }: { bridge: AppBridge; st: ChildUiState; onDone: (u: ChildUiState) => void }) {
  const n = st.nursery;
  const [showCards, setShowCards] = React.useState(!n?.video);
  const [busy, setBusy] = React.useState(false);
  const [idx, setIdx] = React.useState(0);

  React.useEffect(() => {
    if (n?.video) speak(`Let's watch and learn, ${st.childName}!`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!n) {
    return (
      <Overlay label="Nursery">
        <h1 className="m-0 text-xl font-bold">Loading your lesson…</h1>
      </Overlay>
    );
  }
  const item = n.items[Math.min(idx, Math.max(0, n.items.length - 1))];
  const finish = async () => {
    setBusy(true);
    try {
      onDone(await bridge.invoke<ChildUiState>("child_complete_nursery"));
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
      setBusy(false);
    }
  };
  return (
    <Overlay labelledBy="nursery-title">
      <Badge variant="outline" className="border-white/20 text-slate-200">Learning time, {st.childName}!</Badge>
      <h1 id="nursery-title" className="m-0 mb-3 mt-3 text-3xl font-bold text-white">
        {showCards ? "Tap, listen and learn" : n.video?.title}
      </h1>
      {!showCards && n.video ? (
        <div className="flex flex-col gap-3">
          <div className="aspect-video w-full overflow-hidden rounded-xl bg-black">
            <iframe
              title={n.video.title}
              src={`https://www.youtube-nocookie.com/embed/${n.video.videoId}?rel=0`}
              className="h-full w-full"
              allow="accelerometer; encrypted-media; picture-in-picture"
              allowFullScreen
            />
          </div>
          <p className="m-0 text-sm text-slate-400">
            {n.video.channel} · up to {Math.round(n.video.maxSeconds / 60)} min
          </p>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setShowCards(true)}>
              <BookOpen className="h-4 w-4" aria-hidden="true" /> Use learning cards instead
            </Button>
            <Button loading={busy} onClick={() => void finish()}>
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> I finished watching
            </Button>
          </div>
        </div>
      ) : item ? (
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex h-36 w-full flex-col items-center justify-center gap-1 rounded-2xl" style={{ backgroundColor: item.colorHex }}>
            <span className="text-7xl" aria-hidden="true">{item.emoji}</span>
            {item.count != null && <span className="text-lg font-bold text-slate-800">{Array.from({ length: item.count }, () => item.emoji).join(" ")}</span>}
          </div>
          <h2 className="m-0 text-2xl font-bold text-white">{item.title}</h2>
          <p className="m-0 text-slate-300">{item.speechText}</p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button variant="secondary" onClick={() => speak(item.speechText)}>
              <Volume2 className="h-4 w-4" aria-hidden="true" /> Listen
            </Button>
            {idx < n.items.length - 1 ? (
              <Button onClick={() => { const next = idx + 1; setIdx(next); speak(n.items[next].speechText); }}>
                <Play className="h-4 w-4" aria-hidden="true" /> Next card
              </Button>
            ) : (
              <Button loading={busy} onClick={() => void finish()}>
                <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> All done!
              </Button>
            )}
          </div>
          <p className="m-0 text-xs text-slate-400" aria-live="polite">Card {idx + 1} of {n.items.length}</p>
        </div>
      ) : (
        <Button loading={busy} onClick={() => void finish()}>Continue</Button>
      )}
    </Overlay>
  );
}

/* ───────────────────────── Sticker book (launcher) ───────────────────────── */

function StickerBook({ st }: { st: ChildUiState }) {
  const ex = st.explorer;
  if (!ex) return null;
  const owned = st.stickers ?? [];
  return (
    <Card className="flex flex-col gap-2 p-4" aria-labelledby="sticker-book-title">
      <h2 id="sticker-book-title" className="m-0 flex flex-wrap items-center gap-2 text-sm font-bold">
        <Trophy className="h-4 w-4 text-amber-500" aria-hidden="true" /> My sticker book
        <Badge variant="outline">Explorer level {ex.explorerLevel}</Badge>
        <span className="font-semibold text-muted-foreground">{ex.xp} XP</span>
        {ex.passStreakDays > 1 && (
          <span className="inline-flex items-center gap-1 font-semibold text-amber-600">
            <Flame className="h-4 w-4" aria-hidden="true" /> {ex.passStreakDays}-day streak
          </span>
        )}
      </h2>
      {owned.length ? (
        <ul className="m-0 flex list-none flex-wrap gap-2 p-0" aria-label="My stickers">
          {owned.map((u) => (
            <li key={u.unlockId} title={`${u.title} (${u.stage.toLowerCase()})`} className="flex items-center gap-1.5 rounded-full bg-secondary px-3 py-1.5 text-sm font-semibold">
              <span aria-hidden="true">{u.emoji}</span> {u.title}
            </li>
          ))}
        </ul>
      ) : (
        <p className="m-0 flex items-center gap-1.5 text-sm text-muted-foreground">
          <Sparkles className="h-4 w-4" aria-hidden="true" /> Pass a quiz to earn your first sticker!
        </p>
      )}
      <p className="m-0 text-xs text-muted-foreground">{owned.length} of 20 stickers collected</p>
    </Card>
  );
}
