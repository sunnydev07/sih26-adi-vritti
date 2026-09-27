import { Badge } from "@/components/ui/badge";
import { slaStatus } from "@/lib/utils";
import { cn } from "@/lib/utils";

export function SlaBadge({ elapsed, limit }: { elapsed: number; limit: number }) {
  const s = slaStatus(elapsed, limit);
  return (
    <Badge
      variant={s.variant === "breached" ? "failed" : s.variant === "risk" ? "pending" : "verified"}
      className={cn(s.variant === "breached" && "animate-pulse")}
    >
      {s.label}
    </Badge>
  );
}

export function RiskBar({ score }: { score: number }) {
  const color = score >= 70 ? "bg-rose-500" : score >= 40 ? "bg-amber-500" : "bg-emerald-500";
  return (
    <div className="flex items-center gap-2" role="img" aria-label={`Risk score ${score} of 100`}>
      <div className="h-2 w-20 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
        <div className={cn("h-full rounded-full transition-all", color)} style={{ width: `${score}%` }} />
      </div>
      <span className="font-mono text-xs">{score}</span>
    </div>
  );
}

export function ClaimRow({ label, status, detail }: { label: string; status: string; detail: string }) {
  const icon = status === "gov-verified" ? "✅" : status === "pending-review" ? "⏳" : status === "expired" ? "❌" : "🔍";
  return (
    <div className="flex items-start gap-3 rounded-xl border border-[var(--border)] p-3">
      <span aria-hidden className="text-lg">{icon}</span>
      <div className="min-w-0">
        <p className="text-sm font-semibold">{label}</p>
        <p className="text-xs text-[var(--muted-foreground)]">{detail}</p>
        <Badge variant={status === "gov-verified" ? "verified" : status === "pending-review" ? "pending" : "review"} className="mt-1">
          {status}
        </Badge>
      </div>
    </div>
  );
}
