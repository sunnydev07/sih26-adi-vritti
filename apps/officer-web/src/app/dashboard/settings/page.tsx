"use client";

import * as React from "react";
import { GlassCard } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { Switch, toast } from "@/components/ui/controls";
import { Separator } from "@/components/ui/primitives";

export default function SettingsPage() {
  const [dense, setDense] = React.useState(false);
  const [slaAlerts, setSlaAlerts] = React.useState(true);

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
        </div>
        <Separator className="my-4" />
        <p className="text-xs text-[var(--muted-foreground)]">
          Money is stored in integer paise and rendered en-IN. Dates are ISO-8601 with explicit timezone. Aadhaar is never shown in full.
        </p>
      </GlassCard>
      <GlassCard>
        <h2 className="font-display text-base font-bold">Integration readiness</h2>
        <ul className="mt-3 space-y-2 text-sm">
          <li className="flex items-center justify-between">DigiLocker (API Setu sandbox) <Badge variant="verified">live</Badge></li>
          <li className="flex items-center justify-between">Bhashini ASR / NMT / TTS <Badge variant="verified">live</Badge></li>
          <li className="flex items-center justify-between">NSP / SFMP / NOS adapters <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">PFMS / DBT failure taxonomy <Badge variant="pending">contract-ready</Badge></li>
          <li className="flex items-center justify-between">UDISE+ / APAAR hashed join <Badge variant="review">pilot</Badge></li>
        </ul>
      </GlassCard>
    </div>
  );
}
