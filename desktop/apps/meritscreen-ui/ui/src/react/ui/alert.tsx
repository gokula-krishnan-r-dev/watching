import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../lib/utils";

const alertVariants = cva("relative w-full rounded-md border px-4 py-3 text-sm", {
  variants: {
    variant: {
      default: "bg-muted text-foreground border-border",
      destructive: "bg-destructive-container text-on-destructive-container border-destructive/40",
      success: "bg-success-container text-foreground border-success/40",
    },
  },
  defaultVariants: { variant: "default" },
});

export function Alert({
  className,
  variant,
  title,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & VariantProps<typeof alertVariants> & { title?: string }) {
  return (
    <div role={variant === "destructive" ? "alert" : "status"} className={cn(alertVariants({ variant }), className)} {...props}>
      {title && <p className="m-0 mb-0.5 font-semibold">{title}</p>}
      <div className="leading-snug">{children}</div>
    </div>
  );
}
