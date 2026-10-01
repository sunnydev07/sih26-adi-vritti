"use client";

import { motion } from "framer-motion";
import dynamic from "next/dynamic";
import Link from "next/link";
import * as React from "react";
import { GlassCard, NumberTicker } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api";
import { plainFailure } from "@/lib/plain";
import { formatPaise, formatPaiseCompact } from "@/lib/utils";

const FailureChart = dynamic(
  () => import("@/components/charts/failure-chart").then((m) => m.FailureChart),
  {
    ssr: false,
    loading: () => <Skeleton className="h-64" />,
  }
);

/**
 * Money answers two questions: "how much reached students?" and
 * "what is stuck, with what fix?" Everything else is one tap away.
 */
export default function DisbursementsPage() {
  const [data, setData] = React.useState<Awaited<ReturnType<typeof api.getDisbursements>> | null>(null);
  const [filter, setFilter] = React.useState("All");
  const [failed, setFailed] = React.useState(false);

  const load = React.useCallback(() => {
    api.getDisbursements().then(setData, () => setFailed(true));
  }, []);

  React.useEffect(() => {
    load();
  }, [load]);

  function retry() {
    setFailed(false);
    setData(null);
    load();
  }

  if (failed) {
    return (
      <GlassCard>
        <p className="text-sm font-semibold">Payments did not load.</p>
        <p className="mt-1 text-sm text-[var(--muted-foreground)]">
          Money figures are withheld rather than guessed — retry to load them.
        </p>
        <button
          onClick={retry}
          className="min-touch mt-3 rounded-full border border-[var(--border)] px-4 text-sm font-semibold"
        >
          Retry
        </button>
      </GlassCard>
    );
  }

  if (!data) {
    return (
      <div className="grid gap-4 sm:grid-cols-2" aria-label="Loading disbursements">
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
      </div>
    );
  }

  const rows = data.records.filter((r) => filter === "All" || r.status === filter.toLowerCase());

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <GlassCard>
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Reached students</p>
          <p id="money-paid" className="mt-1 text-3xl font-bold text-emerald-600">
            <NumberTicker value={data.summary.paidPaise / 100} format={(n) => `₹${Math.round(n).toLocaleString("en-IN")}`} />
          </p>
          <p className="mt-1 text-xs text-[var(--muted-foreground)]">
            of {formatPaise(data.summary.sanctionedPaise)} sanctioned · {formatPaise(data.summary.pendingPaise)} on the way
          </p>
        </GlassCard>
        <GlassCard className="pulse-border">
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">
            Stuck · {data.summary.failedCount} need a fix
          </p>
          <p id="money-fix" className="mt-1 text-3xl font-bold text-rose-600">{formatPaise(data.summary.failedPaise)}</p>
          <button
            onClick={() => setFilter("Failed")}
            className="mt-1 text-sm font-medium text-[#4338CA] hover:underline"
          >
            Show only stuck payments →
          </button>
        </GlassCard>
      </div>

      <details className="group rounded-2xl border border-[var(--border)] bg-[var(--card)]">
        <summary className="min-touch cursor-pointer list-none px-5 py-4 text-sm font-semibold [&::-webkit-details-marker]:hidden">
          <span className="mr-2 inline-block transition-transform group-open:rotate-90" aria-hidden>›</span>
          Why payments fail (chart)
        </summary>
        <div className="px-4 pb-4">
          <p className="mb-2 text-xs text-[var(--muted-foreground)]">Tap a bar to filter the list below</p>
          <FailureChart data={data.breakdown} onSelect={() => setFilter("Failed")} />
        </div>
      </details>

      <GlassCard className="p-2">
        <div className="flex flex-wrap gap-2 px-3 py-2">
          {["All", "Paid", "Pending", "Failed"].map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              aria-pressed={filter === f}
              className={`min-touch rounded-full px-3.5 text-xs font-semibold ${filter === f ? "bg-[var(--primary)] text-white" : "border border-[var(--border)]"}`}
            >
              {f}
            </button>
          ))}
        </div>
        {/* Mobile cards first; the full table stays on desktop. */}
        <ul className="space-y-2 px-2 pb-2 lg:hidden" aria-label="Disbursements">
          {rows.map((r) => {
            const fix = r.fix ?? plainFailure(r.failureCode).fix;
            return (
              <li key={r.id} className="rounded-xl border border-[var(--border)] p-3 text-sm">
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate font-semibold">{r.studentName}</span>
                  <Badge variant={r.status === "paid" ? "verified" : r.status === "pending" ? "pending" : "failed"}>
                    {r.status === "paid" ? "Paid" : r.status === "pending" ? "On the way" : "Stuck"}
                  </Badge>
                </span>
                <span className="mt-0.5 block font-mono text-xs text-[var(--muted-foreground)]">
                  {r.scheme} · {formatPaiseCompact(r.amountPaise)} · {r.district}
                </span>
                {r.status === "failed" ? (
                  <span className="mt-1.5 block text-xs">
                    <span className="block text-[var(--muted-foreground)]">
                      {r.failureReason ?? plainFailure(r.failureCode).cause}
                    </span>
                    <span className="block font-medium text-[#4338CA]">Fix: {fix}</span>
                  </span>
                ) : null}
              </li>
            );
          })}
          {rows.length === 0 ? (
            <li className="p-6 text-center text-sm text-[var(--muted-foreground)]">Nothing in this view.</li>
          ) : null}
        </ul>
        <div className="hidden overflow-x-auto lg:block">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead>
              <tr className="text-xs uppercase tracking-wider text-[var(--muted-foreground)]">
                <th className="px-3 py-2">Student</th>
                <th className="px-3 py-2">Scheme</th>
                <th className="px-3 py-2">Amount</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Reason / Fix</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <motion.tr
                  key={r.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: Math.min(i, 8) * 0.04 }}
                  className="border-t border-[var(--border)]"
                >
                  <td className="px-3 py-2.5">
                    <span className="block font-semibold">{r.studentName}</span>
                    <span className="font-mono text-xs text-[var(--muted-foreground)]">{r.id} · {r.district}</span>
                  </td>
                  <td className="px-3 py-2.5"><Badge variant="outline">{r.scheme}</Badge></td>
                  <td className="px-3 py-2.5 font-mono">{formatPaiseCompact(r.amountPaise)}</td>
                  <td className="px-3 py-2.5">
                    <Badge variant={r.status === "paid" ? "verified" : r.status === "pending" ? "pending" : "failed"}>{r.status}</Badge>
                    {r.failureCode ? <span className="mt-1 block font-mono text-[11px] text-[var(--muted-foreground)]">{r.failureCode}</span> : null}
                  </td>
                  <td className="px-3 py-2.5 text-xs">
                    {r.failureReason ? <p>{r.failureReason}</p> : <p className="text-[var(--muted-foreground)]">On track</p>}
                    {r.fix ? <p className="mt-1 font-medium text-[#4338CA]">Fix: {r.fix}</p> : null}
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="px-3 pb-2 text-xs text-[var(--muted-foreground)]">
          <Link href="/dashboard/exceptions" className="font-medium text-[#4338CA] hover:underline">
            Files waiting on officers live in the queue →
          </Link>
        </p>
      </GlassCard>
    </div>
  );
}
