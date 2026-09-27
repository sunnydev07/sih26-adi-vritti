import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-full text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)] disabled:opacity-50 disabled:pointer-events-none",
  {
    variants: {
      variant: {
        default: "bg-[var(--primary)] text-white hover:bg-[#4338CA] shadow",
        secondary: "bg-[var(--accent)] text-[var(--accent-foreground)] hover:bg-[#D97706]",
        outline: "border border-[var(--border)] bg-transparent hover:bg-[var(--muted)]",
        ghost: "hover:bg-[var(--muted)]",
        glass: "bg-white/10 backdrop-blur border border-white/20 text-white hover:bg-white/20",
      },
      size: { default: "h-9 px-5 py-2", sm: "h-8 px-3 text-xs", lg: "h-11 px-8 text-base", icon: "h-9 w-9" },
    },
    defaultVariants: { variant: "default", size: "default" },
  }
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {}
export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
