"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ClipboardList, Home, Map, Users, Wallet } from "lucide-react";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/dashboard", label: "Home", icon: Home },
  { href: "/dashboard/exceptions", label: "Queue", icon: ClipboardList },
  { href: "/dashboard/disbursements", label: "Money", icon: Wallet },
  { href: "/dashboard/coverage-gap", label: "Gap", icon: Map },
  { href: "/dashboard/identity", label: "Identity", icon: Users },
];

/** Thumb-reachable primary nav on phones. Desktop keeps the sidebar. */
export function BottomNav() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--border)] bg-white/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl dark:bg-slate-900/85 lg:hidden"
    >
      <ul className="grid grid-cols-5">
        {NAV.map((item) => {
          const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href));
          const Icon = item.icon;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "min-touch flex flex-col items-center justify-center gap-0.5 py-1.5 text-[11px] font-semibold",
                  active ? "text-[var(--primary)]" : "text-[var(--muted-foreground)]"
                )}
              >
                <Icon size={20} aria-hidden />
                {item.label}
                <span
                  aria-hidden
                  className={cn("h-1 w-8 rounded-full", active ? "bg-gradient-to-r from-[#6366F1] to-[#F59E0B]" : "bg-transparent")}
                />
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
