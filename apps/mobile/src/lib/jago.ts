import type { ChatMessage, StudentDashboard, WalletClaim } from "@/types";

/**
 * CRITICAL RULE (mirrors backend JAGO+ tool router): factual scholarship
 * status is ALWAYS rendered from a template filled with structured tool
 * output. The LLM never free-generates stage, amount, or eligibility text.
 */
export interface StatusToolOutput {
  schemeName: string;
  stage: string;
  actor: string;
  days: number;
  slaDays: number;
}

export function renderStatusTemplate(out: StatusToolOutput): string {
  const over = out.days > out.slaDays ? ` — ${out.days - out.slaDays} days over SLA` : "";
  return `Your ${out.schemeName} application is at the ${out.stage} stage. ${out.actor} has been reviewing it for ${out.days} days (SLA is ${out.slaDays} days)${over}.`;
}

export interface EligibilityToolOutput {
  schemeName: string;
  // Contract enums (docs/openapi/core.yaml: eligible|not_eligible|missing_items),
  // not paraphrases: wiring these straight into a template must not need a
  // translation table that drifts.
  verdict: "eligible" | "not_eligible" | "missing_items";
  missingItem: string | null;
}

export function renderEligibilityTemplate(out: EligibilityToolOutput): string {
  if (out.verdict === "eligible") return `You are eligible for ${out.schemeName}.`;
  if (out.verdict === "missing_items")
    return `You are one item away from ${out.schemeName}: ${out.missingItem ?? "a pending document"}.`;
  return `You are not eligible for ${out.schemeName}${out.missingItem ? `: ${out.missingItem}` : "."}`;
}

export const mockDashboard: StudentDashboard = {
  greetingName: "Sunita",
  schemes: [
    { name: "Pre-Matric", eligible: false, status: "Not eligible", amountPaise: 0, reason: "Passed Class X in 2024" },
    { name: "Post-Matric", eligible: true, status: "Applied · district nodal", amountPaise: 1240000, reason: null },
    { name: "Top Class", eligible: false, status: "Not eligible", amountPaise: 0, reason: "Institution not in Top Class list" },
    { name: "NFST", eligible: false, status: "Need PG + NET", amountPaise: 0, reason: "Requires post-graduation" },
    { name: "NOS", eligible: false, status: "Need top-100 admit", amountPaise: 0, reason: "Requires foreign university admit" },
  ],
  applications: [
    { id: "APP-90412", scheme: "Post-Matric", stage: "District nodal verification", actor: "District Nodal Officer, Mandla", elapsedDays: 11, slaDays: 7 },
  ],
  receivedPaise: 1240000,
  pendingPaise: 480000,
  actions: [
    { id: "ACT-1", title: "Income certificate expired for FY 2026-27 — refetch from DigiLocker (1 tap)", cta: "Refetch now" },
  ],
  offline: false,
  lastSyncAt: "2026-09-26T08:00:00+05:30",
};

export const mockClaims: WalletClaim[] = [
  { id: "CLM-1", type: "ST Certificate", preview: "Community: Gond · State: MP", source: "State portal", validUntil: "2028-03-31T00:00:00+05:30", status: "valid", verifiedAt: "2026-08-02T10:00:00+05:30" },
  { id: "CLM-2", type: "Income Certificate", preview: "₹2,40,000 · FY 2025-26", source: "DigiLocker", validUntil: "2026-09-10T00:00:00+05:30", status: "expired", verifiedAt: "2025-08-02T10:00:00+05:30" },
  { id: "CLM-3", type: "Bank Account", preview: "A/c ····4821 · IFSC updated", source: "PFMS", validUntil: "2027-01-01T00:00:00+05:30", status: "expiring", verifiedAt: "2026-06-11T10:00:00+05:30" },
];

export const mockMessages: ChatMessage[] = [
  { id: "m1", role: "jago", text: "Namaste Sunita! Hindi, English, Santali ya Gondi me poochho.", createdAt: "2026-09-26T09:00:00+05:30" },
  {
    id: "m2",
    role: "jago",
    text: renderStatusTemplate({ schemeName: "Post-Matric", stage: "district nodal", actor: "District Nodal Officer, Mandla", days: 11, slaDays: 7 }),
    card: {
      title: "APP-90412 · Post-Matric",
      rows: [
        { label: "Stage", value: "District nodal verification" },
        { label: "SLA", value: "11 / 7 days — breached" },
      ],
      actions: ["Escalate", "Connect DigiLocker"],
    },
    createdAt: "2026-09-26T09:01:20+05:30",
  },
];
