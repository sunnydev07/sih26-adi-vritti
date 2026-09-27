import * as React from "react";
import { cn } from "@/lib/utils";

export function Input({ className, ...props }: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        "h-10 w-full rounded-xl border border-[var(--border)] bg-transparent px-3 text-sm outline-none",
        "placeholder:text-[var(--muted-foreground)] focus:ring-2 focus:ring-[#6366f1]/40",
        className
      )}
      {...props}
    />
  );
}

export function Separator({ className }: { className?: string }) {
  return <div role="separator" className={cn("h-px w-full bg-[var(--border)]", className)} />;
}

export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials = name
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
  return (
    <div
      aria-label={name}
      className={cn(
        "flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#312E81] to-[#F59E0B] text-xs font-bold text-white",
        className
      )}
    >
      {initials}
    </div>
  );
}
