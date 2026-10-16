import * as React from "react";
import { AppWindow, BarChart3, Brain, Clock, Gift, Link2, ShieldAlert, Trash2, Unlink } from "lucide-react";
import type { AppBridge } from "../bridge";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { Input, Label } from "../ui/input";
import { Dialog } from "../ui/dialog";
import { Switch } from "../ui/form-controls";
import { ErrorState, LoadingState } from "../components/states";
import { Page, Section } from "../components/page";
import { useChildDetail } from "../lib/use-child-detail";
import { ageLabel, avatarFor, formatMinutes } from "../lib/catalog";
import { friendlyError } from "../lib/errors";

export function ChildDetailScreen({ bridge, childId }: { bridge: AppBridge; childId: string }) {
  const { detail, setDetail, loading, error, reload } = useChildDetail(bridge, childId);
  const [dialog, setDialog] = React.useState<"unpair" | "remove" | null>(null);
  const [confirmName, setConfirmName] = React.useState("");
  const [pending, setPending] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  if (loading && !detail) return <LoadingState label="Loading…" />;
  if (error || !detail) return <ErrorState error={error} onRetry={() => void reload()} onBack={() => void bridge.navigate({ name: "parent-home" })} />;

  const { card, policy, enforcement } = detail;
  const paired = card.pairedDeviceCount > 0;

  const setPaused = async (v: boolean) => {
    setPending("pause");
    try {
      const next = { ...policy, paused: v };
      await bridge.invoke("parent_update_policy", { childId, policy: next });
      setDetail({ ...detail, policy: next });
    } catch (e) {
      bridge.toast(friendlyError(e), "error");
    } finally {
      setPending(null);
    }
  };

  const unpair = async () => {
    setPending("unpair");
    setActionError(null);
    try {
      const d = await bridge.invoke<typeof detail>("parent_unpair_device", { childId });
      setDetail(d ?? { ...detail, card: { ...card, pairedDeviceCount: 0 } });
      setDialog(null);
      bridge.toast("Device unpaired", "info");
    } catch (e) {
      setActionError(friendlyError(e));
    } finally {
      setPending(null);
    }
  };

  const remove = async () => {
    setPending("remove");
    setActionError(null);
    try {
      await bridge.invoke("parent_remove_child", { childId });
      bridge.toast(`${card.displayName} was removed`, "info");
      await bridge.navigate({ name: "parent-home" });
    } catch (e) {
      setActionError(friendlyError(e));
      setPending(null);
    }
  };

  const tiles = [
    { icon: AppWindow, title: "Allowed apps", body: `${detail.appRules.filter((r) => r.allowed).length} of ${detail.appRules.length} allowed`, route: "allowlist" },
    { icon: Clock, title: "Time limits", body: `Daily ${formatMinutes(policy.dailyCeilingMinutes)} · quiz every ${policy.quizIntervalMinutes} min`, route: "time-limits" },
    { icon: Brain, title: "Quiz settings", body: `${policy.questionsPerQuiz} questions · pass ${policy.passScorePercent}%`, route: "quiz-settings" },
    { icon: Gift, title: "Rewards", body: policy.rewardsEnabled ? `+${policy.extraMinutesOnPass} min on pass` : "Off", route: "rewards" },
    { icon: BarChart3, title: "Reports", body: "Usage and quiz results", route: "reports" },
  ];

  return (
    <Page
      title={
        <span className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-secondary text-2xl" aria-hidden="true">
            {avatarFor(childId, card.displayName)}
          </span>
          {card.displayName}
        </span>
      }
      description={ageLabel(card.ageBand)}
      onBack={() => void bridge.navigate({ name: "parent-home" })}
      backLabel="Family"
    >
      <div className="grid gap-4 md:grid-cols-[1.2fr_1fr]">
        <Section title="Device">
          {paired ? (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="success">Paired</Badge>
                {card.platformHint && <Badge variant="outline">{card.platformHint}</Badge>}
                {enforcement && <Badge variant="outline">Guardian: {enforcement.guardianState}</Badge>}
              </div>
              {enforcement?.degradedMessage && <Alert>{enforcement.degradedMessage}</Alert>}
              {enforcement && enforcement.tamperFlags.length > 0 && (
                <Alert variant="destructive" title="Tamper alerts">
                  <ShieldAlert className="mr-1 inline h-4 w-4" aria-hidden="true" />
                  {enforcement.tamperFlags.join(", ")}
                </Alert>
              )}
              <Button variant="secondary" className="self-start" onClick={() => { setActionError(null); setDialog("unpair"); }}>
                <Unlink className="h-4 w-4" aria-hidden="true" /> Unpair device
              </Button>
            </>
          ) : (
            <>
              <p className="m-0 text-sm text-muted-foreground">No device is paired yet.</p>
              <Button className="self-start" onClick={() => void bridge.navigate({ name: "pairing", childId })}>
                <Link2 className="h-4 w-4" aria-hidden="true" /> Pair a device
              </Button>
            </>
          )}
        </Section>
        <Section title="Today">
          <p className="m-0 text-sm">
            Used <strong>{formatMinutes(card.minutesUsedToday)}</strong>
            {card.minutesRemainingToday != null && (
              <>
                {" "}· <strong>{formatMinutes(card.minutesRemainingToday)}</strong> left
              </>
            )}
          </p>
          <Switch checked={policy.paused} onCheckedChange={(v) => void setPaused(v)} disabled={pending === "pause"} label="Pause screen time" description="Locks all non-emergency apps until you resume." />
        </Section>
      </div>

      <nav aria-label="Settings" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {tiles.map((t) => (
          <button
            key={t.route}
            type="button"
            onClick={() => void bridge.navigate({ name: t.route, childId })}
            className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-card p-4 text-left hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <t.icon className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
            <span>
              <span className="block text-sm font-bold text-foreground">{t.title}</span>
              <span className="block text-xs text-muted-foreground">{t.body}</span>
            </span>
          </button>
        ))}
      </nav>

      <Section title="Danger zone">
        <Button variant="destructive" className="self-start" onClick={() => { setConfirmName(""); setActionError(null); setDialog("remove"); }}>
          <Trash2 className="h-4 w-4" aria-hidden="true" /> Remove {card.displayName}
        </Button>
      </Section>

      <Dialog
        open={dialog === "unpair"}
        onClose={() => setDialog(null)}
        title={`Unpair ${card.displayName}’s device?`}
        description="Protection stops on that device. You can pair it again with a new code."
        footer={
          <>
            <Button variant="ghost" onClick={() => setDialog(null)}>Cancel</Button>
            <Button variant="destructive" loading={pending === "unpair"} onClick={() => void unpair()}>Unpair</Button>
          </>
        }
      >
        {actionError && <Alert variant="destructive">{actionError}</Alert>}
      </Dialog>

      <Dialog
        open={dialog === "remove"}
        onClose={() => setDialog(null)}
        title={`Remove ${card.displayName}?`}
        description="This deletes their profile, rules and history on all devices. This can’t be undone."
        footer={
          <>
            <Button variant="ghost" onClick={() => setDialog(null)}>Cancel</Button>
            <Button variant="destructive" disabled={confirmName.trim() !== card.displayName.trim()} loading={pending === "remove"} onClick={() => void remove()}>
              Remove permanently
            </Button>
          </>
        }
      >
        {actionError && <Alert variant="destructive">{actionError}</Alert>}
        <Label htmlFor="confirm-name">Type “{card.displayName}” to confirm</Label>
        <Input id="confirm-name" value={confirmName} onChange={(e) => setConfirmName(e.target.value)} autoComplete="off" />
      </Dialog>
    </Page>
  );
}
