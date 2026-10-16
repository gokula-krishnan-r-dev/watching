import * as React from "react";
import { Check, Copy, RefreshCw } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { ChildDetail, PairingOffer } from "../lib/types";
import { Button } from "../ui/button";
import { Alert } from "../ui/alert";
import { Spinner } from "../ui/spinner";
import { friendlyError } from "../lib/errors";

const POLL_MS = 3000;

function useNow(active: boolean) {
  const [now, setNow] = React.useState(() => Date.now());
  React.useEffect(() => {
    if (!active) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [active]);
  return now;
}

export function PairingOfferPanel({
  bridge,
  childId,
  childName,
  onPaired,
}: {
  bridge: AppBridge;
  childId: string;
  childName: string;
  onPaired: () => void;
}) {
  const [offer, setOffer] = React.useState<PairingOffer | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [copied, setCopied] = React.useState(false);
  const [paired, setPaired] = React.useState(false);
  const now = useNow(!!offer && !paired);

  const create = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setOffer(await bridge.invoke<PairingOffer>("parent_create_pairing", { childId }));
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [bridge, childId]);

  React.useEffect(() => {
    void create();
  }, [create]);

  // Poll for the child device consuming the code.
  React.useEffect(() => {
    if (!offer || paired) return;
    let alive = true;
    const t = window.setInterval(async () => {
      try {
        const d = await bridge.invoke<ChildDetail>("parent_child_detail", { childId });
        if (alive && d.card.pairedDeviceCount > 0) {
          setPaired(true);
          bridge.toast(`${childName}’s device is paired.`, "success");
          onPaired();
        }
      } catch {
        /* transient; keep polling */
      }
    }, POLL_MS);
    return () => {
      alive = false;
      window.clearInterval(t);
    };
  }, [offer, paired, bridge, childId, childName, onPaired]);

  const secondsLeft = offer ? Math.max(0, Math.round((offer.expiresAtEpochMs - now) / 1000)) : 0;
  const expired = !!offer && offer.expiresAtEpochMs > 0 && secondsLeft === 0;
  const code = offer?.code.replace(/\D/g, "") ?? "";

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      bridge.toast("Couldn’t copy. Read the code out instead.", "error");
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
        <Spinner className="text-primary" /> Creating a pairing code…
      </div>
    );
  }
  if (error) {
    return (
      <div className="flex flex-col gap-3">
        <Alert variant="destructive" title="Couldn’t create a pairing code">
          {error}
        </Alert>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void create()}>
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Try again
          </Button>
          {/PIN/i.test(error) && (
            <Button variant="outline" onClick={() => void bridge.navigate({ name: "account" })}>
              Set parent PIN
            </Button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3 text-center">
      {paired ? (
        <Alert variant="success" title="Paired!">
          {childName}’s device is now protected.
        </Alert>
      ) : (
        <>
          <p className="m-0 text-sm text-muted-foreground">
            On {childName}’s computer, open Watching → <strong>Set up child computer</strong> and enter:
          </p>
          <div
            className="font-mono text-4xl font-bold tracking-[0.3em] text-foreground"
            aria-label={`Pairing code ${code.split("").join(" ")}`}
            data-testid="pairing-code"
          >
            {code.slice(0, 3)} {code.slice(3)}
          </div>
          {expired ? (
            <Alert variant="destructive">This code expired. Create a new one.</Alert>
          ) : (
            offer!.expiresAtEpochMs > 0 && (
              <p className="m-0 text-xs text-muted-foreground" aria-live="polite">
                Expires in {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, "0")}
              </p>
            )
          )}
          <div className="flex flex-wrap justify-center gap-2">
            <Button variant="secondary" size="sm" onClick={() => void copy()} disabled={expired}>
              {copied ? <Check className="h-4 w-4" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
              {copied ? "Copied" : "Copy code"}
            </Button>
            <Button variant="outline" size="sm" onClick={() => void create()}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" /> New code
            </Button>
            {bridge.devShortcuts && !bridge.liveBackend && (
              <Button
                variant="outline"
                size="sm"
                data-testid="dev-sim-pair"
                onClick={async () => {
                  await bridge.invoke("parent_mark_paired", { childId, platform: "macos" });
                }}
              >
                Dev: simulate pairing
              </Button>
            )}
          </div>
          <p className="m-0 flex items-center gap-2 text-xs text-muted-foreground">
            <Spinner className="h-3 w-3" label="Waiting for the device" /> Waiting for the device to connect…
          </p>
        </>
      )}
    </div>
  );
}
