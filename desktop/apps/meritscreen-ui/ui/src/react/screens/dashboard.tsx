import * as React from "react";
import {
  AlertTriangle, BarChart3, Battery, Brain, ChevronRight, Cloud, Flame, HardDrive, Laptop, Pause, Play, Plus,
  RefreshCw, Settings, ShieldAlert, Smartphone, Sparkles, Trophy, Users, Zap,
} from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildCard, ChildDetail, ChildInsights, DashboardSnapshot, FamilyInsights, InsightAlert, UiStateDto } from "../lib/types";
import { Button } from "../ui/button";
import { Card } from "../ui/card";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { EmptyState, ErrorState } from "../components/states";
import { Page } from "../components/page";
import { ageLabel, avatarFor, BONUS_MAX_PER_DAY, formatMinutes, MAX_CHILDREN } from "../lib/catalog";
import { friendlyError } from "../lib/errors";
import { cn } from "../lib/utils";

const QUICK_BONUS = 15;

export function DashboardScreen({ bridge }: { bridge: AppBridge }) {
  const [snap, setSnap] = React.useState<DashboardSnapshot | null>(null);
  const [insights, setInsights] = React.useState<FamilyInsights | null>(null);
  const [insightsError, setInsightsError] = React.useState<string | null>(null);
  const [state, setState] = React.useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = React.useState<string | null>(null);
  const [refreshing, setRefreshing] = React.useState(false);
  const [busy, setBusy] = React.useState<string | null>(null);
  const [selected, setSelected] = React.useState<string | null>(null);

  const loadInsights = React.useCallback(async () => {
    try {
      setInsights(await bridge.invoke<FamilyInsights>("parent_family_insights"));
      setInsightsError(null);
    } catch (e) {
      setInsightsError(friendlyError(e));
    }
  }, [bridge]);

  const apply = React.useCallback(
    async (dto: UiStateDto<DashboardSnapshot>) => {
      if (dto.state === "error") throw dto.error ?? "Couldn’t load your family.";
      setSnap(dto.data);
      setState("ready");
      await loadInsights();
    },
    [loadInsights],
  );

  const load = React.useCallback(async () => {
    setError(null);
    setState("loading");
    try {
      await apply(await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_dashboard"));
    } catch (e) {
      setError(friendlyError(e));
      setState("error");
    }
  }, [bridge, apply]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const refresh = async () => {
    setRefreshing(true);
    try {
      await apply(await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_refresh"));
      bridge.toast("Up to date", "info");
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setRefreshing(false);
    }
  };

  const patchInsight = (childId: string, patch: Partial<ChildInsights>) =>
    setInsights((fi) => fi && { ...fi, children: fi.children.map((c) => (c.childId === childId ? { ...c, ...patch } : c)) });

  const togglePause = async (c: ChildCard) => {
    setBusy(`pause:${c.childId}`);
    try {
      const d = await bridge.invoke<ChildDetail>("parent_child_detail", { childId: c.childId });
      const next = !d.policy.paused;
      await bridge.invoke("parent_update_policy", { childId: c.childId, policy: { ...d.policy, paused: next } });
      patchInsight(c.childId, { paused: next });
      bridge.toast(next ? `${c.displayName}’s screen time is paused` : `${c.displayName} can use the computer again`, "info");
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(null);
    }
  };

  const grantBonus = async (c: ChildCard) => {
    setBusy(`bonus:${c.childId}`);
    try {
      await bridge.invoke("parent_grant_bonus", { childId: c.childId, minutes: QUICK_BONUS });
      bridge.toast(`+${QUICK_BONUS} min for ${c.displayName} today`, "success");
      await loadInsights();
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(null);
    }
  };

  if (state === "loading") return <DashboardSkeleton />;
  if (state === "error") return <ErrorState error={error} onRetry={() => void load()} />;

  const children = snap?.children ?? [];
  const max = snap?.maxChildren || MAX_CHILDREN;
  const atMax = children.length >= max;
  const byId = new Map((insights?.children ?? []).map((i) => [i.childId, i]));
  const focusId = selected && children.some((c) => c.childId === selected) ? selected : children[0]?.childId;
  const focusCard = children.find((c) => c.childId === focusId);
  const focus = focusId ? byId.get(focusId) : undefined;

  const ins = insights?.children ?? [];
  const usedToday = ins.length ? ins.reduce((a, c) => a + c.usedTodayMinutes, 0) : children.reduce((a, c) => a + c.minutesUsedToday, 0);
  const protectedCount = children.filter((c) => c.pairedDeviceCount > 0 || (byId.get(c.childId)?.devices.length ?? 0) > 0).length;
  const onlineDevices = ins.reduce((a, c) => a + c.devices.filter((d) => d.online).length, 0);
  const attempts = ins.reduce((a, c) => a + c.quiz.attemptsWeek, 0);
  const passes = ins.reduce((a, c) => a + c.quiz.passesWeek, 0);
  const rank = { critical: 0, warning: 1, info: 2 } as const;
  const alerts = ins.flatMap((c) => c.alerts.map((a) => ({ ...a, childId: c.childId }))).sort((a, b) => rank[a.severity] - rank[b.severity]);

  return (
    <Page
      title="Family"
      description={snap?.profile?.email ? `Signed in as ${snap.profile.email}` : undefined}
      width="2xl"
      actions={
        <>
          <Button variant="secondary" onClick={() => void refresh()} loading={refreshing} loadingText="Refreshing…">
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Refresh
          </Button>
          <Button variant="secondary" onClick={() => void bridge.navigate({ name: "account" })}>
            <Settings className="h-4 w-4" aria-hidden="true" /> Account
          </Button>
          <Button onClick={() => void bridge.navigate({ name: "parent-add-child" })} disabled={atMax} title={atMax ? `You can add up to ${max} children` : undefined}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Add child
          </Button>
        </>
      }
    >
      {children.length === 0 ? (
        <EmptyState
          icon={<Users className="h-10 w-10 text-muted-foreground" aria-hidden="true" />}
          title="No children yet"
          body="Add a child profile, then pair their computer or phone."
          action={<Button onClick={() => void bridge.navigate({ name: "parent-add-child" })}>Add your first child</Button>}
        />
      ) : (
        <>
          <section aria-label="Family summary" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat icon={<Users className="h-4 w-4" />} label="Children" value={`${children.length} / ${max}`} />
            <Stat icon={<Laptop className="h-4 w-4" />} label="Protected" value={`${protectedCount} of ${children.length}`} hint={insights ? `${onlineDevices} device${onlineDevices === 1 ? "" : "s"} online now` : undefined} />
            <Stat icon={<BarChart3 className="h-4 w-4" />} label="Screen time today" value={formatMinutes(usedToday)} />
            <Stat icon={<Brain className="h-4 w-4" />} label="Quizzes this week" value={insights ? `${passes} / ${attempts} passed` : "—"} hint={attempts ? `${Math.round((passes / attempts) * 100)}% pass rate` : insights ? "No quizzes yet" : undefined} />
          </section>

          {atMax && <Alert>You’ve reached the limit of {max} children.</Alert>}
          {insightsError && (
            <Alert variant="destructive" className="flex flex-wrap items-center justify-between gap-2">
              <span>Couldn’t load activity: {insightsError}</span>
              <Button size="sm" variant="secondary" onClick={() => void loadInsights()}>Try again</Button>
            </Alert>
          )}
          {insights && <SourceNote insights={insights} />}

          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
            <ul className="m-0 grid list-none gap-3 p-0 sm:grid-cols-2 2xl:grid-cols-3" aria-label="Children">
              {children.map((c) => (
                <li key={c.childId}>
                  <ChildSummaryCard
                    card={c}
                    insight={byId.get(c.childId)}
                    loading={!insights && !insightsError}
                    selected={c.childId === focusId}
                    busy={busy}
                    onSelect={() => setSelected(c.childId)}
                    onPause={() => void togglePause(c)}
                    onBonus={() => void grantBonus(c)}
                    onManage={() => void bridge.navigate({ name: "child-detail", childId: c.childId })}
                    onPair={() => void bridge.navigate({ name: "pairing", childId: c.childId })}
                  />
                </li>
              ))}
            </ul>
            <AlertsPanel alerts={alerts} loading={!insights && !insightsError} names={new Map(children.map((c) => [c.childId, c.displayName]))} />
          </div>

          {focusCard && (
            <section aria-labelledby="focus-title" className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 id="focus-title" className="m-0 text-lg font-bold">
                  {focusCard.displayName}’s week
                </h2>
                <div className="flex flex-wrap items-center gap-2">
                  {children.length > 1 && (
                    <div role="tablist" aria-label="Choose child" className="flex flex-wrap gap-1">
                      {children.map((c) => (
                        <Button
                          key={c.childId}
                          role="tab"
                          size="sm"
                          aria-selected={c.childId === focusId}
                          variant="tab"
                          onClick={() => setSelected(c.childId)}
                        >
                          {c.displayName}
                        </Button>
                      ))}
                    </div>
                  )}
                  <Button size="sm" variant="ghost" onClick={() => void bridge.navigate({ name: "reports", childId: focusCard.childId })}>
                    Full report <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              </div>
              {focus ? <ChildAnalytics insight={focus} /> : insightsError ? null : <AnalyticsSkeleton />}
            </section>
          )}
        </>
      )}
    </Page>
  );
}

/* ───────────────────────── pieces ───────────────────────── */

function Stat({ icon, label, value, hint }: { icon: React.ReactNode; label: string; value: string; hint?: string }) {
  return (
    <Card className="p-4">
      <p className="m-0 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
        <span aria-hidden="true">{icon}</span>
        {label}
      </p>
      <p className="m-0 mt-1 text-xl font-bold">{value}</p>
      {hint && <p className="m-0 mt-0.5 text-xs text-muted-foreground">{hint}</p>}
    </Card>
  );
}

function SourceNote({ insights }: { insights: FamilyInsights }) {
  const time = new Date(insights.generatedAtMs).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  return (
    <p className="m-0 -mt-2 flex items-center gap-1.5 text-xs text-muted-foreground">
      {insights.source === "cloud" ? <Cloud className="h-3.5 w-3.5" aria-hidden="true" /> : <HardDrive className="h-3.5 w-3.5" aria-hidden="true" />}
      {insights.source === "cloud" ? `Synced from your family’s devices · updated ${time}` : `Showing activity recorded on this computer · updated ${time}`}
    </p>
  );
}

function UsageRing({ used, total, paused }: { used: number; total: number | null; paused: boolean }) {
  const pct = total ? Math.min(1, used / Math.max(1, total)) : 0;
  const r = 26;
  const c = 2 * Math.PI * r;
  const tone = paused || (total != null && used >= total) ? "stroke-destructive" : pct >= 0.8 ? "stroke-amber-500" : "stroke-primary";
  return (
    <svg viewBox="0 0 64 64" className="h-16 w-16 shrink-0 -rotate-90" aria-hidden="true">
      <circle cx="32" cy="32" r={r} className="fill-none stroke-muted" strokeWidth="7" />
      {total != null && <circle cx="32" cy="32" r={r} className={cn("fill-none transition-all", tone)} strokeWidth="7" strokeLinecap="round" strokeDasharray={`${pct * c} ${c}`} />}
    </svg>
  );
}

function ChildSummaryCard({
  card, insight, loading, selected, busy, onSelect, onPause, onBonus, onManage, onPair,
}: {
  card: ChildCard; insight?: ChildInsights; loading: boolean; selected: boolean; busy: string | null;
  onSelect: () => void; onPause: () => void; onBonus: () => void; onManage: () => void; onPair: () => void;
}) {
  const used = insight?.usedTodayMinutes ?? card.minutesUsedToday;
  const limit = insight ? insight.dailyLimitMinutes : null;
  const bonus = insight?.bonusMinutesToday ?? 0;
  const total = limit == null ? null : limit + bonus;
  const remaining = insight ? insight.remainingTodayMinutes : card.minutesRemainingToday;
  const paused = insight?.paused ?? false;
  const paired = card.pairedDeviceCount > 0 || (insight?.devices.length ?? 0) > 0;
  const online = insight?.devices.some((d) => d.online) ?? false;
  const last = insight?.quiz.recent[0];
  return (
    <Card className={cn("flex h-full flex-col gap-3 p-4 transition-shadow", selected && "ring-2 ring-primary")}>
      <Button variant="tab" onClick={onSelect} className="h-auto w-full justify-start gap-3 px-1 py-1 text-left font-normal" aria-label={`Show ${card.displayName}’s week`} aria-pressed={selected}>
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-secondary text-2xl" aria-hidden="true">
          {avatarFor(card.childId, card.displayName)}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-base font-bold">{card.displayName}</span>
          <span className="block text-xs text-muted-foreground">{ageLabel(card.ageBand)}</span>
        </span>
        {paused ? <Badge variant="destructive">Paused</Badge> : paired ? <Badge variant="success">{online ? "Online" : "Protected"}</Badge> : <Badge variant="outline">Not paired</Badge>}
      </Button>

      <div className="flex items-center gap-3">
        <div className="relative">
          <UsageRing used={used} total={total} paused={paused} />
        </div>
        <dl className="m-0 grid flex-1 grid-cols-2 gap-x-3 gap-y-1 text-sm">
          <dt className="text-xs text-muted-foreground">Used today</dt>
          <dt className="text-xs text-muted-foreground">Remaining</dt>
          <dd className="m-0 font-bold">{formatMinutes(used)}</dd>
          <dd className="m-0 font-bold">{remaining == null ? "No limit" : formatMinutes(remaining)}</dd>
          <dd className="col-span-2 m-0 text-xs text-muted-foreground">
            {loading ? <Skel className="h-3 w-32" /> : total == null ? "No daily limit set" : `of ${formatMinutes(total)}${bonus ? ` · incl. ${bonus} min bonus` : ""}`}
          </dd>
        </dl>
      </div>

      <p className="m-0 min-h-4 text-xs text-muted-foreground">
        {loading ? (
          <Skel className="h-3 w-40" />
        ) : last ? (
          <>
            Last quiz: <span className={last.passed ? "font-semibold text-success" : "font-semibold text-destructive"}>{last.passed ? "Passed" : "Not yet"}</span> {last.total > 0 && `${last.score}/${last.total}`} {last.topic && `· ${last.topic}`} · {relTime(last.atMs)}
          </>
        ) : (
          "No quizzes yet"
        )}
      </p>

      <div className="mt-auto flex flex-wrap gap-2">
        <Button size="sm" onClick={onManage}>
          Manage <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
        {!paired ? (
          <Button size="sm" variant="secondary" onClick={onPair}>
            Pair device
          </Button>
        ) : (
          <>
            <Button size="sm" variant="secondary" loading={busy === `pause:${card.childId}`} onClick={onPause} aria-label={paused ? `Resume ${card.displayName}` : `Pause ${card.displayName}`}>
              {paused ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
              {paused ? "Resume" : "Pause"}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              loading={busy === `bonus:${card.childId}`}
              onClick={onBonus}
              disabled={!insight || bonus + QUICK_BONUS > BONUS_MAX_PER_DAY}
              aria-label={`Give ${card.displayName} ${QUICK_BONUS} bonus minutes today`}
            >
              <Zap className="h-4 w-4" aria-hidden="true" /> +{QUICK_BONUS}m
            </Button>
          </>
        )}
      </div>
    </Card>
  );
}

const SEVERITY_STYLE: Record<InsightAlert["severity"], string> = {
  critical: "border-l-destructive",
  warning: "border-l-amber-500",
  info: "border-l-primary",
};

function AlertsPanel({ alerts, loading, names }: { alerts: (InsightAlert & { childId: string })[]; loading: boolean; names: Map<string, string> }) {
  return (
    <Card className="flex flex-col gap-3 p-4" aria-labelledby="alerts-title">
      <h2 id="alerts-title" className="m-0 flex items-center gap-2 text-base font-bold">
        <ShieldAlert className="h-4 w-4" aria-hidden="true" /> Needs attention
        {alerts.length > 0 && <Badge>{alerts.length}</Badge>}
      </h2>
      {loading ? (
        <div className="flex flex-col gap-2">
          <Skel className="h-10" />
          <Skel className="h-10" />
          <Skel className="h-10" />
        </div>
      ) : alerts.length === 0 ? (
        <p className="m-0 text-sm text-muted-foreground">All clear — nothing needs your attention right now.</p>
      ) : (
        <ul className="m-0 flex max-h-96 list-none flex-col gap-2 overflow-auto p-0" aria-label="Alerts">
          {alerts.map((a, i) => (
            <li key={`${a.childId}-${a.kind}-${i}`} className={cn("rounded-md border-l-4 bg-muted px-3 py-2 text-sm", SEVERITY_STYLE[a.severity])}>
              <span className="flex items-start gap-2">
                {a.severity !== "info" && <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
                <span>
                  <span className="sr-only">{a.severity}: </span>
                  {a.message}
                  <span className="sr-only"> ({names.get(a.childId)})</span>
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function ChildAnalytics({ insight: i }: { insight: ChildInsights }) {
  return (
    <div className="flex flex-col gap-3">
      {i.error && <Alert variant="destructive">Couldn’t sync {i.displayName}’s cloud activity ({i.error}). Showing what this computer knows.</Alert>}
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <WeeklyChart insight={i} />
        <TopApps insight={i} />
        <QuizCard insight={i} />
        <SkillsCard insight={i} />
        <RewardsCard insight={i} />
        <DevicesCard insight={i} />
      </div>
    </div>
  );
}

function Panel({ title, icon, children, className }: { title: string; icon: React.ReactNode; children: React.ReactNode; className?: string }) {
  const id = React.useId();
  return (
    <Card className={cn("flex flex-col gap-3 p-4", className)} role="region" aria-labelledby={id}>
      <h3 id={id} className="m-0 flex items-center gap-2 text-sm font-bold">
        <span aria-hidden="true" className="text-muted-foreground">{icon}</span>
        {title}
      </h3>
      {children}
    </Card>
  );
}

function WeeklyChart({ insight: i }: { insight: ChildInsights }) {
  const max = Math.max(1, i.dailyLimitMinutes ?? 0, ...i.trend.map((p) => p.minutes));
  const limitPct = i.dailyLimitMinutes ? (i.dailyLimitMinutes / max) * 100 : null;
  const delta = i.weekDeltaPercent;
  return (
    <Panel title="Screen time · last 7 days" icon={<BarChart3 className="h-4 w-4" />} className="md:col-span-2 xl:col-span-1">
      <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <p className="m-0 text-2xl font-bold">{formatMinutes(i.weekTotalMinutes)}</p>
        <p className="m-0 text-xs text-muted-foreground">avg {formatMinutes(i.dailyAverageMinutes)} / active day</p>
        {delta != null && (
          <Badge variant={delta > 10 ? "destructive" : "success"}>
            {delta > 0 ? "▲" : delta < 0 ? "▼" : "■"} {Math.abs(delta)}% vs last week
          </Badge>
        )}
      </div>
      <div className="relative flex h-36 items-end gap-2" role="img" aria-label={`Daily minutes: ${i.trend.map((p) => `${weekday(p.day)} ${p.minutes}`).join(", ")}`}>
        {limitPct != null && <div className="pointer-events-none absolute inset-x-0 border-t border-dashed border-muted-foreground/60" style={{ bottom: `calc(${limitPct}% * 0.82 + 1.25rem)` }} aria-hidden="true" />}
        {i.trend.map((p, idx) => {
          const today = idx === i.trend.length - 1;
          const over = i.dailyLimitMinutes != null && p.minutes > i.dailyLimitMinutes;
          return (
            <div key={p.day} className="flex h-full flex-1 flex-col items-center justify-end gap-1">
              <span className="text-[10px] text-muted-foreground">{p.minutes || ""}</span>
              <div
                className={cn("w-full max-w-8 rounded-t-md", over ? "bg-destructive" : today ? "bg-primary" : "bg-primary/45")}
                style={{ height: `${Math.max(p.minutes ? 4 : 1, (p.minutes / max) * 82)}%` }}
              />
              <span className={cn("text-[11px]", today ? "font-bold" : "text-muted-foreground")}>{today ? "Today" : weekday(p.day)}</span>
            </div>
          );
        })}
      </div>
      {limitPct != null && <p className="m-0 text-xs text-muted-foreground">Dashed line: daily limit ({formatMinutes(i.dailyLimitMinutes)})</p>}
    </Panel>
  );
}

function TopApps({ insight: i }: { insight: ChildInsights }) {
  return (
    <Panel title="Top apps this week" icon={<Smartphone className="h-4 w-4" />}>
      {i.topApps.length === 0 ? (
        <Muted>No app usage recorded yet.</Muted>
      ) : (
        <ol className="m-0 flex list-none flex-col gap-2.5 p-0">
          {i.topApps.map((a) => (
            <li key={a.appId} className="flex flex-col gap-1">
              <span className="flex justify-between gap-2 text-sm">
                <span className="truncate font-semibold">{a.label}</span>
                <span className="shrink-0 text-muted-foreground">
                  {formatMinutes(a.minutes)} · {a.percent}%
                </span>
              </span>
              <span className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                <span className="block h-full rounded-full bg-primary" style={{ width: `${Math.max(2, a.percent)}%` }} />
              </span>
            </li>
          ))}
        </ol>
      )}
    </Panel>
  );
}

function QuizCard({ insight: i }: { insight: ChildInsights }) {
  const q = i.quiz;
  return (
    <Panel title="Quiz progress" icon={<Brain className="h-4 w-4" />}>
      <div className="grid grid-cols-3 gap-2 text-center">
        <Metric label="Attempts (7d)" value={String(q.attemptsWeek)} />
        <Metric label="Pass rate" value={q.passRatePercent == null ? "—" : `${q.passRatePercent}%`} />
        <Metric label="Streak" value={`${q.passStreakDays}d`} icon={<Flame className="h-3.5 w-3.5 text-amber-500" />} />
      </div>
      <p className="m-0 text-xs text-muted-foreground">
        Today: {q.passesToday} of {q.attemptsToday} passed · {formatMinutes(q.minutesEarnedWeek)} earned this week
      </p>
      {q.recent.length > 0 ? (
        <ul className="m-0 flex list-none flex-col gap-1 p-0 text-sm" aria-label="Recent quizzes">
          {q.recent.slice(0, 4).map((r, idx) => (
            <li key={`${r.atMs}-${idx}`} className="flex items-center justify-between gap-2">
              <span className="truncate">
                <span className={r.passed ? "text-success" : "text-destructive"} aria-hidden="true">{r.passed ? "✓" : "✗"}</span>
                <span className="sr-only">{r.passed ? "Passed" : "Not passed"}</span> {r.topic || "Quiz"} {r.total > 0 && <span className="text-muted-foreground">{r.score}/{r.total}</span>}
              </span>
              <span className="shrink-0 text-xs text-muted-foreground">{relTime(r.atMs)}</span>
            </li>
          ))}
        </ul>
      ) : (
        <Muted>No quizzes taken yet.</Muted>
      )}
    </Panel>
  );
}

function SkillsCard({ insight: i }: { insight: ChildInsights }) {
  const { strengths, needsPractice } = i.skills;
  return (
    <Panel title="Learning" icon={<Sparkles className="h-4 w-4" />}>
      {strengths.length === 0 && needsPractice.length === 0 ? (
        <Muted>Skill insights appear after a few quizzes.</Muted>
      ) : (
        <>
          <div>
            <p className="m-0 mb-1 text-xs font-semibold text-muted-foreground">Strengths</p>
            <div className="flex flex-wrap gap-1.5">{strengths.length ? strengths.map((s) => <Badge key={s.topic} variant="success">{s.label} · L{s.level}</Badge>) : <Muted>Still exploring</Muted>}</div>
          </div>
          <div>
            <p className="m-0 mb-1 text-xs font-semibold text-muted-foreground">Needs practice</p>
            {needsPractice.length ? (
              <ul className="m-0 flex list-none flex-col gap-1 p-0 text-sm">
                {needsPractice.map((s) => (
                  <li key={s.topic}>
                    <span className="font-semibold">{s.label}</span>
                    {s.concepts.length > 0 && <span className="text-muted-foreground"> — {s.concepts.join(", ")}</span>}
                  </li>
                ))}
              </ul>
            ) : (
              <Muted>Nothing flagged — nice!</Muted>
            )}
          </div>
        </>
      )}
    </Panel>
  );
}

function RewardsCard({ insight: i }: { insight: ChildInsights }) {
  const r = i.rewards;
  return (
    <Panel title="Rewards" icon={<Trophy className="h-4 w-4" />}>
      <div className="grid grid-cols-3 gap-2 text-center">
        <Metric label="Explorer level" value={String(r.level)} />
        <Metric label="XP" value={String(r.xp)} />
        <Metric label="Stickers" value={String(r.stickerCount)} />
      </div>
      {r.recentStickers.length ? (
        <ul className="m-0 flex list-none flex-wrap gap-2 p-0" aria-label="Recent stickers">
          {r.recentStickers.map((s, idx) => (
            <li key={`${s.title}-${idx}`} title={s.title} className="flex items-center gap-1 rounded-full bg-muted px-2.5 py-1 text-xs">
              <span aria-hidden="true">{s.emoji}</span> {s.title}
            </li>
          ))}
        </ul>
      ) : (
        <Muted>Stickers unlock as {i.displayName} passes quizzes.</Muted>
      )}
    </Panel>
  );
}

function DevicesCard({ insight: i }: { insight: ChildInsights }) {
  return (
    <Panel title="Devices" icon={<Laptop className="h-4 w-4" />}>
      {i.devices.length === 0 ? (
        <Muted>No devices reporting yet.</Muted>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {i.devices.map((d) => (
            <li key={d.deviceId} className="flex items-center gap-3 rounded-md bg-muted px-3 py-2 text-sm">
              {d.platform === "android" || d.platform === "ios" ? <Smartphone className="h-4 w-4 shrink-0" aria-hidden="true" /> : <Laptop className="h-4 w-4 shrink-0" aria-hidden="true" />}
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold">{d.model ?? platformLabel(d.platform)}</span>
                <span className="block text-xs text-muted-foreground">{d.online ? "Online now" : d.lastSeenAtMs ? `Last seen ${relTime(d.lastSeenAtMs)}` : "Never checked in"}</span>
              </span>
              {d.batteryPercent != null && (
                <span className={cn("flex items-center gap-1 text-xs", d.batteryPercent <= 15 && "font-semibold text-destructive")}>
                  <Battery className="h-3.5 w-3.5" aria-hidden="true" /> {d.batteryPercent}%
                </span>
              )}
              <span className={cn("h-2.5 w-2.5 shrink-0 rounded-full", d.online ? "bg-success" : "bg-muted-foreground/50")} aria-hidden="true" />
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function Metric({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) {
  return (
    <div className="rounded-md bg-muted px-2 py-2">
      <p className="m-0 flex items-center justify-center gap-1 text-lg font-bold">
        {icon && <span aria-hidden="true">{icon}</span>}
        {value}
      </p>
      <p className="m-0 text-[11px] text-muted-foreground">{label}</p>
    </div>
  );
}

function Muted({ children }: { children: React.ReactNode }) {
  return <p className="m-0 text-sm text-muted-foreground">{children}</p>;
}

function Skel({ className }: { className?: string }) {
  return <span className={cn("block animate-pulse rounded-md bg-muted", className)} aria-hidden="true" />;
}

function AnalyticsSkeleton() {
  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
      {Array.from({ length: 6 }, (_, k) => (
        <Card key={k} className="flex flex-col gap-3 p-4">
          <Skel className="h-4 w-32" />
          <Skel className="h-24" />
          <Skel className="h-3 w-3/4" />
        </Card>
      ))}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 px-4 py-6" role="status" aria-live="polite">
      <span className="sr-only">Loading your family…</span>
      <Skel className="h-8 w-40" />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, k) => (
          <Skel key={k} className="h-20" />
        ))}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Skel className="h-52" />
        <Skel className="h-52" />
      </div>
      <AnalyticsSkeleton />
    </div>
  );
}

/* ───────────────────────── helpers ───────────────────────── */

function weekday(day: string): string {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(y, (m || 1) - 1, d || 1).toLocaleDateString([], { weekday: "short" });
}

function platformLabel(p: string): string {
  return ({ android: "Android phone", ios: "iPhone / iPad", macos: "Mac", windows: "Windows PC", linux: "Linux PC" } as Record<string, string>)[p] ?? p;
}

export function relTime(ms: number, now = Date.now()): string {
  if (!ms) return "";
  const diff = Math.max(0, now - ms);
  const min = Math.round(diff / 60_000);
  if (min < 1) return "just now";
  if (min < 60) return `${min} min ago`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d === 1 ? "yesterday" : `${d} days ago`;
}
