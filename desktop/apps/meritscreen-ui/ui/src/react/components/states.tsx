import * as React from "react";
import { AlertTriangle, Inbox } from "lucide-react";
import { Button } from "../ui/button";
import { Spinner } from "../ui/spinner";
import { friendlyError } from "../lib/errors";

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex min-h-[240px] flex-col items-center justify-center gap-3 text-muted-foreground" aria-live="polite">
      <Spinner className="h-8 w-8 text-primary" label={label} />
      <p className="m-0 text-sm">{label}</p>
    </div>
  );
}

export function ErrorState({
  error,
  title = "Something went wrong",
  onRetry,
  onBack,
}: {
  error: unknown;
  title?: string;
  onRetry?: () => void;
  onBack?: () => void;
}) {
  return (
    <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-3 py-10 text-center">
      <AlertTriangle className="h-10 w-10 text-destructive" aria-hidden="true" />
      <h2 className="m-0 text-lg font-bold">{title}</h2>
      <p className="m-0 text-sm text-muted-foreground">{friendlyError(error)}</p>
      <div className="flex gap-2">
        {onRetry && <Button onClick={onRetry}>Try again</Button>}
        {onBack && (
          <Button variant="secondary" onClick={onBack}>
            Go back
          </Button>
        )}
      </div>
    </div>
  );
}

export function EmptyState({
  title,
  body,
  action,
  icon,
}: {
  title: string;
  body?: string;
  action?: React.ReactNode;
  icon?: React.ReactNode;
}) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-10 text-center">
      {icon ?? <Inbox className="h-10 w-10 text-muted-foreground" aria-hidden="true" />}
      <h2 className="m-0 text-lg font-bold">{title}</h2>
      {body && <p className="m-0 text-sm text-muted-foreground">{body}</p>}
      {action}
    </div>
  );
}

/** Small async helper: tracks loading/error for a submit action. */
export function useAsyncAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const mounted = React.useRef(true);
  React.useEffect(() => () => {
    mounted.current = false;
  }, []);
  const run = React.useCallback(
    async (...args: A): Promise<R | undefined> => {
      setPending(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (e) {
        if (mounted.current) setError(friendlyError(e));
        return undefined;
      } finally {
        if (mounted.current) setPending(false);
      }
    },
    [fn],
  );
  return { run, pending, error, setError };
}
