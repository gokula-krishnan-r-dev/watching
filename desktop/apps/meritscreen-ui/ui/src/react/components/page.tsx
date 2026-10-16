import * as React from "react";
import { ArrowLeft } from "lucide-react";
import { Button } from "../ui/button";
import { cn } from "../lib/utils";

/** Standard parent page layout: back link, title, optional actions, responsive width. */
export function Page({
  title,
  description,
  onBack,
  backLabel = "Back",
  actions,
  children,
  width = "lg",
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  onBack?: () => void;
  backLabel?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  width?: "md" | "lg" | "xl";
}) {
  return (
    <div className={cn("mx-auto flex w-full flex-col gap-5 px-4 py-6", width === "md" ? "max-w-2xl" : width === "lg" ? "max-w-4xl" : "max-w-6xl")}>
      {onBack && (
        <Button variant="ghost" size="sm" className="self-start" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> {backLabel}
        </Button>
      )}
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="m-0 text-2xl font-bold tracking-tight">{title}</h1>
          {description && <p className="m-0 mt-1 text-sm text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </header>
      {children}
    </div>
  );
}

export function Section({ title, description, children, className }: { title?: string; description?: string; children: React.ReactNode; className?: string }) {
  return (
    <section className={cn("flex flex-col gap-3 rounded-lg border border-border bg-card p-5", className)}>
      {(title || description) && (
        <div>
          {title && <h2 className="m-0 text-base font-bold">{title}</h2>}
          {description && <p className="m-0 mt-0.5 text-sm text-muted-foreground">{description}</p>}
        </div>
      )}
      {children}
    </section>
  );
}
