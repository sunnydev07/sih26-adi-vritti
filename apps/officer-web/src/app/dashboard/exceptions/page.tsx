"use client";

import { motion } from "framer-motion";
import { ArrowUpDown, ChevronRight, Search } from "lucide-react";
import * as React from "react";
import { ClaimRow, RiskBar, SlaBadge } from "@/components/dashboard/bits";
import { GlassCard, ShimmerButton } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/components/ui/controls";
import { Input } from "@/components/ui/primitives";
import { Sheet } from "@/components/ui/sheet";
import { api, type JevStpResult } from "@/lib/api";
import { plainSla, plainStp } from "@/lib/plain";
import { cn, maskAadhaar } from "@/lib/utils";
import type { ExceptionItem } from "@/types";

type SortKey = "studentName" | "scheme" | "stage" | "slaElapsedDays" | "riskScore" | "stpScore";

const PAGE_SIZE = 8;

/**
 * Module scope, not render scope: defining this inside the page component
 * remounts every header button on each keystroke and trips
 * react-hooks/incompatible-library (components created during render).
 */
function SortHeader({
  label,
  k,
  sortKey,
  sortDesc,
  onToggle,
}: {
  label: string;
  k: SortKey;
  sortKey: SortKey;
  sortDesc: boolean;
  onToggle: (k: SortKey) => void;
}) {
  return (
    <button
      className="flex items-center gap-1 hover:text-[var(--foreground)]"
      onClick={() => onToggle(k)}
      aria-label={`Sort by ${label}${sortKey === k ? (sortDesc ? ", descending" : ", ascending") : ""}`}
    >
      {label}
      <ArrowUpDown size={12} aria-hidden />
    </button>
  );
}

export default function ExceptionsPage() {
  const [rows, setRows] = React.useState<ExceptionItem[]>([]);
  const [query, setQuery] = React.useState("");
  const [sortKey, setSortKey] = React.useState<SortKey>("slaElapsedDays");
  const [sortDesc, setSortDesc] = React.useState(true);
  const [page, setPage] = React.useState(0);
  const [selected, setSelected] = React.useState<ExceptionItem | null>(null);
  const [jevResult, setJevResult] = React.useState<JevStpResult | null>(null);
  const [isEvaluating, setIsEvaluating] = React.useState(false);
  // A rejected queue load must not render "No files match" — that would read
  // as an empty queue rather than a failed one.
  const [loadFailed, setLoadFailed] = React.useState(false);

  // Render-time reset (not an effect update): a new selection discards the
  // previous file's JEV card before the evaluation for the new one lands.
  // Doing this in an effect trips set-state-in-effect.
  const [prevSelected, setPrevSelected] = React.useState<ExceptionItem | null>(null);
  if (selected !== prevSelected) {
    setPrevSelected(selected);
    setJevResult(null);
  }

  const load = React.useCallback(() => {
    api.getExceptionQueue().then(setRows, () => setLoadFailed(true));
  }, []);

  React.useEffect(() => {
    load();
  }, [load]);

  function retry() {
    setLoadFailed(false);
    load();
  }

  // Selection AND evaluation live in this handler, not an effect: starting
  // the JEV call here keeps every setState inside an event, which is what
  // set-state-in-effect demands. The sequence guard drops a late result when
  // the officer taps through rows faster than the lane answers.
  const evalSeq = React.useRef(0);
  function select(r: ExceptionItem | null) {
    setSelected(r);
    if (!r) return;
    const seq = ++evalSeq.current;
    setIsEvaluating(true);
    api.evaluateStpWithJev(r).then(
      (res) => {
        if (seq !== evalSeq.current) return;
        setJevResult(res);
        setIsEvaluating(false);
      },
      () => {
        if (seq !== evalSeq.current) return;
        setJevResult(null);
        setIsEvaluating(false);
      }
    );
  }

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

  function sortHeader(label: string, k: SortKey) {
    return (
      <SortHeader label={label} k={k} sortKey={sortKey} sortDesc={sortDesc} onToggle={toggleSort} />
    );
  }

  function sortDirection(k: SortKey): "ascending" | "descending" | "none" {
    if (sortKey !== k) return "none";
    return sortDesc ? "descending" : "ascending";
  }

  const autoEligibleCount = React.useMemo(() => rows.filter((r) => r.stpScore >= 85).length, [rows]);

  function handleBatchAutoApprove() {
    const eligible = rows.filter((r) => r.stpScore >= 85);
    if (eligible.length === 0) {
      toast("No applications currently meet the ≥85% STP auto-approval threshold.");
      return;
    }
    setRows((prev) => prev.filter((r) => r.stpScore < 85));
    toast(`Demo: ${eligible.length} applications cleared from this queue — nothing was written back (demo console).`);
  }

  return (
    <div className="space-y-4">
      {loadFailed ? (
        <GlassCard>
          <p className="text-sm font-semibold">Queue did not load.</p>
          <p className="mt-1 text-sm text-[var(--muted-foreground)]">
            The failure list is unavailable — this is not an empty queue.
          </p>
          <button
            onClick={retry}
            className="min-touch mt-3 rounded-full border border-[var(--border)] px-4 text-sm font-semibold"
          >
            Retry
          </button>
        </GlassCard>
      ) : null}
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
        <div className="flex items-center gap-2">
          {autoEligibleCount > 0 ? (
            <ShimmerButton tone="green" onClick={handleBatchAutoApprove} className="text-xs py-1.5 px-3">
              ⚡ Batch Auto-Approve ({autoEligibleCount} files ≥85%)
            </ShimmerButton>
          ) : null}
          <Badge variant="pending">{filtered.length} in queue</Badge>
        </div>
        {/* Plain-words sort: two choices, not six columns. Desktop table keeps full sorting. */}
        <div className="flex w-full gap-2 lg:hidden" role="group" aria-label="Sort queue">
          {(
            [
              { id: "overdue", label: "Most overdue", key: "slaElapsedDays" },
              { id: "ready", label: "Ready to approve", key: "stpScore" },
            ] as const
          ).map((o) => (
            <button
              key={o.id}
              onClick={() => { setSortKey(o.key); setSortDesc(true); setPage(0); }}
              aria-pressed={sortKey === o.key}
              className={cn(
                "min-touch flex-1 rounded-full px-3 text-xs font-semibold",
                sortKey === o.key ? "bg-[var(--primary)] text-white" : "border border-[var(--border)]"
              )}
            >
              {o.label}
            </button>
          ))}
        </div>
      </GlassCard>

      {/* Mobile cards: one file per card, plain words, tap for detail. */}
      <ul id="queue-decide" className="space-y-2.5 lg:hidden" aria-label="Exception queue">
        {pageRows.map((r) => {
          const sla = plainSla(r.slaElapsedDays, r.slaLimitDays);
          const stp = plainStp(r.stpScore);
          return (
            <li key={r.id}>
              <button
                onClick={() => select(r)}
                className="min-touch w-full rounded-2xl border border-[var(--border)] bg-[var(--card)] p-4 text-left"
                aria-label={`${r.studentName}, ${r.scheme}, ${sla.headline}, ${stp.words}`}
              >
                <span className="flex items-center gap-2">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold">{r.studentName}</span>
                    <span className="font-mono text-xs text-[var(--muted-foreground)]">
                      {r.scheme} · USID ··{r.usidLast8}
                    </span>
                  </span>
                  <ChevronRight size={18} aria-hidden className="shrink-0 text-[var(--muted-foreground)]" />
                </span>
                <span className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                  <SlaBadge elapsed={r.slaElapsedDays} limit={r.slaLimitDays} />
                  <span className="text-[var(--muted-foreground)]">{sla.detail}</span>
                </span>
                <span className="mt-1.5 block text-xs font-semibold text-[#4338CA]">{stp.words} · {r.stpScore}%</span>
              </button>
            </li>
          );
        })}
        {pageRows.length === 0 ? (
          <li className="rounded-2xl border border-[var(--border)] p-8 text-center text-sm text-[var(--muted-foreground)]">
            No files match this filter.
          </li>
        ) : null}
      </ul>

      <GlassCard className="hidden overflow-x-auto p-2 lg:block">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead>
            <tr className="text-xs uppercase tracking-wider text-[var(--muted-foreground)]">
              <th scope="col" aria-sort={sortDirection("studentName")} className="px-3 py-2">{sortHeader("Student", "studentName")}</th>
              <th scope="col" aria-sort={sortDirection("scheme")} className="px-3 py-2">{sortHeader("Scheme", "scheme")}</th>
              <th scope="col" aria-sort={sortDirection("stage")} className="px-3 py-2">{sortHeader("Stage", "stage")}</th>
              <th scope="col" aria-sort={sortDirection("slaElapsedDays")} className="px-3 py-2">{sortHeader("SLA", "slaElapsedDays")}</th>
              <th scope="col" aria-sort={sortDirection("riskScore")} className="px-3 py-2">{sortHeader("Risk", "riskScore")}</th>
              <th scope="col" aria-sort={sortDirection("stpScore")} className="px-3 py-2">{sortHeader("STP", "stpScore")}</th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((r, i) => (
              <motion.tr
                key={r.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.03 }}
                onClick={() => select(r)}
                // Rows open the detail sheet: keyboard users get the same path
                // via Tab + Enter, announced with the same words as the mobile
                // card variant.
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    select(r);
                  }
                }}
                aria-label={`${r.studentName}, ${r.scheme}, open file detail`}
                className="cursor-pointer border-t border-[var(--border)] transition-colors hover:bg-[#6366F1]/5 focus-visible:outline-2"
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
      </GlassCard>

      <div className="flex items-center justify-between rounded-2xl border border-[var(--border)] bg-[var(--card)] px-4 py-2.5 text-sm">
        <span className="text-[var(--muted-foreground)]">Page {page + 1} of {pageCount}</span>
        <div className="flex gap-2">
          <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="min-touch rounded-full border border-[var(--border)] px-4 disabled:opacity-40">
            Prev
          </button>
          <button onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))} disabled={page >= pageCount - 1} className="min-touch rounded-full border border-[var(--border)] px-4 disabled:opacity-40">
            Next
          </button>
        </div>
      </div>

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
            <p className="text-xs text-[var(--muted-foreground)]">
              {plainSla(selected.slaElapsedDays, selected.slaLimitDays).detail} ·{" "}
              {plainStp(selected.stpScore).words}
            </p>
            <div>
              <h3 className="mb-2 text-sm font-bold uppercase tracking-wider text-[var(--muted-foreground)]">Claim verification</h3>
              <div className="space-y-2">
                {selected.claims.map((c) => (
                  <ClaimRow key={c.label} label={c.label} status={c.status} detail={c.detail} />
                ))}
              </div>
            </div>
            {/* JEV Real-Time Decision Card */}
            <div className="rounded-xl border border-[#6366F1]/30 bg-gradient-to-br from-[#6366F1]/10 via-[var(--card)] to-[#8B5CF6]/5 p-3.5 text-sm shadow-sm space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-bold text-xs uppercase tracking-wider text-[#6366F1]">
                  <span>⚡ JEV 1.13 Decision Engine</span>
                </div>
                {isEvaluating ? (
                  <span className="text-xs text-[var(--muted-foreground)] animate-pulse">Evaluating...</span>
                ) : (
                  <Badge variant={jevResult?.autoApproveSafe ? "verified" : "review"}>
                    {jevResult?.provider || "JEV System One"}
                  </Badge>
                )}
              </div>
              {jevResult ? (
                <div className="space-y-1.5 pt-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[var(--muted-foreground)]">Auto-Approve Probability:</span>
                    <span className="font-bold font-mono text-[var(--foreground)]">{jevResult.probability}%</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[var(--muted-foreground)]">Recommended Routing:</span>
                    <span className="font-semibold capitalize text-[#6366F1]">{jevResult.routing.replace(/_/g, " ")}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-[var(--muted-foreground)]">Decision Latency:</span>
                    <span className="font-mono text-xs text-[var(--muted-foreground)]">{jevResult.latencyMs}ms</span>
                  </div>
                  {jevResult.autoApproveSafe ? (
                    <div className="pt-2">
                      <ShimmerButton
                        tone="green"
                        className="w-full text-center justify-center font-bold py-2 text-xs"
                        onClick={() => {
                          setRows((prev) => prev.filter((item) => item.id !== selected.id));
                          toast(`Demo: ${selected.id} cleared from this queue — no audit record written (demo console).`);
                          setSelected(null);
                        }}
                      >
                        ⚡ One-Click Auto-Approve (JEV Certified)
                      </ShimmerButton>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="rounded-xl bg-[var(--muted)] p-3 text-sm">
              <h3 className="mb-1 text-xs font-bold uppercase tracking-wider text-[var(--muted-foreground)]">Auto justification</h3>
              <p>{selected.justification}</p>
            </div>
            <div className="flex flex-wrap gap-2 pb-2">
              <ShimmerButton tone="green" onClick={() => {
                setRows((prev) => prev.filter((item) => item.id !== selected.id));
                toast(`Demo: ${selected.id} cleared from this queue — nothing recorded (demo console).`);
                setSelected(null);
              }}>
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
