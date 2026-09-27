"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import {
  Bell,
  ClipboardList,
  Home,
  Map,
  Menu,
  Settings,
  Users,
  Wallet,
  X,
} from "lucide-react";
import * as React from "react";
import { cn } from "@/lib/utils";
import { Avatar } from "@/components/ui/primitives";

const NAV = [
  { href: "/dashboard", label: "Overview", icon: Home },
  { href: "/dashboard/exceptions", label: "Exception Queue", icon: ClipboardList },
  { href: "/dashboard/coverage-gap", label: "Coverage Gap", icon: Map },
  { href: "/dashboard/identity", label: "Identity Resolution", icon: Users },
  { href: "/dashboard/disbursements", label: "Disbursements", icon: Wallet },
  { href: "/dashboard/settings", label: "Settings", icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();
  const [open, setOpen] = React.useState(false);

  const body = (
    <div className="flex h-full flex-col">
      <Link href="/dashboard" className="px-5 pb-2 pt-6" onClick={() => setOpen(false)}>
        <span className="gradient-text font-display text-xl font-bold">Adi-Vritti</span>
        <p className="mt-0.5 text-[11px] uppercase tracking-widest text-[var(--muted-foreground)]">
          Officer Console
        </p>
      </Link>
      <nav aria-label="Dashboard" className="flex-1 space-y-1 overflow-y-auto p-3">
        {NAV.map((item) => {
          const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href));
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setOpen(false)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
                active ? "bg-[var(--primary)]/10 text-[var(--foreground)]" : "text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              )}
            >
              {active ? (
                <motion.span
                  layoutId="nav-active"
                  className="absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-full bg-gradient-to-b from-[#6366F1] to-[#F59E0B]"
                />
              ) : null}
              <Icon size={18} aria-hidden />
              {item.label}
              {item.label === "Exception Queue" ? (
                <span className="ml-auto flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800 dark:bg-amber-500/15 dark:text-amber-300">
                  <Bell size={11} aria-hidden /> 187
                </span>
              ) : null}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-[var(--border)] p-4">
        <div className="flex items-center gap-3">
          <Avatar name="District Officer" />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">R. Ekka</p>
            <p className="text-xs text-[var(--muted-foreground)]">District Nodal · Mandla</p>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <>
      <button
        className="fixed left-4 top-4 z-40 rounded-full border border-[var(--border)] bg-[var(--card)] p-2 shadow lg:hidden"
        onClick={() => setOpen(!open)}
        aria-label={open ? "Close navigation" : "Open navigation"}
      >
        {open ? <X size={18} /> : <Menu size={18} />}
      </button>
      <aside className="sticky top-0 hidden h-screen w-72 shrink-0 border-r border-[var(--border)] bg-white/75 backdrop-blur-xl dark:bg-slate-900/60 lg:block">
        {body}
      </aside>
      {open ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div aria-hidden className="absolute inset-0 bg-slate-950/40" onClick={() => setOpen(false)} />
          <aside className="absolute left-0 top-0 h-full w-72 border-r border-[var(--border)] bg-white/90 backdrop-blur-xl dark:bg-slate-900/90">
            {body}
          </aside>
        </div>
      ) : null}
    </>
  );
}
