import * as React from "react";
import { Bell, Copy, LogOut, Plus, Trash2 } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { DashboardSnapshot, NotificationPrefs, UiStateDto } from "../lib/types";
import { Button } from "../ui/button";
import { Alert } from "../ui/alert";
import { Input, Label, FieldError } from "../ui/input";
import { Switch } from "../ui/form-controls";
import { PinInput } from "../components/code-input";
import { ErrorState, LoadingState } from "../components/states";
import { Page, Section } from "../components/page";
import { validateNewPin } from "../lib/catalog";
import { friendlyError } from "../lib/errors";

export function AccountScreen({ bridge }: { bridge: AppBridge }) {
  const [snap, setSnap] = React.useState<DashboardSnapshot | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [pin, setPin] = React.useState("");
  const [pin2, setPin2] = React.useState("");
  const [pinError, setPinError] = React.useState<string | null>(null);
  const [pinOk, setPinOk] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [signingOut, setSigningOut] = React.useState(false);

  const load = React.useCallback(async () => {
    setError(null);
    try {
      const d = await bridge.invoke<UiStateDto<DashboardSnapshot>>("parent_dashboard");
      if (d.state === "error") throw d.error;
      setSnap(d.data);
    } catch (e) {
      setError(friendlyError(e));
    }
  }, [bridge]);
  React.useEffect(() => void load(), [load]);

  if (error) return <ErrorState error={error} onRetry={() => void load()} onBack={() => void bridge.navigate({ name: "parent-home" })} />;
  if (!snap) return <LoadingState />;

  const savePin = async () => {
    setPinOk(false);
    const v = validateNewPin(pin, pin2);
    if (v) return setPinError(v);
    setPinError(null);
    setSaving(true);
    try {
      await bridge.invoke("parent_reset_pin", { pin });
      setPin("");
      setPin2("");
      setPinOk(true);
      bridge.toast("Parent PIN updated", "success");
    } catch (e) {
      setPinError(friendlyError(e));
    } finally {
      setSaving(false);
    }
  };

  const signOut = async () => {
    setSigningOut(true);
    try {
      localStorage.removeItem("meritscreen_onboarding_completed");
      await bridge.invoke("parent_sign_out");
    } catch {
      /* local sign-out still proceeds */
    }
    await bridge.navigate({ name: "role" });
  };

  const familyId = snap.profile.familyId ?? "";
  return (
    <Page title="Account" description="Sign-in, parent PIN and family settings." onBack={() => void bridge.navigate({ name: "parent-home" })} backLabel="Family" width="md">
      <Section title="Profile">
        <p className="m-0 text-sm">
          Signed in as <strong>{snap.profile.email || "Parent"}</strong>
        </p>
        {familyId && (
          <div className="flex items-center gap-2">
            <Input aria-label="Family ID" readOnly value={familyId} className="font-mono text-xs" />
            <Button
              variant="secondary"
              size="sm"
              aria-label="Copy family ID"
              onClick={() => void navigator.clipboard?.writeText(familyId).then(() => bridge.toast("Copied", "info"), () => bridge.toast("Couldn’t copy", "error"))}
            >
              <Copy className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void bridge.navigate({ name: "notifications" })}>
            <Bell className="h-4 w-4" aria-hidden="true" /> Notifications
          </Button>
          <Button variant="secondary" disabled={snap.children.length >= (snap.maxChildren || 5)} onClick={() => void bridge.navigate({ name: "parent-add-child" })}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Add child
          </Button>
        </div>
      </Section>

      <Section title="Parent PIN" description="Used to unlock settings on your child’s device. Changing it updates every paired device.">
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void savePin();
          }}
        >
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold" aria-hidden="true">New PIN</span>
            <PinInput value={pin} onChange={setPin} label="New PIN" invalid={!!pinError} />
          </div>
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold" aria-hidden="true">Confirm PIN</span>
            <PinInput value={pin2} onChange={setPin2} label="Confirm PIN" invalid={!!pinError} />
          </div>
          <FieldError>{pinError}</FieldError>
          {pinOk && <Alert variant="success">Parent PIN updated.</Alert>}
          <Button type="submit" className="self-start" loading={saving} loadingText="Saving…" disabled={pin.length < 4 || pin2.length < 4}>
            Save PIN
          </Button>
        </form>
      </Section>

      <Section title="Session">
        <Button variant="secondary" className="self-start" loading={signingOut} onClick={() => void signOut()}>
          <LogOut className="h-4 w-4" aria-hidden="true" /> Sign out
        </Button>
      </Section>

      <Section title="Danger zone">
        <Button variant="destructive" className="self-start" onClick={() => void bridge.navigate({ name: "delete-family" })}>
          <Trash2 className="h-4 w-4" aria-hidden="true" /> Delete family
        </Button>
      </Section>
    </Page>
  );
}

export function NotificationsScreen({ bridge }: { bridge: AppBridge }) {
  const [prefs, setPrefs] = React.useState<NotificationPrefs | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [pending, setPending] = React.useState<keyof NotificationPrefs | null>(null);

  const load = React.useCallback(async () => {
    setError(null);
    try {
      setPrefs(await bridge.invoke<NotificationPrefs>("parent_notification_prefs"));
    } catch (e) {
      setError(friendlyError(e));
    }
  }, [bridge]);
  React.useEffect(() => void load(), [load]);

  const back = () => void bridge.navigate({ name: "account" });
  if (error && !prefs) return <ErrorState error={error} onRetry={() => void load()} onBack={back} />;
  if (!prefs) return <LoadingState />;

  const toggle = async (k: keyof NotificationPrefs, v: boolean) => {
    const prev = prefs;
    const next = { ...prefs, [k]: v };
    setPrefs(next);
    setPending(k);
    try {
      await bridge.invoke("parent_set_notification_prefs", { prefs: next });
    } catch (e) {
      setPrefs(prev);
      bridge.toast(friendlyError(e), "error");
    } finally {
      setPending(null);
    }
  };

  const rows: { k: keyof NotificationPrefs; label: string; desc: string }[] = [
    { k: "quizResults", label: "Quiz results", desc: "When a quiz is passed or failed." },
    { k: "failLockAlerts", label: "Fail lock alerts", desc: "When the device locks after a failed quiz." },
    { k: "tamperAlerts", label: "Tamper alerts", desc: "When protection is disabled or bypassed." },
  ];
  return (
    <Page title="Notifications" description="Choose which alerts you receive. Changes save automatically." onBack={back} backLabel="Account" width="md">
      <Section title="Alerts">
        {rows.map((r) => (
          <Switch key={r.k} checked={prefs[r.k]} disabled={pending === r.k} onCheckedChange={(v) => void toggle(r.k, v)} label={r.label} description={r.desc} />
        ))}
      </Section>
    </Page>
  );
}

export function DeleteFamilyScreen({ bridge }: { bridge: AppBridge }) {
  const [text, setText] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const ok = text.trim() === "DELETE";
  const del = async () => {
    setBusy(true);
    setError(null);
    try {
      await bridge.invoke("parent_delete_family");
      localStorage.removeItem("meritscreen_onboarding_completed");
      bridge.toast("Your family was deleted", "info");
      await bridge.navigate({ name: "parent-onboard" });
    } catch (e) {
      setError(friendlyError(e));
      setBusy(false);
    }
  };
  return (
    <Page title="Delete family" onBack={() => void bridge.navigate({ name: "account" })} backLabel="Account" width="md">
      <Section title="This can’t be undone">
        <p className="m-0 text-sm">
          All children, paired devices, app rules and settings are deleted from every device and from the cloud. Paired devices stop being protected.
        </p>
        {error && <Alert variant="destructive" title="Couldn’t delete">{error}</Alert>}
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (ok) void del();
          }}
        >
          <Label htmlFor="delete-confirm">Type DELETE to confirm</Label>
          <Input id="delete-confirm" value={text} onChange={(e) => setText(e.target.value)} autoComplete="off" disabled={busy} />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" variant="destructive" disabled={!ok} loading={busy} loadingText="Deleting…">
              Delete family permanently
            </Button>
            <Button type="button" variant="ghost" onClick={() => void bridge.navigate({ name: "account" })}>
              Cancel
            </Button>
          </div>
        </form>
      </Section>
    </Page>
  );
}
