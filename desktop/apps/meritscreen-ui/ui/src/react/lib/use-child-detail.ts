import * as React from "react";
import type { AppBridge } from "../bridge";
import type { ChildDetail, ChildPolicy } from "./types";
import { friendlyError } from "./errors";

/** Loads a child's detail; exposes reload + loading/error. */
export function useChildDetail(bridge: AppBridge, childId: string) {
  const [detail, setDetail] = React.useState<ChildDetail | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const reload = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDetail(await bridge.invoke<ChildDetail>("parent_child_detail", { childId }));
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, [bridge, childId]);
  React.useEffect(() => {
    void reload();
  }, [reload]);
  return { detail, setDetail, loading, error, reload };
}

/** Draft editing of a child policy with dirty tracking + save. */
export function usePolicyDraft(bridge: AppBridge, childId: string, policy: ChildPolicy | undefined) {
  const [draft, setDraft] = React.useState<ChildPolicy | null>(policy ?? null);
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState<string | null>(null);
  const [saved, setSaved] = React.useState<ChildPolicy | null>(policy ?? null);
  React.useEffect(() => {
    if (policy) {
      setDraft(policy);
      setSaved(policy);
    }
  }, [policy]);
  const dirty = !!draft && !!saved && JSON.stringify(draft) !== JSON.stringify(saved);
  const update = <K extends keyof ChildPolicy>(k: K, v: ChildPolicy[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d));
  const save = async (): Promise<boolean> => {
    if (!draft) return false;
    setSaving(true);
    setSaveError(null);
    try {
      await bridge.invoke("parent_update_policy", { childId, policy: draft });
      setSaved(draft);
      bridge.toast("Saved", "success");
      return true;
    } catch (e) {
      setSaveError(friendlyError(e));
      return false;
    } finally {
      setSaving(false);
    }
  };
  const reset = () => saved && setDraft(saved);
  return { draft, update, dirty, saving, saveError, save, reset };
}
