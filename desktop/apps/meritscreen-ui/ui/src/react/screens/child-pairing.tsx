import * as React from "react";
import { ArrowLeft, ClipboardPaste, Link2 } from "lucide-react";
import type { AppBridge } from "../bridge";
import { Button } from "../ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "../ui/card";
import { Alert } from "../ui/alert";
import { CodeInput } from "../components/code-input";
import { friendlyError } from "../lib/errors";

const STEPS = [
  { title: "Open Watching on the parent device", body: "Phone, tablet or computer." },
  { title: "Choose the child → Pair device", body: "A 6-digit code appears (valid for a few minutes)." },
  { title: "Enter the code here", body: "Type it or paste it." },
];

export function ChildPairingScreen({ bridge }: { bridge: AppBridge }) {
  const [code, setCode] = React.useState("");
  const [autostart, setAutostart] = React.useState(true);
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState(false);
  const inFlight = React.useRef(false);

  const pair = async (value: string) => {
    if (inFlight.current) return;
    setError(null);
    if (value.length !== 6) {
      setError("Enter all 6 digits of the pairing code.");
      return;
    }
    inFlight.current = true;
    setPending(true);
    try {
      await bridge.invoke("child_pair", { code: value, childName: null });
      if (autostart) {
        try {
          await bridge.invoke("set_autostart", { enabled: true });
        } catch {
          bridge.toast("Paired, but couldn’t enable start at login. You can turn it on later.", "info");
        }
      }
      setSuccess(true);
      bridge.toast("This computer is now protected.", "success");
      window.setTimeout(() => void bridge.navigate({ name: "child" }), 600);
    } catch (err) {
      setError(friendlyError(err));
      setCode("");
      setPending(false);
      inFlight.current = false;
    }
  };

  const paste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      const digits = text.replace(/\D/g, "").slice(0, 6);
      if (!digits) {
        setError("Your clipboard doesn’t contain a pairing code.");
        return;
      }
      setCode(digits);
      if (digits.length === 6) void pair(digits);
    } catch {
      setError("Couldn’t read the clipboard. Type the code instead.");
    }
  };

  return (
    <div className="mx-auto grid w-full max-w-4xl gap-6 px-4 py-8 md:grid-cols-[1fr_1.1fr] md:items-start">
      <section aria-labelledby="pair-steps" className="flex flex-col gap-3">
        <h2 id="pair-steps" className="m-0 text-lg font-bold">
          How to pair this computer
        </h2>
        <ol className="m-0 flex list-none flex-col gap-3 p-0">
          {STEPS.map((s, i) => (
            <li key={s.title} className="flex gap-3 rounded-md border border-border bg-card p-3">
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
                {i + 1}
              </span>
              <div>
                <p className="m-0 text-sm font-semibold">{s.title}</p>
                <p className="m-0 text-xs text-muted-foreground">{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Pair with a parent</CardTitle>
          <CardDescription>Enter the 6-digit code shown in the parent app.</CardDescription>
        </CardHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void pair(code);
          }}
          noValidate
        >
          <CardContent>
            {error && (
              <Alert variant="destructive" title="Pairing failed">
                {error}
              </Alert>
            )}
            {success && <Alert variant="success">Paired! Opening the launcher…</Alert>}
            <CodeInput
              length={6}
              groupAfter={3}
              label="Pairing code"
              value={code}
              onChange={(v) => {
                setCode(v);
                if (error) setError(null);
              }}
              onComplete={(v) => void pair(v)}
              disabled={pending || success}
              invalid={!!error}
              autoFocus
            />
            <div className="flex flex-wrap items-center justify-center gap-2">
              <Button type="button" variant="outline" size="sm" disabled={pending} onClick={() => void paste()}>
                <ClipboardPaste className="h-4 w-4" aria-hidden="true" /> Paste code
              </Button>
              {bridge.devShortcuts && !bridge.liveBackend && (
                <Button type="button" variant="outline" size="sm" data-testid="dev-demo-code" disabled={pending} onClick={() => setCode("123456")}>
                  Dev: fill 123456
                </Button>
              )}
            </div>
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={autostart}
                disabled={pending}
                onChange={(e) => setAutostart(e.target.checked)}
                className="h-4 w-4 accent-[var(--ms-primary)]"
              />
              Start Watching automatically when this computer starts (recommended)
            </label>
          </CardContent>
          <CardFooter>
            <Button type="submit" size="lg" className="w-full" loading={pending} loadingText="Pairing…" disabled={code.length !== 6 || success}>
              <Link2 className="h-4 w-4" aria-hidden="true" /> Pair this computer
            </Button>
            <Button type="button" variant="ghost" className="w-full" disabled={pending} onClick={() => void bridge.invoke("set_role", { role: "unassigned" }).then(() => bridge.navigate({ name: "role" }))}>
              <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
