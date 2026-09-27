"use client";

import { motion } from "framer-motion";
import * as React from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { GlassCard, NumberTicker } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import { formatPaise, formatPaiseCompact } from "@/lib/utils";

const SEV_COLOR: Record<string, string> = { high: "#f43f5e", medium: "#f59e0b", low: "#6366f1" };

export default function DisbursementsPage() {
  const [data, setData] = React.useState<Awaited<ReturnType<typeof api.getDisbursements>> | null>(null);
  const [filter, setFilter] = React.useState("All");

  React.useEffect(() => {
    api.getDisbursements().then(setData);
  }, []);

  if (!data) return <p className="py-10 text-center text-sm text-[var(--muted-foreground)]">Loading disbursements…</p>;

  const rows = data.records.filter((r) => filter === "All" || r.status === filter.toLowerCase());

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <GlassCard>
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Total Sanctioned</p>
          <p className="mt-1 text-2xl font-bold"><NumberTicker value={data.summary.sanctionedPaise / 100} format={(n) => `₹${Math.round(n).toLocaleString("en-IN")}`} /></p>
        </GlassCard>
        <GlassCard>
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Successfully Paid</p>
          <p className="mt-1 text-2xl font-bold text-emerald-600"><NumberTicker value={data.summary.paidPaise / 100} format={(n) => `₹${Math.round(n).toLocaleString("en-IN")}`} /></p>
        </GlassCard>
        <GlassCard>
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Pending</p>
          <p className="mt-1 text-2xl font-bold text-amber-600">{formatPaise(data.summary.pendingPaise)}</p>
        </GlassCard>
        <GlassCard className="pulse-border">
          <p className="text-xs uppercase tracking-widest text-[var(--muted-foreground)]">Failed · {data.summary.failedCount} cases</p>
          <p className="mt-1 text-2xl font-bold text-rose-600">{formatPaise(data.summary.failedPaise)}</p>
        </GlassCard>
      </div>

      <GlassCard>
        <h2 className="mb-1 font-display text-base font-bold">Failure Breakdown</h2>
        <p className="mb-2 text-xs text-[var(--muted-foreground)]">PFMS rejection-code taxonomy — click a bar to filter the table</p>
        <div className="h-64 w-full" role="img" aria-label="Failure breakdown bar chart">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data.breakdown} layout="vertical" margin={{ left: 20, right: 20 }}>
              <CartesianGrid strokeDasharray="3 3" opacity={0.25} />
              <XAxis type="number" fontSize={12} />
              <YAxis type="category" dataKey="category" fontSize={12} width={130} />
              <Tooltip />
              <Bar dataKey="count" radius={[0, 8, 8, 0]} onClick={() => setFilter("Failed")}>
                {data.breakdown.map((b) => (
                  <Cell key={b.category} fill={SEV_COLOR[b.severity]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </GlassCard>

      <GlassCard className="p-2">
        <div className="flex flex-wrap gap-2 px-3 py-2">
          {["All", "Paid", "Pending", "Failed"].map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              aria-pressed={filter === f}
              className={`rounded-full px-3.5 py-1.5 text-xs font-semibold ${filter === f ? "bg-[var(--primary)] text-white" : "border border-[var(--border)]"}`}
            >
              {f}
            </button>
          ))}
        </div>
        <div className="overflow-x-auto">
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
                  transition={{ delay: i * 0.04 }}
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
      </GlassCard>
    </div>
  );
}
