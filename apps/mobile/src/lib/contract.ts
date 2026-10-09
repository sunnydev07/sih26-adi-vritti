/**
 * Core API contract seam (docs/openapi/core.yaml) — DTO shapes and mappers.
 *
 * The screens are still mock-backed (no API client exists yet), but the day
 * anyone wires `GET /v1/scholars/{usid}/dashboard` or `GET .../claims` to
 * them, the wire shapes must already be defined here — the app's old render
 * types had ~zero overlap with the contract (`eligible`, `reason`,
 * `amountPaise`, `status`, `preview` exist in neither response), so a direct
 * wiring would have rendered blank cards and crashed the home screen.
 *
 * Rule: screens render `StudentDashboard` / `WalletClaim` (app types);
 * anything from the network passes through `toStudentDashboard` /
 * `toWalletClaim` first. Display status is DERIVED client-side (expired /
 * expiring window / verified tier) because the contract `Claim` carries no
 * `status` string — keying renders off a field the API never returns is what
 * produced the empty pills.
 */
import type {
  PendingAction,
  SchemeCard,
  SchemeName,
  StudentDashboard,
  WalletClaim,
} from "@/types";

/** GET /v1/scholars/{usid}/dashboard — DashboardResponse. */
export interface CoreSchemeStatus {
  scheme: string;
  eligibility: "eligible" | "not_eligible" | "missing_items";
  stage: string;
  current_actor: string;
  days_elapsed: number;
  sla_days: number;
  sanctioned_amount_paise: number;
  paid_amount_paise: number;
}

export interface CoreMoneySnapshot {
  received_paise: number;
  pending_paise: number;
  total_sanctioned_paise: number;
}

export interface CorePendingAction {
  type: string;
  message: string;
  action_url: string | null;
}

export interface CoreDashboardResponse {
  usid: string;
  schemes: CoreSchemeStatus[];
  money: CoreMoneySnapshot;
  pending_actions: CorePendingAction[];
}

/** GET /v1/scholars/{usid}/claims — Claim. */
export interface CoreClaim {
  id: string;
  usid: string;
  claim_type: string;
  source: string;
  method: string;
  confidence: number;
  verified_at: string;
  valid_until: string;
  expired: boolean;
}

/** GET /v1/applications/{id}/timeline — ApplicationTimelineResponse. */
export interface CoreTimelineEvent {
  stage: string;
  actor: string;
  timestamp: string;
  notes: string | null;
}

export interface CoreTimelineResponse {
  application_id: string;
  current_stage: string;
  current_actor: string;
  sla_deadline: string | null;
  days_elapsed: number;
  events: CoreTimelineEvent[];
}

const SCHEME_LABELS: Record<string, SchemeName> = {
  PRE_MATRIC: "Pre-Matric",
  POST_MATRIC: "Post-Matric",
  TOP_CLASS: "Top Class",
  NFST: "NFST",
  NOS: "NOS",
};

export function schemeLabel(scheme: string): SchemeName {
  return SCHEME_LABELS[scheme] ?? (scheme as SchemeName);
}

/** Application stage -> student-facing status line (mirrors Core deriveStatus). */
export function stageStatus(stage: string): string {
  switch (stage) {
    case "submitted":
      return "Applied";
    case "pfms_payment":
      return "Approved · payment queued";
    case "disbursed":
      return "Paid";
    case "institute_verification":
    case "district_nodal":
    case "state_dept":
    case "ministry":
      return "In verification";
    default:
      return "In verification";
  }
}

const CLAIM_TYPE_LABELS: Record<string, string> = {
  identity: "Identity Document",
  st_status: "ST Certificate",
  income: "Income Certificate",
  domicile: "Domicile Certificate",
  academic: "Academic Certificate",
  enrolment: "Enrolment Certificate",
  institution: "Institution Certificate",
  net_jrf: "NET/JRF Scorecard",
  disability: "Disability Certificate",
  bank_account: "Bank Account",
};

export function claimTypeLabel(claimType: string): string {
  return CLAIM_TYPE_LABELS[claimType] ?? claimType;
}

/** Days until `valid_until` goes past, from now. Negative means expired. */
function daysUntilValid(validUntil: string, now: number): number {
  return (new Date(validUntil).getTime() - now) / 86_400_000;
}

/**
 * Contract claim -> app display state. The contract has no `status` string,
 * so it is derived: expired flag or past date -> "expired", inside the
 * 30-day renewal window -> "expiring", otherwise still verified -> "valid".
 * Unknown contract `method` tiers still map to "valid": the pill must never
 * render a raw tier code.
 */
export function claimDisplayStatus(
  claim: Pick<CoreClaim, "expired" | "valid_until">,
  now: number = Date.now(),
): WalletClaim["status"] {
  if (claim.expired || daysUntilValid(claim.valid_until, now) < 0) return "expired";
  if (daysUntilValid(claim.valid_until, now) <= 30) return "expiring";
  return "valid";
}

export function toWalletClaim(claim: CoreClaim, now: number = Date.now()): WalletClaim {
  return {
    id: claim.id,
    type: claimTypeLabel(claim.claim_type),
    preview: `${claim.source} · verified ${claim.verified_at.slice(0, 10)}`,
    source: claim.source,
    validUntil: claim.valid_until,
    status: claimDisplayStatus(claim, now),
    verifiedAt: claim.verified_at,
  };
}

export function toStudentDashboard(
  dto: CoreDashboardResponse,
  opts: { greetingName: string; offline: boolean; lastSyncAt: string },
): StudentDashboard {
  const schemes: SchemeCard[] = dto.schemes.map((s) => ({
    name: schemeLabel(s.scheme),
    eligible: s.eligibility === "eligible",
    status: stageStatus(s.stage),
    amountPaise: s.sanctioned_amount_paise,
    reason: s.eligibility === "eligible" ? null : s.stage,
  }));
  // NOTE: DashboardResponse carries no application UUIDs (schemes are not
  // applications), so `applications` stays empty here. Timeline deep links
  // need real ids — `pending_actions[].action_url` carries
  // "/applications/{id}/timeline" links when there is something to appeal.
  // Synthesising ids would land every link on "File not found".
  const applications: StudentDashboard["applications"] = [];
  const actions: PendingAction[] = dto.pending_actions.map((a, i) => ({
    id: `${a.type}-${i}`,
    title: a.message,
    cta: a.action_url ?? "View",
  }));
  return {
    greetingName: opts.greetingName,
    schemes,
    applications,
    receivedPaise: dto.money.received_paise,
    pendingPaise: dto.money.pending_paise,
    actions,
    offline: opts.offline,
    lastSyncAt: opts.lastSyncAt,
  };
}
