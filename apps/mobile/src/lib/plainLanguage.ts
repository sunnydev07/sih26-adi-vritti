/**
 * Plain-language status words for judges, officers, and students.
 * Thresholds mirror the backend: SLA breached when elapsed > limit
 * (officer-web slaStatus), STP auto-approvable at >= 85, failure text
 * matches services/core FailureDecoder.java via shared tokens.
 */
import rawTokens from "@adi-vritti/ui-tokens";
import type { Tone } from "@/lib/theme";

/* JSON imports infer literal object types with no index signature —
   view the tables as Records at the boundary. */
const tokens = rawTokens as unknown as {
  sla: { atRiskWindowDays: number; words: { ok: string; risk: string; breached: string } };
  claimStatus: Record<string, { tone: Tone; words: string; detail: string }>;
  stp: {
    autoApproveThreshold: number;
    reviewThreshold: number;
    words: { ready: string; review: string; manual: string };
  };
  failureFixes: Record<string, { cause: string; short: string; fix: string }>;
  vocabulary: Record<string, string>;
};

export interface SlaWords {
  tone: Tone;
  headline: string;
  detail: string;
}

export function slaWords(elapsed: number, limit: number): SlaWords {
  const over = elapsed - limit;
  if (elapsed > limit) {
    return {
      tone: "bad",
      headline: `${tokens.sla.words.breached} by ${over} day${over === 1 ? "" : "s"}`,
      detail: `Waiting ${elapsed} days · limit was ${limit}`,
    };
  }
  if (elapsed >= limit - tokens.sla.atRiskWindowDays) {
    return {
      tone: "warn",
      headline: tokens.sla.words.risk,
      detail: `Waiting ${elapsed} days · limit is ${limit}`,
    };
  }
  return {
    tone: "ok",
    headline: tokens.sla.words.ok,
    detail: `Waiting ${elapsed} days · limit is ${limit}`,
  };
}

export interface ClaimWords {
  tone: Tone;
  words: string;
  detail: string;
}

/**
 * App display states ("valid") are older than the token table, whose keys are
 * verifier tiers ("gov-verified"). Resolve through the alias map so a verified
 * document reads "Verified" instead of echoing the internal enum word — and so
 * a raw contract tier passed straight through still renders correctly.
 */
const STATUS_ALIASES: Record<string, string> = {
  valid: "gov-verified",
};

export function claimWords(status: string): ClaimWords {
  const entry = tokens.claimStatus[STATUS_ALIASES[status] ?? status];
  if (!entry) return { tone: "info", words: status, detail: "" };
  return { tone: entry.tone, words: entry.words, detail: entry.detail };
}

export interface StpWords {
  tone: Tone;
  words: string;
}

export function stpWords(score: number): StpWords {
  if (score >= tokens.stp.autoApproveThreshold) {
    return { tone: "ok", words: tokens.stp.words.ready };
  }
  if (score >= tokens.stp.reviewThreshold) {
    return { tone: "warn", words: tokens.stp.words.review };
  }
  return { tone: "bad", words: tokens.stp.words.manual };
}

export interface FailureWords {
  cause: string;
  short: string;
  fix: string;
}

const FALLBACK_FAILURE: FailureWords = {
  cause: "Other / technical failure",
  short: "Technical payment failure",
  fix: "Ask the district nodal officer.",
};

/** Decode a PFMS/NPCI code to plain words. Never returns empty strings. */
export function failureWords(code: string | null | undefined): FailureWords {
  const key = (code ?? "").trim();
  return tokens.failureFixes[key] ?? tokens.failureFixes["UNKNOWN"] ?? FALLBACK_FAILURE;
}

export function plainTerm(term: string): string {
  return tokens.vocabulary[term] ?? term;
}
