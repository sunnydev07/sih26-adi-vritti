/** Mobile domain types — same OpenAPI shapes as officer-web, React Native flavor. */

export type SchemeName = "Pre-Matric" | "Post-Matric" | "Top Class" | "NFST" | "NOS";

export interface SchemeCard {
  name: SchemeName;
  eligible: boolean;
  status: string;
  amountPaise: number;
  reason: string | null;
}

export interface TrackedApplication {
  id: string;
  scheme: SchemeName;
  stage: string;
  actor: string;
  elapsedDays: number;
  slaDays: number;
}

export interface PendingAction {
  id: string;
  title: string;
  cta: string;
}

export interface StudentDashboard {
  greetingName: string;
  schemes: SchemeCard[];
  applications: TrackedApplication[];
  receivedPaise: number;
  pendingPaise: number;
  actions: PendingAction[];
  offline: boolean;
  lastSyncAt: string;
}

export type ClaimState = "valid" | "expiring" | "expired";

export interface WalletClaim {
  id: string;
  type: string;
  preview: string;
  source: string;
  validUntil: string;
  status: ClaimState;
  verifiedAt: string;
}

export interface ChatCard {
  title: string;
  rows: { label: string; value: string }[];
  actions: string[];
}

export interface ChatMessage {
  id: string;
  role: "user" | "jago";
  text: string;
  card?: ChatCard;
  createdAt: string;
  queued?: boolean;
  /** askAdi side-channel (offline mirror, consent block, ...); rendered as caption. */
  notice?: "session-expired" | "consent-required" | "mirror-offline" | "lang-coming-soon";
}

export type SupportedLang = "en" | "hi" | "sat" | "gon";

export interface OutboxEntry {
  id: string;
  kind: "message" | "submission" | "consent";
  payload: Record<string, string>;
  createdAt: string;
  /**
   * Retry-safety key, minted at enqueue and replayed verbatim on every retry.
   * Core's verify endpoint deduplicates on the caller key: without this, a
   * retry after a lost response would mint a duplicate verification.
   */
  idempotencyKey: string;
}
