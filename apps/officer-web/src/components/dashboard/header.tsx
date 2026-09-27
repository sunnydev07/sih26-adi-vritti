"use client";

import { useTheme } from "next-themes";
import { Bell, Moon, Search, Sun } from "lucide-react";
import * as React from "react";
import { Input } from "@/components/ui/primitives";
import { Button } from "@/components/ui/button";

export function Header({ title, subtitle }: { title: string; subtitle: string }) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = React.useState(false);
  React.useEffect(() => setMounted(true), []);

  return (
    <header className="sticky top-0 z-30 border-b border-[var(--border)] bg-white/75 backdrop-blur-xl dark:bg-slate-900/60">
      <div className="flex items-center gap-3 px-4 py-3 pl-14 lg:px-8 lg:pl-8">
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-display text-lg font-bold lg:text-xl">{title}</h1>
          <p className="truncate text-xs text-[var(--muted-foreground)] lg:text-sm">{subtitle}</p>
        </div>
        <div className="hidden w-64 items-center gap-2 md:flex">
          <div className="relative w-full">
            <Search size={15} aria-hidden className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)]" />
            <Input placeholder="Search USID, name…  (⌘K)" aria-label="Global search" className="pl-9" />
          </div>
        </div>
        <Button variant="ghost" size="icon" aria-label="Notifications, 4 unread">
          <span className="relative">
            <Bell size={18} />
            <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-[10px] font-bold text-white">
              4
            </span>
          </span>
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label={mounted && theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
          onClick={() => setTheme(mounted && theme === "dark" ? "light" : "dark")}
        >
          {mounted && theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </Button>
      </div>
    </header>
  );
}
