"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

export function Tabs({
  tabs,
  value,
  onChange,
}: {
  tabs: { id: string; label: string }[];
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <div role="tablist" aria-label="Tabs" className="flex gap-1 rounded-full border border-[var(--border)] p-1">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={value === t.id}
          onClick={() => onChange(t.id)}
          className={cn(
            "rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
            value === t.id ? "bg-[var(--primary)] text-white shadow" : "text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function Switch({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn(
        "relative h-6 w-11 rounded-full transition-colors",
        checked ? "bg-[var(--primary)]" : "bg-slate-300 dark:bg-slate-700"
      )}
    >
      <span
        className={cn(
          "absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all",
          checked ? "left-[22px]" : "left-0.5"
        )}
      />
    </button>
  );
}

export function Toaster() {
  const [items, setItems] = React.useState<{ id: number; text: string }[]>([]);
  React.useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<string>).detail;
      const id = Date.now();
      setItems((prev) => [...prev, { id, text: detail }]);
      setTimeout(() => setItems((prev) => prev.filter((i) => i.id !== id)), 3200);
    };
    window.addEventListener("adi-toast", handler);
    return () => window.removeEventListener("adi-toast", handler);
  }, []);
  return (
    <div aria-live="polite" className="fixed bottom-4 right-4 z-[70] flex flex-col gap-2">
      {items.map((i) => (
        <div key={i.id} className="glass-card px-4 py-2.5 text-sm font-medium">
          {i.text}
        </div>
      ))}
    </div>
  );
}

export function toast(text: string): void {
  window.dispatchEvent(new CustomEvent<string>("adi-toast", { detail: text }));
}
