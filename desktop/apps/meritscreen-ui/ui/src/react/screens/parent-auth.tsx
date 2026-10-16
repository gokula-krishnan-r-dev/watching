import * as React from "react";
import { ArrowLeft, Mail, ShieldCheck } from "lucide-react";
import type { AppBridge } from "../bridge";
import { Button } from "../ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "../ui/card";
import { FieldError, Input, Label } from "../ui/input";
import { Alert } from "../ui/alert";
import { OtpInput } from "../components/code-input";
import { friendlyError, validateEmail } from "../lib/errors";

const RESEND_SECONDS = 30;
const TESTER_EMAILS = ["tester4.parent@anajyo.com", "tester1.parent@anajyo.com"];

interface Props {
  bridge: AppBridge;
  step: "email" | "otp";
  email: string;
  onEmailChange: (email: string) => void;
}

export function ParentAuthScreen({ bridge, step, email, onEmailChange }: Props) {
  return (
    <div className="mx-auto flex w-full max-w-md flex-col px-4 py-8 sm:py-12">
      {step === "email" ? (
        <EmailStep bridge={bridge} email={email} onEmailChange={onEmailChange} />
      ) : (
        <OtpStep bridge={bridge} email={email} />
      )}
    </div>
  );
}

function EmailStep({ bridge, email, onEmailChange }: Omit<Props, "step">) {
  const [value, setValue] = React.useState(email);
  const [fieldError, setFieldError] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [sending, setSending] = React.useState(false);
  const [oauthPending, setOauthPending] = React.useState<"google" | "apple" | null>(null);

  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError(null);
    const v = validateEmail(value);
    setFieldError(v);
    if (v) return;
    const normalized = value.trim().toLowerCase();
    setSending(true);
    try {
      await bridge.invoke("parent_send_otp", { email: normalized });
      onEmailChange(normalized);
      await bridge.navigate({ name: "parent-auth", step: "otp", email: normalized });
    } catch (err) {
      setError(friendlyError(err));
      setSending(false);
    }
  };

  const oauth = async (provider: "google" | "apple") => {
    setError(null);
    setOauthPending(provider);
    try {
      if (provider === "google") {
        // Desktop loopback OAuth is not shipped yet (needs a desktop OAuth client id).
        await bridge.invoke<string>("parent_google_url");
        setError("Google sign-in isn’t available on desktop yet. Please use email sign-in.");
      } else {
        await bridge.invoke("parent_apple_sign_in");
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setOauthPending(null);
    }
  };

  return (
    <Card>
      <CardHeader className="items-center text-center">
        <div className="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
          <ShieldCheck className="h-6 w-6" aria-hidden="true" />
        </div>
        <CardTitle>Parent sign in</CardTitle>
        <CardDescription>Set screen-time rules, approve apps and review quiz progress.</CardDescription>
      </CardHeader>
      <form onSubmit={submit} noValidate>
        <CardContent>
          {error && (
            <Alert variant="destructive" title="Couldn’t sign in">
              {error}
            </Alert>
          )}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="parent-email">Email</Label>
            <Input
              id="parent-email"
              type="email"
              autoComplete="username"
              placeholder="you@example.com"
              value={value}
              autoFocus
              disabled={sending}
              aria-invalid={!!fieldError}
              aria-describedby={fieldError ? "parent-email-error" : undefined}
              onChange={(e) => {
                setValue(e.target.value);
                if (fieldError) setFieldError(null);
              }}
            />
            <FieldError id="parent-email-error">{fieldError}</FieldError>
          </div>
          {bridge.devShortcuts && (
            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground" data-testid="dev-tester-chips">
              <span className="font-semibold">Dev testers:</span>
              {TESTER_EMAILS.map((t) => (
                <Button key={t} type="button" size="sm" variant="outline" onClick={() => setValue(t)}>
                  {t}
                </Button>
              ))}
            </div>
          )}
        </CardContent>
        <CardFooter>
          <Button type="submit" size="lg" className="w-full" loading={sending} loadingText="Sending code…">
            <Mail className="h-4 w-4" aria-hidden="true" /> Send verification code
          </Button>
          <div className="my-1 flex items-center gap-3 text-xs text-muted-foreground" aria-hidden="true">
            <span className="h-px flex-1 bg-border" /> or <span className="h-px flex-1 bg-border" />
          </div>
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            disabled={sending}
            loading={oauthPending === "google"}
            onClick={() => void oauth("google")}
          >
            Continue with Google
          </Button>
          {bridge.appleSignInAvailable && (
            <Button
              type="button"
              variant="secondary"
              className="w-full"
              disabled={sending}
              loading={oauthPending === "apple"}
              onClick={() => void oauth("apple")}
            >
              Sign in with Apple
            </Button>
          )}
          <Button type="button" variant="ghost" className="w-full" disabled={sending} onClick={() => void bridge.navigate({ name: "role" })}>
            <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function OtpStep({ bridge, email }: { bridge: AppBridge; email: string }) {
  const [code, setCode] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [verifying, setVerifying] = React.useState(false);
  const [resendIn, setResendIn] = React.useState(RESEND_SECONDS);
  const [resending, setResending] = React.useState(false);
  const inFlight = React.useRef(false);

  React.useEffect(() => {
    if (resendIn <= 0) return;
    const t = window.setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => window.clearTimeout(t);
  }, [resendIn]);

  const verify = async (value: string) => {
    if (inFlight.current) return;
    setError(null);
    if (value.length !== 6) {
      setError("Enter all 6 digits of the code.");
      return;
    }
    inFlight.current = true;
    setVerifying(true);
    try {
      await bridge.invoke("parent_verify_otp", { email, code: value });
      const dash = await bridge.invoke<{ state: string; data?: { children?: unknown[] } }>("parent_dashboard");
      const hasChildren = dash.state === "success" && (dash.data?.children?.length ?? 0) > 0;
      if (hasChildren) localStorage.setItem("meritscreen_onboarding_completed", "true");
      const done = hasChildren || localStorage.getItem("meritscreen_onboarding_completed") === "true";
      await bridge.navigate({ name: done ? "parent-home" : "parent-onboard" });
    } catch (err) {
      setError(friendlyError(err));
      setCode("");
      setVerifying(false);
      inFlight.current = false;
    }
  };

  const resend = async () => {
    setError(null);
    setResending(true);
    try {
      await bridge.invoke("parent_send_otp", { email });
      bridge.toast("A new code is on its way.", "info");
      setResendIn(RESEND_SECONDS);
      setCode("");
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setResending(false);
    }
  };

  if (!email) {
    return (
      <Card>
        <CardContent className="pt-6">
          <Alert variant="destructive" title="Email missing">Go back and enter your email first.</Alert>
          <Button onClick={() => void bridge.navigate({ name: "parent-auth", step: "email" })}>Enter email</Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="items-center text-center">
        <CardTitle>Check your email</CardTitle>
        <CardDescription>
          We sent a 6-digit code to <strong className="text-foreground">{email}</strong>.
        </CardDescription>
      </CardHeader>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void verify(code);
        }}
        noValidate
      >
        <CardContent>
          {error && (
            <Alert variant="destructive" title="Verification failed">
              {error}
            </Alert>
          )}
          <OtpInput
            value={code}
            onChange={(v) => {
              setCode(v);
              if (error) setError(null);
            }}
            onComplete={(v) => void verify(v)}
            disabled={verifying}
            invalid={!!error}
            autoFocus
            describedBy="otp-help"
          />
          <p id="otp-help" className="m-0 text-center text-xs text-muted-foreground">
            The code expires in 10 minutes. You can paste it from your email.
            {bridge.devShortcuts && <span data-testid="dev-otp-hint"> Dev build: 123456 / 424242 also work.</span>}
          </p>
        </CardContent>
        <CardFooter>
          <Button type="submit" size="lg" className="w-full" loading={verifying} loadingText="Verifying…" disabled={code.length !== 6}>
            Verify and continue
          </Button>
          <Button type="button" variant="ghost" className="w-full" disabled={resendIn > 0 || verifying} loading={resending} onClick={() => void resend()}>
            {resendIn > 0 ? `Resend code in ${resendIn}s` : "Resend code"}
          </Button>
          <Button type="button" variant="ghost" className="w-full" disabled={verifying} onClick={() => void bridge.navigate({ name: "parent-auth", step: "email" })}>
            Use a different email
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
