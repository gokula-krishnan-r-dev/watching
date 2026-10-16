import * as React from "react";
import { BarChart3 } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildCard, ChildReportsSnapshot, DashboardSnapshot, UiStateDto } from "../lib/types";
import { Button } from "../ui/button";
import { Card } from "../ui/card";
import { Alert } from "../ui/alert";
import { EmptyState, ErrorState, LoadingState } from "../components/states";
import { Page, Section } from "../components/page";
import { formatMinutes } from "../lib/catalog";
import { friendlyError } from "../lib/errors";
import { cn } from "../lib/utils";

function dayLabel(d: string): string {
  const t = new Date(d + (d.length === 10 ? "T00:00:00" : ""));
  return Number.isNaN(t.getTime()) ? d : t.toLocaleDateString(undefined, { weekday: "short" });
}

export function ReportsScreen({ bridge, childId }: { bridge: AppBridge; childId?: string }) {
  const [children, setChildren] = React.useState<ChildCard[] | null>(null);
  const [active, setActive] = React.useState<string | undefined>(childId);
  const [report, setReport] = React.useState<ChildReportsSnapshot | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);

  const load = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const dash = await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_dashboard");
      const kids = dash.data?.children ?? [];
      setChildren(kids);
      const target = active ?? kids[0]?.childId;
      setActive(target);
      setReport(target ? await bridge.invoke<ChildReportsSnapshot>("parent_child_reports", { childId: target }) : null);
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [bridge, active]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const back = () => void bridge.navigate(childId ? { name: "child-detail", childId } : { name: "parent-home" });
  if (loading && !report) return <LoadingState label="Loading reports…" />;
  if (error) return <ErrorState error={error} onRetry={() => void load()} onBack={back} />;

  if (!children || children.length === 0 || !report) {
    return (
      <Page title="Reports" onBack={back}>
        <EmptyState
          icon={<BarChart3 className="h-10 w-10 text-muted-foreground" aria-hidden="true" />}
          title="No reports yet"
          body="Add a child and pair their device to see usage and quiz results."
          action={<Button onClick={() => void bridge.navigate({ name: "parent-add-child" })}>Add child</Button>}
        />
      </Page>
    );
  }

  const max = Math.max(1, ...report.dailyTrend.map((d) => d.totalMinutes));
  const hasData = report.weeklyTotalMinutes > 0 || report.quizStats.totalAttempts > 0;

  return (
    <Page title="Reports" description={`${report.childName} · last 7 days`} onBack={back} backLabel={childId ? report.childName : "Family"} width="xl">
      {children.length > 1 && (
        <div role="tablist" aria-label="Child" className="flex flex-wrap gap-2">
          {children.map((c) => (
            <Button key={c.childId} role="tab" aria-selected={c.childId === active} size="sm" variant={c.childId === active ? "default" : "secondary"} onClick={() => setActive(c.childId)}>
              {c.displayName}
            </Button>
          ))}
        </div>
      )}
      {report.syncStatus && report.syncStatus !== "synced" && (
        <Alert>Reports show activity recorded on this computer ({report.syncStatus}). Activity from other devices may be missing.</Alert>
      )}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi label="Today" value={formatMinutes(report.todayMinutes)} />
        <Kpi label="This week" value={formatMinutes(report.weeklyTotalMinutes)} />
        <Kpi label="Daily average" value={formatMinutes(report.dailyAverageMinutes)} />
        <Kpi label="Quiz pass rate" value={report.quizStats.totalAttempts ? `${Math.round(report.quizStats.passRatePercent)}%` : "—"} sub={`${report.quizStats.passes} of ${report.quizStats.totalAttempts} passed`} />
      </div>
      {!hasData ? (
        <EmptyState title="No activity recorded yet" body="Usage appears here after the child’s device has been used." />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
          <Section title="Daily screen time">
            <div className="flex h-44 items-end gap-2" role="img" aria-label={report.dailyTrend.map((d) => `${dayLabel(d.date)} ${formatMinutes(d.totalMinutes)}`).join(", ")}>
              {report.dailyTrend.map((d) => (
                <div key={d.date} className="flex flex-1 flex-col items-center gap-1">
                  <div className="flex h-36 w-full flex-col justify-end">
                    <div className="w-full rounded-t bg-primary/80" style={{ height: `${(d.totalMinutes / max) * 100}%`, minHeight: d.totalMinutes ? 2 : 0 }} />
                  </div>
                  <span className="text-[11px] text-muted-foreground">{dayLabel(d.date)}</span>
                </div>
              ))}
            </div>
            <p className="m-0 text-xs text-muted-foreground">Quiz time this week: {formatMinutes(report.weeklyQuizMinutes)}</p>
          </Section>
          <Section title="Top apps">
            {report.topApps.length === 0 ? (
              <p className="m-0 text-sm text-muted-foreground">No app usage yet.</p>
            ) : (
              <ul className="m-0 flex list-none flex-col gap-3 p-0">
                {report.topApps.slice(0, 6).map((a) => (
                  <li key={a.appId} className="flex flex-col gap-1">
                    <div className="flex justify-between text-sm">
                      <span className="truncate font-semibold">{a.displayName || a.appId}</span>
                      <span className="text-muted-foreground">{formatMinutes(a.minutes)}</span>
                    </div>
                    <div className="h-2 rounded-full bg-muted">
                      <div className={cn("h-2 rounded-full bg-primary")} style={{ width: `${Math.min(100, a.percentage)}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </div>
      )}
    </Page>
  );
}

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Card className="p-4">
      <p className="m-0 text-xs font-semibold text-muted-foreground">{label}</p>
      <p className="m-0 mt-1 text-xl font-bold">{value}</p>
      {sub && <p className="m-0 text-xs text-muted-foreground">{sub}</p>}
    </Card>
  );
}
