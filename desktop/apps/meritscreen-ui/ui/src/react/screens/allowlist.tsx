import * as React from "react";
import { Save, Search, ShieldCheck } from "lucide-react";
import type { AppBridge } from "../bridge";
import type { AppRule } from "../lib/types";
import { Button } from "../ui/button";
import { Alert } from "../ui/alert";
import { Badge } from "../ui/badge";
import { Input } from "../ui/input";
import { EmptyState, ErrorState, LoadingState } from "../components/states";
import { Page } from "../components/page";
import { useChildDetail } from "../lib/use-child-detail";
import { friendlyError } from "../lib/errors";
import { cn } from "../lib/utils";

export function AllowlistScreen({ bridge, childId }: { bridge: AppBridge; childId: string }) {
  const { detail, loading, error, reload } = useChildDetail(bridge, childId);
  const [rules, setRules] = React.useState<AppRule[]>([]);
  const [saved, setSaved] = React.useState<string>("[]");
  const [query, setQuery] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!detail) return;
    const sorted = [...detail.appRules].sort((a, b) => (a.displayName || a.appId).localeCompare(b.displayName || b.appId));
    setRules(sorted);
    setSaved(JSON.stringify(sorted));
  }, [detail]);

  const back = () => void bridge.navigate({ name: "child-detail", childId });
  if (loading && !detail) return <LoadingState />;
  if (error || !detail) return <ErrorState error={error} onRetry={() => void reload()} onBack={back} />;

  const dirty = JSON.stringify(rules) !== saved;
  const q = query.trim().toLowerCase();
  const visible = rules.filter((r) => !q || (r.displayName || r.appId).toLowerCase().includes(q) || r.packageOrBundleId.toLowerCase().includes(q));
  const set = (appId: string, patch: Partial<AppRule>) => setRules((rs) => rs.map((r) => (r.appId === appId ? { ...r, ...patch } : r)));
  const setAll = (allowed: boolean) => setRules((rs) => rs.map((r) => (r.isEmergency || visible.some((v) => v.appId === r.appId) ? { ...r, allowed: r.isEmergency ? true : allowed } : r)));

  const save = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      await bridge.invoke("parent_update_app_rules", { childId, rules });
      setSaved(JSON.stringify(rules));
      bridge.toast("App rules saved", "success");
    } catch (e) {
      setSaveError(friendlyError(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Page title="Allowed apps" description={`${detail.card.displayName} · Choose which apps can be used. Emergency apps are always allowed.`} onBack={back} backLabel={detail.card.displayName}>
      {rules.length === 0 ? (
        <EmptyState
          title={detail.card.pairedDeviceCount > 0 ? "No apps reported yet" : "No device paired"}
          body={detail.card.pairedDeviceCount > 0 ? "Apps appear here once the device finishes scanning. Refresh in a minute." : "Pair a device to see its installed apps."}
          action={
            detail.card.pairedDeviceCount > 0 ? (
              <Button variant="secondary" onClick={() => void reload()}>Refresh</Button>
            ) : (
              <Button onClick={() => void bridge.navigate({ name: "pairing", childId })}>Pair a device</Button>
            )
          }
        />
      ) : (
        <>
          {saveError && <Alert variant="destructive" title="Couldn’t save">{saveError}</Alert>}
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px] flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input aria-label="Search apps" placeholder="Search apps" value={query} onChange={(e) => setQuery(e.target.value)} className="pl-9" />
            </div>
            <Button variant="secondary" size="sm" onClick={() => setAll(true)}>Allow {q ? "shown" : "all"}</Button>
            <Button variant="secondary" size="sm" onClick={() => setAll(false)}>Block {q ? "shown" : "all"}</Button>
          </div>
          <p className="m-0 text-xs text-muted-foreground" aria-live="polite">
            {rules.filter((r) => r.allowed).length} of {rules.length} allowed{q && ` · ${visible.length} shown`}
          </p>
          {visible.length === 0 ? (
            <EmptyState title="No matching apps" body="Try a different search." />
          ) : (
            <ul className="m-0 flex list-none flex-col divide-y divide-border rounded-lg border border-border bg-card p-0">
              {visible.map((r) => (
                <li key={r.appId} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="m-0 truncate text-sm font-semibold">{r.displayName || r.appId}</p>
                    <p className="m-0 truncate text-xs text-muted-foreground">{r.packageOrBundleId}</p>
                  </div>
                  {r.isEmergency && (
                    <Badge variant="success">
                      <ShieldCheck className="h-3 w-3" aria-hidden="true" /> Emergency
                    </Badge>
                  )}
                  <div role="radiogroup" aria-label={`${r.displayName || r.appId} access`} className="flex overflow-hidden rounded-md border border-border">
                    {[true, false].map((allow) => (
                      <button
                        key={String(allow)}
                        type="button"
                        role="radio"
                        aria-checked={r.allowed === allow}
                        disabled={r.isEmergency && !allow}
                        onClick={() => set(r.appId, { allowed: allow })}
                        className={cn(
                          "cursor-pointer px-3 py-1.5 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40",
                          r.allowed === allow ? (allow ? "bg-primary text-primary-foreground" : "bg-destructive text-white") : "bg-card text-muted-foreground hover:bg-muted",
                        )}
                      >
                        {allow ? "Allow" : "Block"}
                      </button>
                    ))}
                  </div>
                </li>
              ))}
            </ul>
          )}
          <div className="sticky bottom-0 flex justify-end gap-2 border-t border-border bg-background/90 py-3 backdrop-blur">
            <Button variant="ghost" disabled={!dirty || saving} onClick={() => setRules(JSON.parse(saved) as AppRule[])}>Discard</Button>
            <Button disabled={!dirty} loading={saving} loadingText="Saving…" onClick={() => void save()}>
              <Save className="h-4 w-4" aria-hidden="true" /> Save changes
            </Button>
          </div>
        </>
      )}
    </Page>
  );
}
