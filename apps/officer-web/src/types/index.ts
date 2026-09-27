/** Shared domain types — mirror docs/openapi/core.yaml shapes. No `any`. */

export type SchemeName =
  | "Pre-Matric"
  | "Post-Matric"
  | "Top Class"
  | "NFST"
  | "NOS";

export type ApplicationStage =
  | "submitted"
  | "institute_verification"
  | "district_nodal"
  | "state_dept"
  | "ministry"
  | "pfms_payment"
  | "disbursed";

export type ClaimStatus = "gov-verified" | "corroborated" | "assisted" | "pending-review" | "expired" | "expiring";

export type SlaVariant = "ok" | "risk" | "breached";

export interface DashboardMetrics {
  totalApplications: number;
  stpRate: number;
  pendingReview: number;
  paymentSuccessRate: number;
  schemeBreakdown: { scheme: SchemeName; count: number }[];
  disbursementTrend: { month: string; sanctionedPaise: number; paidPaise: number }[];
  totalStudents: number;
  totalApplied: number;
}

export interface ExceptionItem {
  id: string;
  studentName: string;
  usidLast8: string;
  aadhaarRefLast4: string;
  scheme: SchemeName;
  stage: ApplicationStage;
  slaElapsedDays: number;
  slaLimitDays: number;
  riskScore: number;
  stpScore: number;
  claims: { label: string; status: ClaimStatus; detail: string }[];
  justification: string;
  updatedAt: string;
}

export interface CoverageRegion {
  id: string;
  name: string;
  level: "state" | "district" | "block" | "school";
  parentId: string | null;
  totalStudents: number;
  applications: number;
  pvtg: boolean;
}

export interface SchoolOutreachItem {
  id: string;
  schoolName: string;
  district: string;
  classLevel: string;
  stStudents: number;
  applications: number;
  contact: string;
}

export interface IdentityCase {
  id: string;
  nameA: string;
  nameB: string;
  systemA: string;
  systemB: string;
  confidence: number;
  fields: {
    field: string;
    valueA: string;
    valueB: string;
    match: "exact" | "partial" | "mismatch";
    similarity: number;
  }[];
  createdAt: string;
}

export interface DisbursementRecord {
  id: string;
  studentName: string;
  usidLast8: string;
  scheme: SchemeName;
  amountPaise: number;
  status: "paid" | "pending" | "failed";
  failureCode: string | null;
  failureReason: string | null;
  fix: string | null;
  district: string;
  updatedAt: string;
}

export interface FailureBreakdown {
  category: string;
  count: number;
  severity: "high" | "medium" | "low";
}

export interface StudentDashboard {
  greetingName: string;
  schemes: {
    name: SchemeName;
    eligible: boolean;
    status: string;
    amountPaise: number;
    reason: string | null;
  }[];
  applications: {
    id: string;
    scheme: SchemeName;
    stage: string;
    actor: string;
    elapsedDays: number;
    slaDays: number;
  }[];
  receivedPaise: number;
  pendingPaise: number;
  actions: { id: string; title: string; cta: string }[];
}

export interface WalletClaim {
  id: string;
  type: string;
  preview: string;
  source: string;
  validUntil: string;
  status: "valid" | "expiring" | "expired";
  verifiedAt: string;
}

export interface ChatMessage {
  id: string;
  role: "user" | "jago";
  text: string;
  card?: { title: string; rows: { label: string; value: string }[]; actions: string[] };
  createdAt: string;
}
