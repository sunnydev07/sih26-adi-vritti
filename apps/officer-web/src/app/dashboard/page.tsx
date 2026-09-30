"use client";

import { motion } from "framer-motion";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import * as React from "react";
import { DisbursementTrend, SchemeDonut } from "@/components/charts/charts";
import { SlaBadge } from "@/components/dashboard/bits";
import { GlassCard, NumberTicker } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api";
import { formatPaise } from "@/lib/utils";
import type { DashboardMetrics } from "@/types";

const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08 } },
};
const cardAnim = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4 } },
};

type Recent = Awaited<ReturnType<typeof api.getRecentExceptions>>;

/**
 * Overview answers one question: "what needs me today?"
 * Three hero cards above the fold; every chart lives behind a tap.
 */
export default function OverviewPage() {
  const [metrics, setMetrics] = React.useState<DashboardMetrics | null>(null);
  const [recent, setRecent] = React.useState<Recent>([]);

  React.useEffect(() => {
    api.getDashboardMetrics().then(setMetrics);
    api.getRecentExceptions().then(setRecent);
  }, []);

  if (!metrics) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Loading overview">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-32" />
        ))}
      </div>
    );
  }

  const gapPct = ((metrics.totalStudents - metrics.totalApplied) / metrics.totalStudents) * 100;
  const latest = metrics.disbursementTrend[metrics.disbursementTrend.length - 1];

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-4">
      {/* Hero: three decisions, plain words, one tap each. */}
      <motion.div variants={stagger} className="grid gap-4 md:grid-cols-3">
        <motion.div variants={cardAnim}>
          <Link
            id="hero-decide"
            href="/dashboard/exceptions"
            className="block rounded-2xl focus-visible:outline-2"
            aria-label={`${metrics.pendingReview} files need your decision. Open the queue.`}
          >
            <GlassCard className="h-full">
              <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
                Needs your decision
              </p>
              <p className="mt-1 text-4xl font-bold text-amber-600">
                <NumberTicker value={metrics.pendingReview} format={(n) => Math.round(n).toLocaleString("en-IN")} />
              </p>
              <p className="mt-1 text-xs text-[var(--muted-foreground)]">
                files waiting · {Math.round(metrics.stpRate)}% clear without touch
              </p>
              <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-[#4338CA]">
                Open queue <ArrowRight size={15} aria-hidden />
              </span>
            </GlassCard>
          </Link>
        </motion.div>

        <motion.div variants={cardAnim}>
          <Link
            id="hero-pay"
            href="/dashboard/disbursements"
            className="block rounded-2xl focus-visible:outline-2"
            aria-label={`Payment success ${metrics.paymentSuccessRate} percent. See failed payments with fixes.`}
          >
            <GlassCard className="h-full">
              <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Bank payments</p>
              <p className="mt-1 text-4xl font-bold text-emerald-600">
                <NumberTicker value={metrics.paymentSuccessRate} format={(n) => `${n.toFixed(1)}%`} />
              </p>
              <p className="mt-1 text-xs text-[var(--muted-foreground)]">
                paid in 30 days · every failure has a fix
              </p>
              <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-[#4338CA]">
                See failed with fixes <ArrowRight size={15} aria-hidden />
              </span>
            </GlassCard>
          </Link>
        </motion.div>

        <motion.div variants={cardAnim}>
          <Link
            id="hero-gap"
            href="/dashboard/coverage-gap"
            className="block rounded-2xl focus-visible:outline-2"
            aria-label={`${Math.round(gapPct)} percent of students missing. Open the coverage map.`}
          >
            <GlassCard className="h-full">
              <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Students missing</p>
              <p className="mt-1 text-4xl font-bold text-amber-600">
                <NumberTicker value={gapPct} format={(n) => `${n.toFixed(0)}%`} />
              </p>
              <p className="mt-1 text-xs text-[var(--muted-foreground)]">
                {metrics.totalApplied.toLocaleString("en-IN")} of {metrics.totalStudents.toLocaleString("en-IN")} enrolled applied
              </p>
              <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-[#4338CA]">
                Open map <ArrowRight size={15} aria-hidden />
              </span>
            </GlassCard>
          </Link>
        </motion.div>
      </motion.div>

      {/* Bridge to the queue: 5 most urgent files, nothing more. */}
      <GlassCard>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-display text-base font-bold">Most urgent files</h2>
          <Link href="/dashboard/exceptions" className="text-sm font-medium text-[#4338CA] hover:underline">
            Open queue →
          </Link>
        </div>
        <ul id="recent-list" className="space-y-2.5">
          {recent.slice(0, 5).map((r) => (
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

      {/* Analytics, one tap away — never competing with decisions. */}
      <details className="group rounded-2xl border border-[var(--border)] bg-[var(--card)]">
        <summary className="min-touch cursor-pointer list-none px-5 py-4 text-sm font-semibold [&::-webkit-details-marker]:hidden">
          <span className="mr-2 inline-block transition-transform group-open:rotate-90" aria-hidden>›</span>
          Scheme &amp; payment analytics
        </summary>
        <div className="grid gap-4 px-4 pb-4 lg:grid-cols-2">
          <GlassCard>
            <h2 className="mb-1 font-display text-base font-bold">Scheme split</h2>
            <p className="mb-2 text-xs text-[var(--muted-foreground)]">Applications across 5 MoTA schemes</p>
            <SchemeDonut data={metrics.schemeBreakdown} />
          </GlassCard>
          <GlassCard>
            <h2 className="mb-1 font-display text-base font-bold">Payment trend</h2>
            {latest ? (
              <p className="mb-2 font-mono text-xs text-[var(--muted-foreground)]">
                Latest: sanctioned {formatPaise(latest.sanctionedPaise)} · paid {formatPaise(latest.paidPaise)}
              </p>
            ) : null}
            <DisbursementTrend data={metrics.disbursementTrend} />
          </GlassCard>
        </div>
      </details>
    </motion.div>
  );
}
