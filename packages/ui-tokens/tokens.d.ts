/** Typed view of tokens.json — import the JSON and annotate with this. */

export type StatusTone = "ok" | "warn" | "bad" | "info";

export interface StatusSwatch {
  color: string;
  bg: string;
  ink: string;
}

export interface FailureFix {
  /** Backend-exact cause (mirrors FailureDecoder.java). */
  cause: string;
  /** Judge-friendly headline. */
  short: string;
  /** Backend-exact fix instruction. */
  fix: string;
}

export interface UiTokens {
  brand: {
    primary: string;
    primaryStrong: string;
    primarySoft: string;
    accent: string;
    ink: string;
    muted: string;
    surface: string;
    page: string;
    border: string;
  };
  status: Record<StatusTone, StatusSwatch>;
  dark: {
    page: string;
    surface: string;
    ink: string;
    muted: string;
    border: string;
  };
  radius: { card: number; pill: number; control: number };
  touch: { minTarget: number };
  type: { hero: number; title: number; body: number; caption: number; tiny: number };
  layout: {
    mobileMaxWidth: number;
    cardPadding: number;
    screenPadding: number;
    maxCardsBeforeFold: number;
  };
  vocabulary: Record<string, string>;
  sla: {
    atRiskWindowDays: number;
    words: { ok: string; risk: string; breached: string };
  };
  claimStatus: Record<string, { tone: StatusTone; words: string; detail: string }>;
  stp: {
    autoApproveThreshold: number;
    reviewThreshold: number;
    words: { ready: string; review: string; manual: string };
  };
  failureFixes: Record<string, FailureFix>;
  assistant: { name: string; suggestions: string[] };
}

declare const tokens: UiTokens;
export default tokens;
