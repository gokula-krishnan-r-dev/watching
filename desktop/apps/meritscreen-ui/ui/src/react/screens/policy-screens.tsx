import * as React from "react";
import { Save } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildPolicy } from "../lib/types";
import { Button } from "../ui/button";
import { Alert } from "../ui/alert";
import { Slider, Switch } from "../ui/form-controls";
import { ErrorState, LoadingState } from "../components/states";
import { Page, Section } from "../components/page";
import { useChildDetail, usePolicyDraft } from "../lib/use-child-detail";
import { formatMinutes } from "../lib/catalog";

type Kind = "time-limits" | "quiz-settings" | "rewards";

const TITLES: Record<Kind, { title: string; desc: string }> = {
  "time-limits": { title: "Time limits", desc: "Daily allowance and how often a quiz appears." },
  "quiz-settings": { title: "Quiz settings", desc: "How hard quizzes are and what happens after a fail." },
  rewards: { title: "Rewards", desc: "Extra time earned by passing quizzes." },
};

export function PolicyScreen({ bridge, childId, kind }: { bridge: AppBridge; childId: string; kind: Kind }) {
  const { detail, loading, error, reload } = useChildDetail(bridge, childId);
  const { draft, update, dirty, saving, saveError, save, reset } = usePolicyDraft(bridge, childId, detail?.policy);
  const back = () => void bridge.navigate({ name: "child-detail", childId });

  // Warn before leaving with unsaved changes (window close / reload).
  React.useEffect(() => {
    if (!dirty) return;
    const h = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", h);
    return () => window.removeEventListener("beforeunload", h);
  }, [dirty]);

  if (loading && !detail) return <LoadingState />;
  if (error || !detail || !draft) return <ErrorState error={error} onRetry={() => void reload()} onBack={back} />;

  const t = TITLES[kind];
  return (
    <Page
      title={t.title}
      description={`${detail.card.displayName} · ${t.desc}`}
      onBack={() => {
        if (!dirty || window.confirm("Discard unsaved changes?")) back();
      }}
      backLabel={detail.card.displayName}
      width="md"
    >
      {saveError && (
        <Alert variant="destructive" title="Couldn’t save">
          {saveError}
        </Alert>
      )}
      {kind === "time-limits" && <TimeLimits draft={draft} update={update} />}
      {kind === "quiz-settings" && <QuizSettings draft={draft} update={update} />}
      {kind === "rewards" && <Rewards draft={draft} update={update} />}
      <div className="sticky bottom-0 flex justify-end gap-2 border-t border-border bg-background/90 py-3 backdrop-blur">
        <Button variant="ghost" disabled={!dirty || saving} onClick={reset}>
          Discard
        </Button>
        <Button disabled={!dirty} loading={saving} loadingText="Saving…" onClick={() => void save()}>
          <Save className="h-4 w-4" aria-hidden="true" /> Save changes
        </Button>
      </div>
    </Page>
  );
}

type Upd = <K extends keyof ChildPolicy>(k: K, v: ChildPolicy[K]) => void;

function TimeLimits({ draft, update }: { draft: ChildPolicy; update: Upd }) {
  const limitOn = draft.dailyCeilingMinutes != null;
  return (
    <>
      <Section title="Daily allowance">
        <Switch checked={limitOn} onCheckedChange={(v) => update("dailyCeilingMinutes", v ? 120 : null)} label="Limit daily screen time" description="When the limit is reached, only emergency apps stay open." />
        {limitOn && (
          <Slider label="Daily limit" value={draft.dailyCeilingMinutes ?? 120} min={15} max={480} step={15} onChange={(v) => update("dailyCeilingMinutes", v)} format={formatMinutes} />
        )}
        <Switch checked={draft.weekendBonusEnabled} onCheckedChange={(v) => update("weekendBonusEnabled", v)} label="Weekend bonus" description="A little extra time on Saturdays and Sundays." />
      </Section>
      <Section title="Quiz pacing">
        <Slider label="Quiz every" value={draft.quizIntervalMinutes} min={5} max={120} step={5} onChange={(v) => update("quizIntervalMinutes", v)} format={(v) => `${v} min`} />
        <Slider label="Time unlocked per pass" value={draft.defaultBlockMinutes} min={5} max={240} step={5} onChange={(v) => update("defaultBlockMinutes", v)} format={formatMinutes} />
      </Section>
      <Section title="Pause">
        <Switch checked={draft.paused} onCheckedChange={(v) => update("paused", v)} label="Pause all screen time now" description="Locks every non-emergency app until you turn this off." />
      </Section>
    </>
  );
}

function QuizSettings({ draft, update }: { draft: ChildPolicy; update: Upd }) {
  return (
    <>
      <Section title="Quiz difficulty">
        <Slider label="Questions per quiz" value={Math.min(5, Math.max(3, draft.questionsPerQuiz))} min={3} max={5} step={1} onChange={(v) => update("questionsPerQuiz", v)} />
        <Slider label="Score needed to pass" value={Math.min(100, Math.max(50, draft.passScorePercent))} min={50} max={100} step={5} onChange={(v) => update("passScorePercent", v)} format={(v) => `${v}%`} />
      </Section>
      <Section title="After a failed quiz">
        <Slider label="Cooldown before retry" value={Math.min(180, Math.max(1, draft.defaultCooldownMinutes))} min={1} max={60} step={1} onChange={(v) => update("defaultCooldownMinutes", v)} format={(v) => `${v} min`} />
        <Switch checked={draft.allowRetryDuringCooldown} onCheckedChange={(v) => update("allowRetryDuringCooldown", v)} label="Allow retry during cooldown" description="Lets your child try again before the cooldown ends." />
        <p className="m-0 text-xs text-muted-foreground">During a fail lock, every non-emergency app is blocked.</p>
      </Section>
    </>
  );
}

function Rewards({ draft, update }: { draft: ChildPolicy; update: Upd }) {
  return (
    <Section title="Quiz rewards">
      <Switch checked={draft.rewardsEnabled} onCheckedChange={(v) => update("rewardsEnabled", v)} label="Reward passed quizzes" description="Give bonus minutes when a quiz is passed." />
      {draft.rewardsEnabled && (
        <Slider label="Bonus minutes per pass" value={Math.min(60, draft.extraMinutesOnPass)} min={0} max={60} step={5} onChange={(v) => update("extraMinutesOnPass", v)} format={(v) => `+${v} min`} />
      )}
    </Section>
  );
}
