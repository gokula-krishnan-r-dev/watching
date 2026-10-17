import * as React from "react";
import { ArrowLeft, ArrowRight, BookOpen, Gamepad2, PartyPopper } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildCard, ChildDetail } from "../lib/types";
import { Button } from "../ui/button";
import { Alert } from "../ui/alert";
import { FieldError, Input, Label } from "../ui/input";
import { ChoiceCard, Slider, Stepper, Switch } from "../ui/form-controls";
import { PinInput } from "../components/code-input";
import { PairingOfferPanel } from "../components/pairing-offer";
import { Page, Section } from "../components/page";
import { AGE_BANDS, AVATARS, NAME_MAX, formatMinutes, saveAvatar, validateChildName, validateNewPin } from "../lib/catalog";
import { friendlyError } from "../lib/errors";

type Mode = "onboard" | "add-child";
type StepId = "profile" | "limits" | "apps" | "pin" | "pair" | "done";

export function ChildSetupWizard({ bridge, mode }: { bridge: AppBridge; mode: Mode }) {
  const steps: StepId[] = mode === "onboard" ? ["profile", "limits", "apps", "pin", "pair", "done"] : ["profile", "limits", "apps", "pair", "done"];
  const labels: Record<StepId, string> = { profile: "Profile", limits: "Limits", apps: "Apps", pin: "Parent PIN", pair: "Pair device", done: "Done" };
  const [step, setStep] = React.useState<StepId>("profile");
  const idx = steps.indexOf(step);

  const [name, setName] = React.useState("");
  const [nameError, setNameError] = React.useState<string | null>(null);
  const [avatar, setAvatar] = React.useState<string>("fox");
  const [age, setAge] = React.useState<string>("7_to_9");
  const [limitOn, setLimitOn] = React.useState(true);
  const [dailyMinutes, setDailyMinutes] = React.useState(90);
  const [quizEvery, setQuizEvery] = React.useState(30);
  const [pin, setPin] = React.useState("");
  const [pin2, setPin2] = React.useState("");
  const [pinError, setPinError] = React.useState<string | null>(null);
  const [creating, setCreating] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [child, setChild] = React.useState<ChildCard | null>(null);

  const go = (s: StepId) => {
    setError(null);
    setStep(s);
  };
  const next = () => go(steps[Math.min(idx + 1, steps.length - 1)]);
  const back = () => (idx === 0 ? void bridge.navigate({ name: mode === "onboard" ? "role" : "parent-home" }) : go(steps[idx - 1]));

  /** Creates the child (and family for onboarding), then applies limits. */
  const create = async () => {
    setCreating(true);
    setError(null);
    try {
      const card =
        child ??
        (mode === "onboard"
          ? await bridge.invoke<ChildCard>("parent_onboard", { childName: name.trim(), ageBand: age, pin })
          : await bridge.invoke<ChildCard>("parent_add_child", { childName: name.trim(), ageBand: age }));
      setChild(card);
      saveAvatar(card.childId, avatar);
      const detail = await bridge.invoke<ChildDetail>("parent_child_detail", { childId: card.childId });
      await bridge.invoke("parent_update_policy", {
        childId: card.childId,
        policy: {
          ...detail.policy,
          dailyCeilingMinutes: limitOn ? dailyMinutes : null,
          quizIntervalMinutes: quizEvery,
        },
      });
      if (mode === "onboard") localStorage.setItem("meritscreen_onboarding_completed", "true");
      go("pair");
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setCreating(false);
    }
  };

  const onContinue = () => {
    if (step === "profile") {
      const v = validateChildName(name);
      setNameError(v);
      if (!v) next();
    } else if (step === "limits") {
      next();
    } else if (step === "apps") {
      if (mode === "onboard") next();
      else void create();
    } else if (step === "pin") {
      const v = validateNewPin(pin, pin2);
      setPinError(v);
      if (!v) void create();
    }
  };

  const displayName = name.trim() || "your child";

  return (
    <Page
      title={mode === "onboard" ? "Set up your family" : "Add a child"}
      description={mode === "onboard" ? "A few quick steps. You can change everything later." : "Create a profile and pair their device."}
      width="md"
    >
      <Stepper steps={steps.map((s) => labels[s])} current={idx} />
      {error && (
        <Alert variant="destructive" title="Couldn’t save">
          {error}
        </Alert>
      )}

      {step === "profile" && (
        <Section title="Child profile">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="child-name">Name</Label>
            <Input
              id="child-name"
              value={name}
              maxLength={NAME_MAX + 5}
              autoFocus
              placeholder="e.g. Asha"
              aria-invalid={!!nameError}
              aria-describedby={nameError ? "child-name-error" : undefined}
              onChange={(e) => {
                setName(e.target.value);
                if (nameError) setNameError(null);
              }}
              onKeyDown={(e) => e.key === "Enter" && onContinue()}
            />
            <FieldError id="child-name-error">{nameError}</FieldError>
          </div>
          <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
            <legend className="mb-1 text-sm font-semibold">Avatar</legend>
            <div role="radiogroup" aria-label="Avatar" className="grid grid-cols-4 gap-2 sm:grid-cols-8">
              {AVATARS.map((a) => (
                <ChoiceCard key={a.id} label={a.label} selected={avatar === a.id} onSelect={() => setAvatar(a.id)} className="justify-center p-2 text-2xl">
                  <span aria-hidden="true">{a.emoji}</span>
                </ChoiceCard>
              ))}
            </div>
          </fieldset>
          <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
            <legend className="mb-1 text-sm font-semibold">Age group</legend>
            <div role="radiogroup" aria-label="Age group" className="grid gap-2 sm:grid-cols-3">
              {AGE_BANDS.map((b) => (
                <ChoiceCard key={b.id} label={b.label} selected={age === b.id} onSelect={() => setAge(b.id)} className="flex-col items-start gap-0.5">
                  <span className="text-sm font-bold text-foreground">{b.label}</span>
                  <span className={age === b.id ? "text-xs text-foreground" : "text-xs text-muted-foreground"}>{b.sub}</span>
                </ChoiceCard>
              ))}
            </div>
          </fieldset>
        </Section>
      )}

      {step === "limits" && (
        <Section title="Daily limits" description={`How much screen time ${displayName} gets, and how often a short quiz appears.`}>
          <Switch checked={limitOn} onCheckedChange={setLimitOn} label="Daily screen-time limit" description="When off, quizzes still gate time but there’s no daily cap." />
          {limitOn && <Slider label="Daily limit" value={dailyMinutes} min={15} max={240} step={15} onChange={setDailyMinutes} format={formatMinutes} />}
          <Slider label="Quiz every" value={quizEvery} min={10} max={60} step={5} onChange={setQuizEvery} format={(v) => `${v} min`} />
        </Section>
      )}

      {step === "apps" && (
        <Section title="How apps are handled" description="You can fine-tune each app once the device is paired.">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-md border border-border p-4">
              <BookOpen className="mb-2 h-5 w-5 text-success" aria-hidden="true" />
              <p className="m-0 text-sm font-bold">Emergency & school tools</p>
              <p className="m-0 text-xs text-muted-foreground">Always available, even during a lock.</p>
            </div>
            <div className="rounded-md border border-border p-4">
              <Gamepad2 className="mb-2 h-5 w-5 text-primary" aria-hidden="true" />
              <p className="m-0 text-sm font-bold">Games, browsers & video</p>
              <p className="m-0 text-xs text-muted-foreground">Time is earned by passing short quizzes.</p>
            </div>
          </div>
          <p className="m-0 text-xs text-muted-foreground">After pairing, the device’s installed apps appear under Allowed apps so you can approve or block each one.</p>
        </Section>
      )}

      {step === "pin" && (
        <Section title="Create a parent PIN" description="Needed on your child’s devices to unlock settings or exit Watching. Don’t share it.">
          <div className="flex flex-col items-center gap-2">
            <Label>New PIN</Label>
            <PinInput label="New parent PIN" value={pin} onChange={(v) => { setPin(v); setPinError(null); }} autoFocus invalid={!!pinError} />
            <Label className="mt-2">Confirm PIN</Label>
            <PinInput label="Confirm parent PIN" value={pin2} onChange={(v) => { setPin2(v); setPinError(null); }} invalid={!!pinError} onComplete={() => undefined} />
            <FieldError>{pinError}</FieldError>
          </div>
        </Section>
      )}

      {step === "pair" && child && (
        <Section title={`Pair ${child.displayName}’s device`}>
          <PairingOfferPanel bridge={bridge} childId={child.childId} childName={child.displayName} onPaired={() => go("done")} />
        </Section>
      )}

      {step === "done" && child && (
        <Section>
          <div className="flex flex-col items-center gap-2 py-4 text-center">
            <PartyPopper className="h-10 w-10 text-primary" aria-hidden="true" />
            <h2 className="m-0 text-xl font-bold">All set!</h2>
            <p className="m-0 text-sm text-muted-foreground">{child.displayName}’s profile is ready. Review apps and limits any time.</p>
          </div>
        </Section>
      )}

      <div className="flex flex-wrap justify-between gap-2">
        {step !== "pair" && step !== "done" ? (
          <>
            <Button variant="ghost" onClick={back} disabled={creating}>
              <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {idx === 0 ? "Cancel" : "Back"}
            </Button>
            <Button onClick={onContinue} loading={creating} loadingText="Saving…">
              {(mode === "onboard" ? step === "pin" : step === "apps") ? "Create profile" : "Continue"} <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </>
        ) : step === "pair" ? (
          <Button variant="ghost" className="ml-auto" onClick={() => go("done")}>
            Pair later
          </Button>
        ) : (
          <Button className="ml-auto" onClick={() => void bridge.navigate({ name: "parent-home" })}>
            Go to dashboard <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        )}
      </div>
    </Page>
  );
}
