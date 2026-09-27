"use client";

import { motion } from "framer-motion";
import { ArrowUpDown, Search } from "lucide-react";
import * as React from "react";
import { ClaimRow, RiskBar, SlaBadge } from "@/components/dashboard/bits";
import { GlassCard, ShimmerButton } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/components/ui/controls";
import { Input } from "@/components/ui/primitives";
import { Sheet } from "@/components/ui/sheet";
import { api } from "@/lib/api";
import { maskAadhaar } from "@/lib/utils";
import type { ExceptionItem } from "@/types";

type SortKey = "studentName" | "scheme" | "stage" | "slaElapsedDays" | "riskScore" | "stpScore";

const PAGE_SIZE = 8;

export default function ExceptionsPage() {
  const [rows, setRows] = React.useState<ExceptionItem[]>([]);
  const [query, setQuery] = React.useState("");
  const [sortKey, setSortKey] = React.useState<SortKey>("slaElapsedDays");
  const [sortDesc, setSortDesc] = React.useState(true);
  const [page, setPage] = React.useState(0);
  const [selected, setSelected] = React.useState<ExceptionItem | null>(null);

  React.useEffect(() => {
    api.getExceptionQueue().then(setRows);
  }, []);

  const filtered = React.useMemo(() => {
    const q = query.trim().toLowerCase();
    const base = q
      ? rows.filter((r) =>
          [r.studentName, r.id, r.usidLast8, r.scheme, r.stage].some((v) => v.toLowerCase().includes(q))
        )
      : rows;
    const sorted = [...base].sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      const cmp = typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv));
      return sortDesc ? -cmp : cmp;
    });
    return sorted;
  }, [rows, query, sortKey, sortDesc]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageRows = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  function toggleSort(key: SortKey) {
    if (key === sortKey) {
      setSortDesc((d) => !d);
    } else {
      setSortKey(key);
      setSortDesc(true);
    }
    setPage(0);
  }

  function SortHeader({ label, k }: { label: string; k: SortKey }) {
    return (
      <button
        className="flex items-center gap-1 hover:text-[var(--foreground)]"
        onClick={() => toggleSort(k)}
        aria-label={`Sort by ${label}${sortKey === k ? (sortDesc ? ", descending" : ", ascending") : ""}`}
      >
        {label}
        <ArrowUpDown size={12} aria-hidden />
      </button>
    );
  }

  return (
    <div className="space-y-4">
      <GlassCard className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-52 flex-1">
          <Search size={15} aria-hidden className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)]" />
          <Input
            value={query}
            onChange={(e) => { setQuery(e.target.value); setPage(0); }}
            placeholder="Search name, USID, scheme…"
            aria-label="Filter queue"
            className="pl-9"
          />
        </div>
        <Badge variant="pending">{filtered.length} in queue</Badge>
      </GlassCard>

      <GlassCard className="overflow-x-auto p-2">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead>
            <tr className="text-xs uppercase tracking-wider text-[var(--muted-foreground)]">
              <th className="px-3 py-2"><SortHeader label="Student" k="studentName" /></th>
              <th className="px-3 py-2"><SortHeader label="Scheme" k="scheme" /></th>
              <th className="px-3 py-2"><SortHeader label="Stage" k="stage" /></th>
              <th className="px-3 py-2"><SortHeader label="SLA" k="slaElapsedDays" /></th>
              <th className="px-3 py-2"><SortHeader label="Risk" k="riskScore" /></th>
              <th className="px-3 py-2"><SortHeader label="STP" k="stpScore" /></th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((r, i) => (
              <motion.tr
                key={r.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.03 }}
                onClick={() => setSelected(r)}
                className="cursor-pointer border-t border-[var(--border)] transition-colors hover:bg-[#6366F1]/5"
              >
                <td className="px-3 py-2.5">
                  <span className="block font-semibold">{r.studentName}</span>
                  <span className="font-mono text-xs text-[var(--muted-foreground)]">USID ··{r.usidLast8}</span>
                </td>
                <td className="px-3 py-2.5"><Badge variant="outline">{r.scheme}</Badge></td>
                <td className="px-3 py-2.5 text-xs">{r.stage.replace(/_/g, " ")}</td>
                <td className="px-3 py-2.5"><SlaBadge elapsed={r.slaElapsedDays} limit={r.slaLimitDays} /></td>
                <td className="px-3 py-2.5"><RiskBar score={r.riskScore} /></td>
                <td className="px-3 py-2.5">
                  <Badge variant={r.stpScore >= 85 ? "verified" : r.stpScore >= 70 ? "pending" : "review"} title="Straight-through-processing score">
                    {r.stpScore}%
                  </Badge>
                </td>
              </motion.tr>
            ))}
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-3 py-10 text-center text-sm text-[var(--muted-foreground)]">
                  No files match this filter.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-3 py-2 text-sm">
          <span className="text-[var(--muted-foreground)]">Page {page + 1} of {pageCount}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="rounded-full border border-[var(--border)] px-3 py-1 disabled:opacity-40">
              Prev
            </button>
            <button onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))} disabled={page >= pageCount - 1} className="rounded-full border border-[var(--border)] px-3 py-1 disabled:opacity-40">
              Next
            </button>
          </div>
        </div>
      </GlassCard>

      <Sheet
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `${selected.studentName} · ${selected.id}` : ""}
        subtitle={selected ? `USID ··${selected.usidLast8} · Aadhaar ${maskAadhaar(`0000${selected.aadhaarRefLast4}`)}` : ""}
      >
        {selected ? (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline">{selected.scheme}</Badge>
              <SlaBadge elapsed={selected.slaElapsedDays} limit={selected.slaLimitDays} />
              <Badge variant="review">STP {selected.stpScore}%</Badge>
            </div>
            <div>
              <h3 className="mb-2 text-sm font-bold uppercase tracking-wider text-[var(--muted-foreground)]">Claim verification</h3>
              <div className="space-y-2">
                {selected.claims.map((c) => (
                  <ClaimRow key={c.label} label={c.label} status={c.status} detail={c.detail} />
                ))}
              </div>
            </div>
            <div className="rounded-xl bg-[var(--muted)] p-3 text-sm">
              <h3 className="mb-1 text-xs font-bold uppercase tracking-wider text-[var(--muted-foreground)]">Auto justification</h3>
              <p>{selected.justification}</p>
            </div>
            <div className="flex flex-wrap gap-2 pb-2">
              <ShimmerButton tone="green" onClick={() => { toast(`${selected.id} approved — STP recorded`); setSelected(null); }}>
                Approve
              </ShimmerButton>
              <ShimmerButton tone="amber" onClick={() => toast(`Info requested for ${selected.id}`)}>
                Request Info
              </ShimmerButton>
              <ShimmerButton tone="rose" onClick={() => toast(`${selected.id} escalated to state dept`)}>
                Escalate
              </ShimmerButton>
            </div>
          </div>
        ) : null}
      </Sheet>
    </div>
  );
}
