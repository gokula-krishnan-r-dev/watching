import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../lib/utils";
import { Spinner } from "./spinner";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md font-semibold transition-colors cursor-pointer select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ms-focus-ring)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--ms-bg)] disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-[var(--ms-btn-disabled-bg)] disabled:text-[var(--ms-btn-disabled-fg)] disabled:border-transparent border",
  {
    variants: {
      variant: {
        // All colours come from the --ms-btn-* design tokens in styles.css.
        // Every fg/bg pair (incl. hover + dark) is >= WCAG AA 4.5:1 — see
        // test/button.test.tsx. Never use raw bg-primary/text-white here:
        // in dark mode --ms-primary is a pale mint and white text fails AA.
        default: "bg-[var(--ms-btn-primary-bg)] text-[var(--ms-btn-primary-fg)] border-transparent hover:bg-[var(--ms-btn-primary-hover-bg)] hover:text-[var(--ms-btn-primary-hover-fg)]",
        secondary: "bg-[var(--ms-btn-secondary-bg)] text-[var(--ms-btn-secondary-fg)] border-transparent hover:bg-[var(--ms-btn-secondary-hover-bg)] hover:text-[var(--ms-btn-secondary-hover-fg)]",
        outline: "bg-[var(--ms-btn-outline-bg)] text-[var(--ms-btn-outline-fg)] border-[var(--ms-btn-outline-border)] hover:bg-[var(--ms-btn-outline-hover-bg)]",
        ghost: "bg-transparent text-[var(--ms-btn-ghost-fg)] border-transparent hover:bg-[var(--ms-btn-ghost-hover-bg)]",
        destructive: "bg-[var(--ms-btn-destructive-bg)] text-[var(--ms-btn-destructive-fg)] border-transparent hover:bg-[var(--ms-btn-destructive-hover-bg)] hover:text-[var(--ms-btn-destructive-hover-fg)]",
        tab: "bg-[var(--ms-btn-tab-bg)] text-[var(--ms-btn-tab-fg)] border-transparent hover:bg-[var(--ms-btn-tab-hover-bg)] aria-selected:bg-[var(--ms-btn-tab-selected-bg)] aria-selected:text-[var(--ms-btn-tab-selected-fg)] aria-selected:hover:bg-[var(--ms-btn-tab-selected-hover-bg)] aria-selected:hover:text-[var(--ms-btn-tab-selected-hover-fg)] aria-pressed:bg-[var(--ms-btn-tab-selected-bg)] aria-pressed:text-[var(--ms-btn-tab-selected-fg)]",
        link: "bg-transparent border-transparent text-[var(--ms-focus-ring)] underline-offset-4 hover:underline px-0",
      },
      size: {
        default: "h-10 px-4 text-sm",
        sm: "h-8 px-3 text-xs",
        lg: "h-12 px-6 text-base",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
  loadingText?: string;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, loading = false, loadingText, disabled, children, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        ref={ref}
        className={cn(buttonVariants({ variant, size }), className)}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        {...props}
      >
        {loading ? (
          <>
            <Spinner className="h-4 w-4" />
            <span>{loadingText ?? children}</span>
          </>
        ) : (
          children
        )}
      </Comp>
    );
  },
);
Button.displayName = "Button";

export { buttonVariants };
