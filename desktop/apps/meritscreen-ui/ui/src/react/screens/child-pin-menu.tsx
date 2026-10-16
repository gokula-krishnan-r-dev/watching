import * as React from "react";
import { ArrowLeft, LogOut, Unlink } from "lucide-react";
import type { AppBridge } from "../bridge";
import { Button } from "../ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "../ui/card";
import { Alert } from "../ui/alert";
import { PinInput } from "../components/code-input";
import { friendlyError } from "../lib/errors";

type Action = "unpair" | "exit";

export function ChildPinMenuScreen({ bridge, childName }: { bridge: AppBridge; childName: string }) {
  const [pin, setPin] = React.useState("");
  const [pending, setPending] = React.useState<Action | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [confirmUnpair, setConfirmUnpair] = React.useState(false);

  const run = async (action: Action) => {
    setError(null);
    if (pin.length !== 4) {
      setError("Enter the 4-digit parent PIN.");
      return;
    }
    if (action === "unpair" && !confirmUnpair) {
      setConfirmUnpair(true);
      return;
    }
    setPending(action);
    try {
      if (action === "unpair") {
        await bridge.invoke("child_pin_unpair", { pin });
        bridge.toast("This computer was unpaired.", "info");
        await bridge.navigate({ name: "role" });
      } else {
        await bridge.invoke("child_exit_with_pin", { pin });
      }
    } catch (err) {
      setError(friendlyError(err));
      setPin("");
      setConfirmUnpair(false);
    } finally {
      setPending(null);
    }
  };

  const back = async () => {
    try {
      await bridge.invoke("child_dismiss_pin_menu");
    } finally {
      await bridge.navigate({ name: "child" });
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-md flex-col px-4 py-8">
      <Card>
        <CardHeader className="items-center text-center">
          <CardTitle>Parent menu</CardTitle>
          <CardDescription>
            Enter the parent PIN to manage {childName ? `${childName}’s` : "this"} computer.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {error && <Alert variant="destructive">{error}</Alert>}
          {confirmUnpair && !error && (
            <Alert title="Unpair this computer?">
              Protection stops and the parent will need a new code to pair again. Press Unpair again to confirm.
            </Alert>
          )}
          <PinInput
            value={pin}
            onChange={(v) => {
              setPin(v);
              if (error) setError(null);
            }}
            disabled={pending !== null}
            invalid={!!error}
            autoFocus
          />
        </CardContent>
        <CardFooter>
          <Button
            size="lg"
            variant={confirmUnpair ? "destructive" : "default"}
            className="w-full"
            disabled={pin.length !== 4}
            loading={pending === "unpair"}
            loadingText="Unpairing…"
            onClick={() => void run("unpair")}
          >
            <Unlink className="h-4 w-4" aria-hidden="true" /> {confirmUnpair ? "Confirm unpair" : "Unpair this computer"}
          </Button>
          <Button
            variant="secondary"
            className="w-full"
            disabled={pin.length !== 4 || pending !== null}
            loading={pending === "exit"}
            loadingText="Exiting…"
            onClick={() => void run("exit")}
          >
            <LogOut className="h-4 w-4" aria-hidden="true" /> Exit Watching
          </Button>
          <Button variant="ghost" className="w-full" disabled={pending !== null} onClick={() => void back()}>
            <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Back
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
