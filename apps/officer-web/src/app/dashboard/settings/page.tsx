"use client";

import { useTheme } from "next-themes";
import Link from "next/link";
import * as React from "react";
import { GlassCard } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { Switch, toast } from "@/components/ui/controls";
import { Separator } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";

export default function SettingsPage() {
  const [dense, setDense] = React.useState(false);
  const [slaAlerts, setSlaAlerts] = React.useState(true);
  const { theme, setTheme } = useTheme();

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <GlassCard>
        <h2 className="font-display text-base font-bold">Console preferences</h2>
        <div className="mt-4 space-y-4 text-sm">
          <div className="flex items-center justify-between gap-3">
            <span>SLA breach alerts</span>
            <Switch checked={slaAlerts} onChange={(v) => { setSlaAlerts(v); toast(v ? "SLA alerts on" : "SLA alerts off"); }} label="SLA breach alerts" />
          </div>
          <div className="flex items-center justify-between gap-3">
            <span>Dense tables</span>
            <Switch checked={dense} onChange={setDense} label="Dense tables" />
          </div>
          <div className="flex items-center justify-between gap-3">
            <span id="theme-label">Appearance</span>
            <div className="flex gap-1" role="group" aria-labelledby="theme-label">
              {(["light", "system", "dark"] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setTheme(t)}
                  aria-pressed={theme === t}
                  className={cn(
                    "min-touch rounded-full px-3 text-xs font-semibold capitalize",
                    theme === t ? "bg-[var(--primary)] text-white" : "border border-[var(--border)]"
                  )}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
        </div>
        <Separator className="my-4" />
        <p className="text-xs text-[var(--muted-foreground)]">
          Money is stored in integer paise and rendered en-IN. Dates are ISO-8601 with explicit timezone. Aadhaar is never shown in full.
        </p>
      </GlassCard>
      <GlassCard className="lg:col-span-2">
        <h2 className="font-display text-base font-bold">Guided demo tour</h2>
        <p className="mt-1 text-sm text-[var(--muted-foreground)]">
          3 beats in 5 minutes: the coverage gap, one-tap approvals, and a fixed payment — built for judges and new officers.
        </p>
        <Link
          href="/dashboard/coverage-gap?demo=1"
          className="min-touch mt-3 inline-flex items-center rounded-full bg-[var(--primary)] px-5 text-sm font-semibold text-white"
        >
          Start the tour →
        </Link>
      </GlassCard>
      <GlassCard>
        <h2 className="font-display text-base font-bold">Integration readiness</h2>
        {/* No "live" row exists: every government system behind this console is
            govsim. The README and demo-path say contract-ready; this table must
            not say otherwise. */}
        <ul className="mt-3 space-y-2 text-sm">
          <li className="flex items-center justify-between">DigiLocker (govsim proxy) <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">Bhashini ASR / NMT / TTS <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">NSP / SFMP / NOS adapters <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">PFMS / DBT failure taxonomy <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">UDISE+ / APAAR hashed join <Badge variant="review">pilot</Badge></li>
        </ul>
      </GlassCard>
    </div>
  );
}
