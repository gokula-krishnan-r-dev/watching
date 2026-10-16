import * as React from "react";
import { ArrowRight, Laptop, Users } from "lucide-react";
import type { AppBridge } from "../bridge";
import { Button } from "../ui/button";
import { Card } from "../ui/card";
import { Badge } from "../ui/badge";
import { Alert } from "../ui/alert";
import { friendlyError } from "../lib/errors";

interface Props {
  bridge: AppBridge;
  platform: string;
  onResetLocal?: () => Promise<void>;
}

export function RoleSelectScreen({ bridge, platform, onResetLocal }: Props) {
  const [pending, setPending] = React.useState<"parent" | "child" | "reset" | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const chooseChild = async () => {
    setError(null);
    setPending("child");
    try {
      await bridge.invoke("set_role", { role: "child" });
      await bridge.navigate({ name: "child" });
    } catch (e) {
      setError(friendlyError(e));
      setPending(null);
    }
  };

  const options = [
    {
      id: "parent" as const,
      icon: <Users className="h-6 w-6" aria-hidden="true" />,
      title: "I’m a parent",
      body: "Sign in to set screen-time rules, approve apps and see quiz progress.",
      tags: ["Rules", "Approvals", "Reports"],
      cta: "Continue as parent",
      primary: true,
      onSelect: () => {
        setPending("parent");
        void bridge.navigate({ name: "parent-auth", step: "email" });
      },
    },
    {
      id: "child" as const,
      icon: <Laptop className="h-6 w-6" aria-hidden="true" />,
      title: "This is my child’s computer",
      body: "Pair it with a parent. Watching then protects screen time in the background.",
      tags: ["Always on", "Quiz to earn time", "Calm lock"],
      cta: "Set up child computer",
      primary: false,
      onSelect: () => void chooseChild(),
    },
  ];

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col items-center gap-6 px-4 py-10">
      <div className="text-center">
        <h1 className="m-0 text-2xl font-bold tracking-tight sm:text-3xl">Who is using this computer?</h1>
        <p className="mx-auto mt-2 max-w-lg text-sm text-muted-foreground">
          Screen time is earned through short, playful quizzes. Choose how this computer will be used.
        </p>
      </div>
      {error && (
        <Alert variant="destructive" title="Couldn’t continue" className="max-w-xl">
          {error}
        </Alert>
      )}
      <div className="grid w-full gap-4 sm:grid-cols-2">
        {options.map((o) => (
          <Card key={o.id} className="flex flex-col gap-3 p-5">
            <div className="flex items-start gap-3">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-secondary text-secondary-foreground">{o.icon}</div>
              <div>
                <h2 className="m-0 text-base font-bold">{o.title}</h2>
                <p className="m-0 mt-1 text-sm text-muted-foreground">{o.body}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {o.tags.map((t) => (
                <Badge key={t} variant="outline">
                  {t}
                </Badge>
              ))}
            </div>
            <Button
              className="mt-auto w-full"
              variant={o.primary ? "default" : "secondary"}
              loading={pending === o.id}
              disabled={pending !== null && pending !== o.id}
              onClick={o.onSelect}
            >
              {o.cta} <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </Card>
        ))}
      </div>
      <div className="flex w-full flex-wrap items-center justify-between gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
        <span>
          Platform: <strong className="text-foreground">{platform.toUpperCase() || "—"}</strong>
        </span>
        {bridge.devShortcuts && onResetLocal && (
          <Button
            size="sm"
            variant="outline"
            data-testid="dev-reset"
            loading={pending === "reset"}
            onClick={async () => {
              setPending("reset");
              try {
                await onResetLocal();
              } finally {
                setPending(null);
              }
            }}
          >
            Dev: reset local app state
          </Button>
        )}
      </div>
    </div>
  );
}
