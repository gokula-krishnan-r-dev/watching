import * as React from "react";
import { cn } from "../lib/utils";

export interface CodeInputProps {
  /** Number of digits (6 for OTP/pairing, 4 for PIN). */
  length: number;
  value: string;
  onChange: (value: string) => void;
  /** Fired once all digits are filled (after change). */
  onComplete?: (value: string) => void;
  /** Mask digits as dots (PIN). */
  mask?: boolean;
  disabled?: boolean;
  invalid?: boolean;
  autoFocus?: boolean;
  /** Accessible group label, e.g. "Verification code". */
  label: string;
  /** id of an element describing errors/help. */
  describedBy?: string;
  /** Visual gap after this index (e.g. 3 → "123 456"). */
  groupAfter?: number;
  /** autocomplete hint for the first cell ("one-time-code" for OTP). */
  autoComplete?: string;
  className?: string;
  name?: string;
}

const onlyDigits = (s: string) => s.replace(/\D/g, "");

/**
 * Segmented numeric code input (shadcn "InputOTP" pattern, dependency-free).
 * - numeric only, one digit per cell, auto-advance
 * - Backspace clears current cell or moves back; Arrow/Home/End navigation
 * - paste anywhere fills from that cell (non-digits stripped)
 * - optional masking for PINs; full ARIA labelling
 */
export function CodeInput({
  length,
  value,
  onChange,
  onComplete,
  mask = false,
  disabled = false,
  invalid = false,
  autoFocus = false,
  label,
  describedBy,
  groupAfter,
  autoComplete,
  className,
  name,
}: CodeInputProps) {
  const refs = React.useRef<Array<HTMLInputElement | null>>([]);
  const digits = React.useMemo(() => {
    const clean = onlyDigits(value).slice(0, length);
    return Array.from({ length }, (_, i) => clean[i] ?? "");
  }, [value, length]);

  const focusCell = (i: number) => {
    const el = refs.current[Math.max(0, Math.min(length - 1, i))];
    el?.focus();
    el?.select();
  };

  React.useEffect(() => {
    if (autoFocus && !disabled) {
      const first = digits.findIndex((d) => d === "");
      focusCell(first === -1 ? length - 1 : first);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoFocus, disabled]);

  const commit = (next: string[]) => {
    const joined = next.join("");
    onChange(joined);
    if (onComplete && joined.length === length && next.every((d) => d !== "")) {
      onComplete(joined);
    }
  };

  const fillFrom = (start: number, text: string) => {
    const incoming = onlyDigits(text);
    if (!incoming) return;
    const next = [...digits];
    // A full-length paste always replaces the whole code.
    const from = incoming.length >= length ? 0 : start;
    let i = from;
    for (const ch of incoming) {
      if (i >= length) break;
      next[i++] = ch;
    }
    // Keep the value contiguous (no gaps before filled cells).
    const compact = next.join("");
    const arr = Array.from({ length }, (_, k) => compact[k] ?? "");
    commit(arr);
    focusCell(Math.min(i, length - 1));
  };

  const handleChange = (i: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    const d = onlyDigits(raw);
    if (!d) return; // reject non-digits silently
    if (d.length > 1) {
      fillFrom(i, d);
      return;
    }
    const next = [...digits];
    // Write into the first empty cell at or before i so the code stays contiguous.
    const firstEmpty = next.findIndex((x) => x === "");
    const target = firstEmpty !== -1 && firstEmpty < i ? firstEmpty : i;
    next[target] = d;
    commit(next);
    if (target < length - 1) focusCell(target + 1);
  };

  const handleKeyDown = (i: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    switch (e.key) {
      case "Backspace": {
        e.preventDefault();
        const next = [...digits];
        if (next[i]) {
          // remove this digit and shift the rest left
          next.splice(i, 1);
          next.push("");
          commit(next);
        } else if (i > 0) {
          next.splice(i - 1, 1);
          next.push("");
          commit(next);
          focusCell(i - 1);
        }
        break;
      }
      case "Delete": {
        e.preventDefault();
        const next = [...digits];
        next.splice(i, 1);
        next.push("");
        commit(next);
        break;
      }
      case "ArrowLeft":
        e.preventDefault();
        focusCell(i - 1);
        break;
      case "ArrowRight":
        e.preventDefault();
        focusCell(i + 1);
        break;
      case "Home":
        e.preventDefault();
        focusCell(0);
        break;
      case "End":
        e.preventDefault();
        focusCell(length - 1);
        break;
      default:
        // Block letters/symbols; allow digits, Tab, Enter, modifier shortcuts.
        if (e.key.length === 1 && !/\d/.test(e.key) && !e.metaKey && !e.ctrlKey) {
          e.preventDefault();
        }
    }
  };

  const handlePaste = (i: number, e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    fillFrom(i, e.clipboardData.getData("text"));
  };

  return (
    <div
      role="group"
      aria-label={label}
      aria-describedby={describedBy}
      className={cn("flex items-center justify-center gap-2", className)}
    >
      {digits.map((d, i) => (
        <React.Fragment key={i}>
          <input
            ref={(el) => {
              refs.current[i] = el;
            }}
            name={name ? `${name}-${i}` : undefined}
            type={mask ? "password" : "text"}
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={length}
            autoComplete={i === 0 ? autoComplete ?? "off" : "off"}
            aria-label={`${label} digit ${i + 1} of ${length}`}
            aria-invalid={invalid || undefined}
            disabled={disabled}
            value={d}
            onChange={(e) => handleChange(i, e)}
            onKeyDown={(e) => handleKeyDown(i, e)}
            onPaste={(e) => handlePaste(i, e)}
            onFocus={(e) => e.currentTarget.select()}
            className={cn(
              "h-14 w-11 rounded-md border border-input bg-card text-center font-mono text-2xl font-bold text-foreground caret-primary",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50",
              d && "border-primary",
              invalid && "border-destructive",
            )}
          />
          {groupAfter !== undefined && i === groupAfter - 1 && i < length - 1 && (
            <span aria-hidden="true" className="px-1 text-muted-foreground">
              –
            </span>
          )}
        </React.Fragment>
      ))}
    </div>
  );
}

/** 6-digit email verification code. */
export function OtpInput(props: Omit<CodeInputProps, "length" | "mask" | "label"> & { label?: string; length?: number }) {
  return (
    <CodeInput
      length={props.length ?? 6}
      label={props.label ?? "Verification code"}
      autoComplete="one-time-code"
      groupAfter={3}
      {...props}
    />
  );
}

/** Masked 4-digit parent PIN (Android: PARENT_PIN_MIN_LENGTH 4). */
export function PinInput(props: Omit<CodeInputProps, "length" | "mask" | "label"> & { label?: string; length?: number; reveal?: boolean }) {
  const { reveal, ...rest } = props;
  return <CodeInput length={props.length ?? 4} label={props.label ?? "Parent PIN"} mask={!reveal} {...rest} />;
}
