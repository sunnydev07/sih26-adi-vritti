"use client";

import { motion } from "framer-motion";
import { ChevronRight, Download, MapPin } from "lucide-react";
import * as React from "react";
import { GlassCard, NumberTicker } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/components/ui/controls";
import { api } from "@/lib/api";
import { coverageColor, coveragePct } from "@/lib/coverage";
import type { CoverageRegion } from "@/types";

const LEVEL_LABEL: Record<CoverageRegion["level"], string> = {
  state: "States",
  district: "Districts",
  block: "Blocks",
  school: "Schools",
};

export default function CoverageGapPage() {
  const [trail, setTrail] = React.useState<CoverageRegion[]>([]);
  const [children, setChildren] = React.useState<CoverageRegion[]>([]);
  const [outreach, setOutreach] = React.useState<Awaited<ReturnType<typeof api.getOutreachList>>>([]);
  const [loading, setLoading] = React.useState(true);
  const [failed, setFailed] = React.useState(false);

  const parentId = trail.length === 0 ? null : trail[trail.length - 1].id;

  const load = React.useCallback(() => {
    // No synchronous setState in this path (see overview page): the loading
    // flag is raised by the navigation handlers below, which are events.
    Promise.all([api.getCoverageChildren(parentId), api.getOutreachList()]).then(
      ([c, o]) => {
        setChildren(c);
        setOutreach(o);
        setLoading(false);
      },
      () => {
        setLoading(false);
        setFailed(true);
      }
    );
  }, [parentId]);

  React.useEffect(() => {
    load();
  }, [load]);

  function navigate(next: CoverageRegion[]) {
    // Event handler, so raising the flags here is lint-clean — and it keeps
    // the skeleton honest when drilling between cached levels too.
    setLoading(true);
    setFailed(false);
    setTrail(next);
  }

  const level: CoverageRegion["level"] =
    children[0]?.level ?? (trail.length === 0 ? "state" : "school");

  function drill(region: CoverageRegion) {
    if (region.level === "school") {
      toast(`Demo: ${region.name} outreach drafted locally — nothing sent.`);
      return;
    }
    navigate([...trail, region]);
  }

  function exportCsv() {
    // The rows are synthetic (demo console): mark the file itself, so a
    // forwarded CSV cannot be mistaken for an official extract.
    const rows = ["# Adi-Vritti coverage-gap outreach — DEMO DATA, synthetic figures, not official", "school,district,class,st_students,applications", ...outreach.map((o) => `"${o.schoolName}","${o.district}","${o.classLevel}",${o.stStudents},${o.applications}`)];
    const blob = new Blob([rows.join("\n")], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "coverage-gap-outreach.csv";
    a.click();
    URL.revokeObjectURL(url);
    toast("Demo outreach list exported — synthetic figures, not official.");
  }

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <div className="space-y-4 lg:col-span-2">
        <GlassCard>
          <nav aria-label="Breadcrumb" className="mb-3 flex flex-wrap items-center gap-1 text-sm">
            <button onClick={() => navigate([])} className="font-medium text-[#4338CA] hover:underline">
              India
            </button>
            {trail.map((t, i) => (
              <span key={t.id} className="flex items-center gap-1">
                <ChevronRight size={14} aria-hidden className="text-[var(--muted-foreground)]" />
                <button
                  onClick={() => navigate(trail.slice(0, i + 1))}
                  className="font-medium text-[#4338CA] hover:underline"
                >
                  {t.name}
                </button>
              </span>
            ))}
          </nav>
          <h2 className="font-display text-base font-bold">{LEVEL_LABEL[level]} · tap to drill down</h2>
          {loading ? (
            <p className="py-8 text-center text-sm text-[var(--muted-foreground)]">Loading regions…</p>
          ) : failed ? (
            <div className="py-6 text-center">
              <p className="text-sm font-semibold">Coverage map did not load.</p>
              <p className="mt-1 text-sm text-[var(--muted-foreground)]">
                No regional figures are shown rather than partial ones.
              </p>
              <button
                onClick={() => navigate(trail)}
                className="min-touch mt-3 rounded-full border border-[var(--border)] px-4 text-sm font-semibold"
              >
                Retry
              </button>
            </div>
          ) : (
            <div id="coverage-regions" className="mt-3 grid scroll-mt-24 gap-3 sm:grid-cols-2">
              {children.map((r, i) => {
                const pct = coveragePct(r);
                return (
                  <motion.button
                    key={r.id}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.05 }}
                    onClick={() => drill(r)}
                    className="rounded-2xl border border-[var(--border)] p-4 text-left transition-transform hover:scale-[1.01]"
                    aria-label={`${r.name}, coverage ${pct} percent`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{r.name}</span>
                      <Badge variant={pct < 20 ? "failed" : pct < 60 ? "pending" : "verified"}>{pct}%</Badge>
                    </div>
                    <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                      <motion.div
                        className="h-full rounded-full"
                        style={{ background: coverageColor(pct) }}
                        initial={{ width: 0 }}
                        animate={{ width: `${pct}%` }}
                        transition={{ duration: 0.7, delay: i * 0.05 }}
                      />
                    </div>
                    <p className="mt-1.5 font-mono text-xs text-[var(--muted-foreground)]">
                      {r.applications.toLocaleString("en-IN")} / {r.totalStudents.toLocaleString("en-IN")} applied
                      {r.pvtg ? " · PVTG" : ""}
                    </p>
                  </motion.button>
                );
              })}
              {children.length === 0 ? (
                <p className="py-8 text-center text-sm text-[var(--muted-foreground)] sm:col-span-2">
                  Deepest level reached — use the outreach list to act.
                </p>
              ) : null}
            </div>
          )}
          <div className="mt-4 flex items-center gap-2 text-xs text-[var(--muted-foreground)]">
            <span className="h-2.5 w-16 rounded-full bg-gradient-to-r from-rose-500 via-amber-500 to-emerald-500" aria-hidden />
            &lt;20% critical · 20–60% watch · &gt;60% healthy
          </div>
        </GlassCard>
      </div>

      <div className="space-y-4">
        <GlassCard>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-display text-base font-bold">School outreach</h2>
            <button
              onClick={exportCsv}
              className="flex items-center gap-1 rounded-full border border-[var(--border)] px-3 py-1.5 text-xs font-medium hover:bg-[var(--muted)]"
            >
              <Download size={13} aria-hidden /> CSV
            </button>
          </div>
          <div id="outreach-list" className="scroll-mt-24 space-y-2.5">
            {outreach.map((o) => (
              <div key={o.id} className="rounded-xl border border-[var(--border)] p-3 text-sm">
                <p className="flex items-start gap-1.5 font-semibold">
                  <MapPin size={14} aria-hidden className="mt-0.5 shrink-0 text-[#4338CA]" />
                  {o.schoolName}: {o.stStudents} ST students {o.classLevel}, only {o.applications} applications
                </p>
                <p className="mt-1 font-mono text-xs text-[var(--muted-foreground)]">{o.contact}</p>
                <button
                  onClick={() => toast(`Demo: outreach staged for ${o.schoolName} — nothing sent.`)}
                  className="mt-2 rounded-full bg-[#312E81] px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-[#4338CA]"
                >
                  Send Outreach
                </button>
              </div>
            ))}
          </div>
        </GlassCard>
        <GlassCard className="flex items-center gap-4">
          <div>
            <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Students missing</p>
            <p className="text-2xl font-bold text-amber-600">
              <NumberTicker value={1090000} format={(n) => Math.round(n).toLocaleString("en-IN")} />
            </p>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
