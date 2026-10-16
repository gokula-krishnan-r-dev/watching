import * as React from "react";
import { createPortal } from "react-dom";
import { cn } from "../lib/utils";

/** Accessible modal dialog: focus moves in, Esc closes, focus returns on close. */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  className,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: React.ReactNode;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}) {
  const ref = React.useRef<HTMLDivElement>(null);
  const titleId = React.useId();
  React.useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const first = ref.current?.querySelector<HTMLElement>("input,button,[tabindex]:not([tabindex='-1'])");
    first?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
      if (e.key === "Tab" && ref.current) {
        const f = Array.from(ref.current.querySelectorAll<HTMLElement>("input,button,select,textarea,[tabindex]:not([tabindex='-1'])")).filter((el) => !el.hasAttribute("disabled"));
        if (!f.length) return;
        const [a, z] = [f[0], f[f.length - 1]];
        if (e.shiftKey && document.activeElement === a) {
          e.preventDefault();
          z.focus();
        } else if (!e.shiftKey && document.activeElement === z) {
          e.preventDefault();
          a.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey, true);
    return () => {
      document.removeEventListener("keydown", onKey, true);
      prev?.focus?.();
    };
  }, [open, onClose]);
  if (!open) return null;
  return createPortal(
    <div className="ms-react-root fixed inset-0 z-[70] flex items-center justify-center bg-black/50 p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={titleId} className={cn("w-full max-w-md rounded-lg border border-border bg-card p-6 text-card-foreground shadow-xl", className)}>
        <h2 id={titleId} className="m-0 text-lg font-bold">
          {title}
        </h2>
        {description && <div className="mt-1 text-sm text-muted-foreground">{description}</div>}
        {children && <div className="mt-4 flex flex-col gap-3">{children}</div>}
        {footer && <div className="mt-5 flex flex-wrap justify-end gap-2">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
