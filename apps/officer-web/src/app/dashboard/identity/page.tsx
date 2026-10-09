"use client";

import { motion } from "framer-motion";
import * as React from "react";
import { GlassCard, ProgressRing, ShimmerButton } from "@/components/effects/premium";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/components/ui/controls";
import { Dialog } from "@/components/ui/sheet";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { IdentityCase } from "@/types";

export default function IdentityPage() {
  const [queue, setQueue] = React.useState<IdentityCase[]>([]);
  const [activeId, setActiveId] = React.useState<string | null>(null);
  // Decisions made on this screen only. Starts at 0: the old hardcoded 23
  // presented an invented figure as a scoreboard. Nothing here writes back —
  // Core has no /v1/admin/identity-queue or adjudication endpoint — so the
  // count resets on reload and says so.
  const [resolved, setResolved] = React.useState(0);
  // A rejected queue load must not render "Queue clear" — failure is not an
  // empty queue.
  const [loadFailed, setLoadFailed] = React.useState(false);

  const load = React.useCallback(() => {
    api.getIdentityQueue().then(
      (q) => {
        setQueue(q);
        setActiveId(q[0]?.id ?? null);
      },
      () => setLoadFailed(true)
    );
  }, []);

  React.useEffect(() => {
    load();
  }, [load]);

  function retry() {
    setLoadFailed(false);
    load();
  }
  const [confirm, setConfirm] = React.useState<"merge" | "reject" | null>(null);

  const active = queue.find((c) => c.id === activeId) ?? null;

  function decide(kind: "merge" | "reject" | "info") {
    if (!active) return;
    if (kind === "info") {
      toast(`Demo: ${active.id} staged for need-more-info — nothing sent (demo console).`);
      return;
    }
    setQueue((q) => q.filter((c) => c.id !== active.id));
    setResolved((r) => r + 1);
    setActiveId((id) => {
      // Advance to the case AFTER the decided one, not back to the first:
      // the old rest[0] jumped the officer to the top of the queue every time.
      const idx = queue.findIndex((c) => c.id === id);
      const rest = queue.filter((c) => c.id !== id);
      return rest[idx]?.id ?? rest[0]?.id ?? null;
    });
    setConfirm(null);
    toast(kind === "merge" ? `Demo: ${active.id} staged as merged — no USID was linked (demo console).` : `Demo: ${active.id} staged as separate — nothing recorded (demo console).`);
  }

  return (
    <div className="grid gap-4 lg:grid-cols-5">
      <div className="space-y-3 lg:col-span-2">
        <p className="text-sm text-[var(--muted-foreground)]">
          Sorted by confidence — most uncertain first. Decided this session (demo, not recorded): <strong className="text-[var(--foreground)]">{resolved}</strong>
        </p>
        {loadFailed ? (
          <GlassCard>
            <p className="text-sm font-semibold">Identity queue did not load.</p>
            <p className="mt-1 text-sm text-[var(--muted-foreground)]">
              No cases are shown — this is not a clear queue.
            </p>
            <button
              onClick={retry}
              className="min-touch mt-3 rounded-full border border-[var(--border)] px-4 text-sm font-semibold"
            >
              Retry
            </button>
          </GlassCard>
        ) : null}
        {queue.map((c, i) => (
          <motion.button
            key={c.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            onClick={() => setActiveId(c.id)}
            aria-pressed={c.id === activeId}
            className={cn(
              "w-full rounded-2xl border p-4 text-left transition-transform hover:scale-[1.01]",
              c.id === activeId ? "border-[#6366F1] bg-[#6366F1]/5" : "border-[var(--border)]"
            )}
          >
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="font-semibold">{c.nameA} ⇄ {c.nameB}</span>
              <Badge variant={c.confidence < 0.7 ? "failed" : c.confidence < 0.85 ? "pending" : "verified"}>
                {Math.round(c.confidence * 100)}%
              </Badge>
            </div>
            <p className="mt-1 font-mono text-xs text-[var(--muted-foreground)]">
              {c.systemA} vs {c.systemB}
            </p>
          </motion.button>
        ))}
        {queue.length === 0 ? (
          <GlassCard>
            <p className="text-sm">Queue clear. All uncertain links adjudicated.</p>
          </GlassCard>
        ) : null}
      </div>

      <div className="lg:col-span-3">
        <p className="mb-2 text-sm text-[var(--muted-foreground)] lg:hidden">
          Same person or two? Green rows match, red rows differ — then decide below.
        </p>
        {active ? (
          <GlassCard>
            <div id="identity-compare" className="grid scroll-mt-24 gap-3 md:grid-cols-[1fr_auto_1fr] md:items-center">
              <div className="rounded-xl border border-[var(--border)] p-3">
                <h3 className="mb-2 text-sm font-bold">System A Record</h3>
                <p className="font-mono text-xs text-[var(--muted-foreground)]">{active.systemA}</p>
                <FieldRows fields={active.fields} side="valueA" />
              </div>
              <div className="mx-auto">
                <ProgressRing pct={active.confidence * 100} label="match confidence" />
              </div>
              <div className="rounded-xl border border-[var(--border)] p-3">
                <h3 className="mb-2 text-sm font-bold">System B Record</h3>
                <p className="font-mono text-xs text-[var(--muted-foreground)]">{active.systemB}</p>
                <FieldRows fields={active.fields} side="valueB" />
              </div>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <ShimmerButton tone="green" onClick={() => setConfirm("merge")}>Same Person</ShimmerButton>
              <ShimmerButton tone="rose" onClick={() => setConfirm("reject")}>Different People</ShimmerButton>
              <ShimmerButton tone="amber" onClick={() => decide("info")}>Need More Info</ShimmerButton>
            </div>
          </GlassCard>
        ) : (
          <GlassCard>
            <p className="text-sm text-[var(--muted-foreground)]">Select a case from the queue.</p>
          </GlassCard>
        )}
      </div>

      <Dialog
        open={confirm !== null}
        onClose={() => setConfirm(null)}
        title={confirm === "merge" ? "Merge into one USID?" : "Keep as two people?"}
        onConfirm={() => decide(confirm ?? "info")}
        confirmLabel={confirm === "merge" ? "Merge" : "Confirm separate"}
      >
        <p className="text-[var(--muted-foreground)]">
          {confirm === "merge"
            ? "Demo only: no links change and no training signal is sent. In integration both system links point at a single USID with full provenance."
            : "Demo only: nothing is recorded. In integration both records keep separate USIDs with your reason recorded."}
        </p>
      </Dialog>
    </div>
  );
}

function FieldRows({ fields, side }: { fields: IdentityCase["fields"]; side: "valueA" | "valueB" }) {
  return (
    <ul className="mt-2 space-y-1.5">
      {fields.map((f) => (
        <li
          key={f.field}
          className={cn(
            "rounded-lg px-2 py-1 text-sm",
            f.match === "exact" && "bg-emerald-500/10",
            f.match === "partial" && "bg-amber-500/10",
            f.match === "mismatch" && "bg-rose-500/10"
          )}
          title={f.match === "partial" ? `${f.similarity}% similar` : f.match}
        >
          <span className="mr-2 text-xs text-[var(--muted-foreground)]">{f.field}</span>
          <span className="font-medium">{f[side]}</span>
          {f.match === "partial" ? <span className="ml-2 font-mono text-[11px] text-amber-700">~{f.similarity}%</span> : null}
        </li>
      ))}
    </ul>
  );
}
