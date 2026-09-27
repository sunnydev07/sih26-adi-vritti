"use client";

import { motion } from "framer-motion";
import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import Link from "next/link";
import * as React from "react";
import { DisbursementTrend, SchemeDonut } from "@/components/charts/charts";
import { SlaBadge } from "@/components/dashboard/bits";
import { GlassCard, NumberTicker, ProgressRing } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api";
import { formatPaise, formatPaiseCompact } from "@/lib/utils";
import type { DashboardMetrics } from "@/types";

const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.1 } },
};
const cardAnim = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45 } },
};

type Recent = Awaited<ReturnType<typeof api.getRecentExceptions>>;

export default function OverviewPage() {
  const [metrics, setMetrics] = React.useState<DashboardMetrics | null>(null);
  const [recent, setRecent] = React.useState<Recent>([]);

  React.useEffect(() => {
    api.getDashboardMetrics().then(setMetrics);
    api.getRecentExceptions().then(setRecent);
  }, []);

  if (!metrics) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Loading overview">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-32" />
        ))}
      </div>
    );
  }

  const pendingTone = metrics.pendingReview > 200 ? "text-rose-600" : metrics.pendingReview > 50 ? "text-amber-600" : "text-emerald-600";
  const latest = metrics.disbursementTrend[metrics.disbursementTrend.length - 1];

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={stagger} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <motion.div variants={cardAnim}>
          <GlassCard>
            <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Total Applications</p>
            <p className="mt-1 text-3xl font-bold">
              <NumberTicker value={metrics.totalApplications} format={(n) => Math.round(n).toLocaleString("en-IN")} />
            </p>
            <p className="mt-1 flex items-center gap-1 text-xs text-emerald-600">
              <ArrowUpRight size={13} aria-hidden /> 6.2% vs last month
            </p>
          </GlassCard>
        </motion.div>
        <motion.div variants={cardAnim}>
          <GlassCard className="flex items-center gap-4">
            <ProgressRing pct={metrics.stpRate} label="STP rate" />
            <div>
              <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Straight-Through</p>
              <p className="text-sm text-[var(--muted-foreground)]">auto-approved without touch</p>
            </div>
          </GlassCard>
        </motion.div>
        <motion.div variants={cardAnim}>
          <GlassCard>
            <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Pending Review</p>
            <p className={`mt-1 text-3xl font-bold ${pendingTone}`}>
              <NumberTicker value={metrics.pendingReview} format={(n) => Math.round(n).toLocaleString("en-IN")} />
            </p>
            <p className="mt-1 text-xs text-[var(--muted-foreground)]">sorted by breach risk in queue</p>
          </GlassCard>
        </motion.div>
        <motion.div variants={cardAnim}>
          <GlassCard>
            <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Payment Success · 30d</p>
            <p className="mt-1 text-3xl font-bold text-emerald-600">
              <NumberTicker value={metrics.paymentSuccessRate} format={(n) => `${n.toFixed(1)}%`} />
            </p>
            <p className="mt-1 flex items-center gap-1 text-xs text-emerald-600">
              <ArrowUpRight size={13} aria-hidden /> DBT mapper seeding improved
            </p>
          </GlassCard>
        </motion.div>
      </motion.div>

      <div className="grid gap-4 lg:grid-cols-2">
        <GlassCard>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-display text-base font-bold">Recent Exceptions</h2>
            <Link href="/dashboard/exceptions" className="text-sm font-medium text-[#4338CA] hover:underline">
              Open queue →
            </Link>
          </div>
          <ul className="space-y-2.5">
            {recent.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-2 rounded-xl border border-[var(--border)] px-3 py-2 text-sm">
                <span className="font-semibold">{r.studentName}</span>
                <Badge variant="outline">{r.scheme}</Badge>
                <span className="ml-auto">
                  <SlaBadge elapsed={r.slaElapsedDays} limit={r.slaLimitDays} />
                </span>
              </li>
            ))}
          </ul>
        </GlassCard>
        <GlassCard>
          <h2 className="mb-1 font-display text-base font-bold">Scheme Distribution</h2>
          <p className="mb-2 text-xs text-[var(--muted-foreground)]">Applications across 5 MoTA schemes</p>
          <SchemeDonut data={metrics.schemeBreakdown} />
        </GlassCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <GlassCard className="lg:col-span-2">
          <h2 className="font-display text-base font-bold">Coverage Gap</h2>
          <p className="text-xs text-[var(--muted-foreground)]">
            {metrics.totalApplied.toLocaleString("en-IN")} applied of {metrics.totalStudents.toLocaleString("en-IN")} enrolled
          </p>
          <div className="mt-3 flex items-end gap-2">
            <p className="text-3xl font-bold text-amber-600">
              <NumberTicker value={(metrics.totalStudents - metrics.totalApplied) / metrics.totalStudents * 100} format={(n) => `${n.toFixed(0)}%`} />
            </p>
            <p className="pb-1 text-xs text-[var(--muted-foreground)]">missing — tap to drill to school</p>
          </div>
          <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
            <motion.div
              className="h-full rounded-full bg-gradient-to-r from-rose-500 via-amber-500 to-emerald-500"
              initial={{ width: 0 }}
              animate={{ width: `${(metrics.totalApplied / metrics.totalStudents) * 100}%` }}
              transition={{ duration: 1 }}
            />
          </div>
          <Link href="/dashboard/coverage-gap" className="mt-3 inline-block text-sm font-medium text-[#4338CA] hover:underline">
            Open full map →
          </Link>
        </GlassCard>
        <GlassCard className="lg:col-span-3">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="font-display text-base font-bold">Disbursement Trend</h2>
            <span className="flex items-center gap-1 text-xs text-[var(--muted-foreground)]">
              <ArrowDownRight size={13} aria-hidden /> amounts in {formatPaiseCompact(100000)}
            </span>
          </div>
          {latest ? (
            <p className="mb-2 font-mono text-xs text-[var(--muted-foreground)]">
              Latest: sanctioned {formatPaise(latest.sanctionedPaise)} · paid {formatPaise(latest.paidPaise)}
            </p>
          ) : null}
          <DisbursementTrend data={metrics.disbursementTrend} />
        </GlassCard>
      </div>
    </motion.div>
  );
}
