import * as React from "react";
import { cn } from "../lib/utils";

export function Switch({
  checked,
  onCheckedChange,
  label,
  description,
  disabled,
  id,
}: {
  checked: boolean;
  onCheckedChange: (v: boolean) => void;
  label: string;
  description?: string;
  disabled?: boolean;
  id?: string;
}) {
  const autoId = React.useId();
  const sid = id ?? autoId;
  return (
    <div className="flex items-start justify-between gap-4 py-2">
      <div>
        <label htmlFor={sid} className="text-sm font-semibold">
          {label}
        </label>
        {description && <p className="m-0 text-xs text-muted-foreground">{description}</p>}
      </div>
      <button
        id={sid}
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onCheckedChange(!checked)}
        className={cn(
          "relative inline-flex h-6 w-11 min-w-0 shrink-0 cursor-pointer items-center justify-start rounded-full p-0 border border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50",
          checked ? "bg-primary" : "bg-input",
        )}
      >
        <span className={cn("absolute left-0.5 top-1/2 h-5 w-5 -translate-y-1/2 rounded-full bg-white shadow transition-transform", checked ? "translate-x-5" : "translate-x-0")} />
      </button>
    </div>
  );
}

export function Slider({
  label,
  value,
  min,
  max,
  step,
  onChange,
  format,
  disabled,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  format?: (v: number) => string;
  disabled?: boolean;
}) {
  const id = React.useId();
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="text-sm font-semibold">
          {label}
        </label>
        <span className="font-mono text-sm font-bold text-primary" aria-hidden="true">
          {format ? format(value) : value}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        aria-valuetext={format ? format(value) : String(value)}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-[var(--ms-primary)]"
      />
      <div className="flex justify-between text-xs text-muted-foreground" aria-hidden="true">
        <span>{format ? format(min) : min}</span>
        <span>{format ? format(max) : max}</span>
      </div>
    </div>
  );
}

export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  return (
    <ol className="m-0 flex list-none flex-wrap items-center gap-2 p-0" aria-label="Progress">
      {steps.map((s, i) => {
        const state = i < current ? "done" : i === current ? "current" : "todo";
        return (
          <li key={s} className="flex items-center gap-2" aria-current={state === "current" ? "step" : undefined}>
            <span
              className={cn(
                "flex h-7 w-7 items-center justify-center rounded-full border text-xs font-bold",
                state === "done" && "border-primary bg-primary text-primary-foreground",
                state === "current" && "border-primary text-primary",
                state === "todo" && "border-border text-muted-foreground",
              )}
            >
              {state === "done" ? "✓" : i + 1}
            </span>
            <span className={cn("hidden text-xs font-semibold sm:inline", state === "todo" ? "text-muted-foreground" : "text-foreground")}>{s}</span>
            {i < steps.length - 1 && <span className="hidden h-px w-6 bg-border sm:inline-block" aria-hidden="true" />}
          </li>
        );
      })}
    </ol>
  );
}

export function ChoiceCard({
  selected,
  onSelect,
  children,
  className,
  label,
}: {
  selected: boolean;
  onSelect: () => void;
  children: React.ReactNode;
  className?: string;
  label: string;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={label}
      onClick={onSelect}
      className={cn(
        "flex cursor-pointer items-center gap-3 rounded-md border bg-card p-3 text-left text-card-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        selected ? "border-primary ring-1 ring-primary" : "border-border hover:bg-muted",
        className,
      )}
    >
      {children}
    </button>
  );
}
