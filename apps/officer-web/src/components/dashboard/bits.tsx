import { Badge } from "@/components/ui/badge";
import { plainClaimStatus, type PlainTone } from "@/lib/plain";
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

const TONE_BADGE: Record<PlainTone, "verified" | "pending" | "failed" | "review"> = {
  ok: "verified",
  warn: "pending",
  bad: "failed",
  info: "review",
};

const TONE_ICON: Record<PlainTone, string> = {
  ok: "✅",
  warn: "⏳",
  bad: "❌",
  info: "🔍",
};

export function ClaimRow({ label, status, detail }: { label: string; status: string; detail: string }) {
  // Render through the shared vocabulary, not the raw tier code: every tier
  // except gov-verified/pending-review/expired used to fall into a violet
  // "review" badge with its code string, so verified `corroborated` and
  // `assisted` rows triaged as risky files needing review.
  const words = plainClaimStatus(status);
  const icon = TONE_ICON[words.tone];
  return (
    <div className="flex items-start gap-3 rounded-xl border border-[var(--border)] p-3">
      <span aria-hidden className="text-lg">{icon}</span>
      <div className="min-w-0">
        <p className="text-sm font-semibold">{label}</p>
        <p className="text-xs text-[var(--muted-foreground)]">{detail || words.detail}</p>
        <Badge variant={TONE_BADGE[words.tone]} className="mt-1">
          {words.words}
        </Badge>
      </div>
    </div>
  );
}
