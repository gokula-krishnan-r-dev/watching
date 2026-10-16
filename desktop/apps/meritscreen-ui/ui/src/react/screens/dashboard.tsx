import * as React from "react";
import { BarChart3, ChevronRight, Monitor, Pause, Play, Plus, RefreshCw, Settings, Users } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildCard, ChildDetail, DashboardSnapshot, UiStateDto } from "../lib/types";
import { Button } from "../ui/button";
import { Card } from "../ui/card";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { EmptyState, ErrorState, LoadingState } from "../components/states";
import { Page } from "../components/page";
import { ageLabel, avatarFor, formatMinutes, MAX_CHILDREN } from "../lib/catalog";
import { friendlyError } from "../lib/errors";

export function DashboardScreen({ bridge }: { bridge: AppBridge }) {
  const [snap, setSnap] = React.useState<DashboardSnapshot | null>(null);
  const [paused, setPaused] = React.useState<Record<string, boolean>>({});
  const [state, setState] = React.useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = React.useState<string | null>(null);
  const [refreshing, setRefreshing] = React.useState(false);
  const [busy, setBusy] = React.useState<string | null>(null);

  const apply = async (dto: UiStateDto<DashboardSnapshot>) => {
    if (dto.state === "error") throw dto.error ?? "Couldn’t load your family.";
    const data = dto.data;
    setSnap(data);
    const p: Record<string, boolean> = {};
    await Promise.all(
      (data?.children ?? []).map(async (c) => {
        try {
          const d = await bridge.invoke<ChildDetail>("parent_child_detail", { childId: c.childId });
          p[c.childId] = d.policy.paused;
        } catch {
          /* keep unknown */
        }
      }),
    );
    setPaused(p);
    setState("ready");
  };

  const load = React.useCallback(async () => {
    setError(null);
    try {
      await apply(await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_dashboard"));
    } catch (e) {
      setError(friendlyError(e));
      setState("error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bridge]);

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

  const togglePause = async (c: ChildCard) => {
    setBusy(c.childId);
    try {
      const d = await bridge.invoke<ChildDetail>("parent_child_detail", { childId: c.childId });
      const next = !d.policy.paused;
      await bridge.invoke("parent_update_policy", { childId: c.childId, policy: { ...d.policy, paused: next } });
      setPaused((p) => ({ ...p, [c.childId]: next }));
      bridge.toast(next ? `${c.displayName}’s screen time is paused` : `${c.displayName} can use the computer again`, "info");
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setBusy(null);
    }
  };

  if (state === "loading") return <LoadingState label="Loading your family…" />;
  if (state === "error") return <ErrorState error={error} onRetry={() => void load()} />;

  const children = snap?.children ?? [];
  const max = snap?.maxChildren || MAX_CHILDREN;
  const atMax = children.length >= max;
  const usedToday = children.reduce((a, c) => a + c.minutesUsedToday, 0);
  const pairedCount = children.filter((c) => c.pairedDeviceCount > 0).length;

  return (
    <Page
      title="Family"
      description={snap?.profile?.email ? `Signed in as ${snap.profile.email}` : undefined}
      width="xl"
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
          <div className="grid gap-3 sm:grid-cols-3">
            <Stat label="Children" value={`${children.length} / ${max}`} />
            <Stat label="Devices paired" value={`${pairedCount} of ${children.length}`} />
            <Stat label="Screen time today" value={formatMinutes(usedToday)} />
          </div>
          {atMax && <Alert>You’ve reached the limit of {max} children.</Alert>}
          <ul className="m-0 grid list-none gap-3 p-0 md:grid-cols-2" aria-label="Children">
            {children.map((c) => {
              const isPaused = paused[c.childId] === true;
              return (
                <li key={c.childId}>
                  <Card className="flex flex-col gap-3 p-4">
                    <div className="flex items-center gap-3">
                      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary text-2xl" aria-hidden="true">
                        {avatarFor(c.childId, c.displayName)}
                      </span>
                      <div className="min-w-0 flex-1">
                        <h2 className="m-0 truncate text-base font-bold">{c.displayName}</h2>
                        <p className="m-0 text-xs text-muted-foreground">{ageLabel(c.ageBand)}</p>
                      </div>
                      {isPaused ? <Badge variant="destructive">Paused</Badge> : c.pairedDeviceCount > 0 ? <Badge variant="success">Protected</Badge> : <Badge variant="outline">Not paired</Badge>}
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="rounded-md bg-muted p-2">
                        <p className="m-0 text-xs text-muted-foreground">Used today</p>
                        <p className="m-0 font-bold">{formatMinutes(c.minutesUsedToday)}</p>
                      </div>
                      <div className="rounded-md bg-muted p-2">
                        <p className="m-0 text-xs text-muted-foreground">Remaining</p>
                        <p className="m-0 font-bold">{c.minutesRemainingToday == null ? "No limit" : formatMinutes(c.minutesRemainingToday)}</p>
                      </div>
                    </div>
                    {c.pairedDeviceCount > 0 && c.platformHint && (
                      <p className="m-0 flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Monitor className="h-3.5 w-3.5" aria-hidden="true" /> {c.platformHint}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-2">
                      <Button size="sm" onClick={() => void bridge.navigate({ name: "child-detail", childId: c.childId })}>
                        Manage <ChevronRight className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      {c.pairedDeviceCount === 0 ? (
                        <Button size="sm" variant="secondary" onClick={() => void bridge.navigate({ name: "pairing", childId: c.childId })}>
                          Pair device
                        </Button>
                      ) : (
                        <Button size="sm" variant="secondary" loading={busy === c.childId} onClick={() => void togglePause(c)} aria-label={isPaused ? `Resume ${c.displayName}` : `Pause ${c.displayName}`}>
                          {isPaused ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
                          {isPaused ? "Resume" : "Pause"}
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => void bridge.navigate({ name: "reports", childId: c.childId })}>
                        <BarChart3 className="h-4 w-4" aria-hidden="true" /> Reports
                      </Button>
                    </div>
                  </Card>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Page>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card className="p-4">
      <p className="m-0 text-xs font-semibold text-muted-foreground">{label}</p>
      <p className="m-0 mt-1 text-xl font-bold">{value}</p>
    </Card>
  );
}
