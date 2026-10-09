"use client";

import { useTheme } from "next-themes";
import { useRouter } from "next/navigation";
import { Bell, LogOut, Moon, Sun } from "lucide-react";
import * as React from "react";
import { Button } from "@/components/ui/button";

export function Header({ title, subtitle }: { title: string; subtitle: string }) {
  const { theme, setTheme } = useTheme();
  const router = useRouter();
  const [mounted, setMounted] = React.useState(false);
  const [signingOut, setSigningOut] = React.useState(false);
  // Deferred past the effect body: a synchronous setMounted here trips
  // react-hooks/set-state-in-effect with a cascading-render warning.
  React.useEffect(() => {
    const raf = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  // The session cookie is httpOnly, so sign-out must go through the server
  // route that clears it; navigating away alone would leave it usable.
  // Replace even if the call fails: an expired-or-gone cookie needs no server.
  async function signOut() {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await fetch("/api/session", { method: "DELETE", cache: "no-store" });
    } catch {
      // Offline or already signed out — still leave the console.
    } finally {
      router.replace("/login");
    }
  }

  return (
    <header className="sticky top-0 z-30 border-b border-[var(--border)] bg-white/75 backdrop-blur-xl dark:bg-slate-900/60">
      <div className="flex items-center gap-3 px-4 py-3 pl-14 lg:px-8 lg:pl-8">
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-display text-lg font-bold lg:text-xl">{title}</h1>
          <p className="truncate text-xs text-[var(--muted-foreground)] lg:text-sm">{subtitle}</p>
        </div>
        {/* No global search box: the previous Input implied console-wide search
            but was uncontrolled and did nothing — typing a USID and hitting
            Enter read as "file does not exist". Search lives in the exception
            queue filter until a real cross-console search ships. */}
        <Button variant="ghost" size="icon" aria-label="Notifications">
          <span className="relative">
            <Bell size={18} />
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
        <Button
          variant="ghost"
          size="sm"
          aria-label="Sign out"
          disabled={signingOut}
          onClick={signOut}
        >
          <LogOut size={16} aria-hidden />
          <span className="hidden lg:inline">Sign out</span>
        </Button>
      </div>
    </header>
  );
}
