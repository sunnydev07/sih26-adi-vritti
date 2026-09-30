/**
 * Plain-language helpers for the officer console.
 * Same shared tokens as the mobile app — one vocabulary everywhere.
 * Thresholds mirror slaStatus() in ./utils and FailureDecoder.java.
 */
import rawTokens from "@adi-vritti/ui-tokens";

export type PlainTone = "ok" | "warn" | "bad" | "info";

/* JSON imports infer literal object types with no index signature —
   view the tables as Records at the boundary. */
const tokens = rawTokens as unknown as {
  sla: { atRiskWindowDays: number; words: { ok: string; risk: string; breached: string } };
  claimStatus: Record<string, { tone: PlainTone; words: string; detail: string }>;
  stp: {
    autoApproveThreshold: number;
    reviewThreshold: number;
    words: { ready: string; review: string; manual: string };
  };
  failureFixes: Record<string, { cause: string; short: string; fix: string }>;
  vocabulary: Record<string, string>;
  assistant: { name: string; suggestions: string[] };
};

export function plainSla(
  elapsed: number,
  limit: number
): { tone: PlainTone; headline: string; detail: string } {
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

export function plainClaimStatus(status: string): {
  tone: PlainTone;
  words: string;
  detail: string;
} {
  const entry = tokens.claimStatus[status];
  if (!entry) return { tone: "info", words: status, detail: "" };
  return { tone: entry.tone, words: entry.words, detail: entry.detail };
}

export function plainStp(score: number): { tone: PlainTone; words: string } {
  if (score >= tokens.stp.autoApproveThreshold) {
    return { tone: "ok", words: tokens.stp.words.ready };
  }
  if (score >= tokens.stp.reviewThreshold) {
    return { tone: "warn", words: tokens.stp.words.review };
  }
  return { tone: "bad", words: tokens.stp.words.manual };
}

export function plainFailure(code: string | null | undefined): {
  cause: string;
  short: string;
  fix: string;
} {
  const key = (code ?? "").trim();
  return (
    tokens.failureFixes[key] ??
    tokens.failureFixes["UNKNOWN"] ?? {
      cause: "Other / technical failure",
      short: "Technical payment failure",
      fix: "Ask the district nodal officer.",
    }
  );
}

export function plainTerm(term: string): string {
  return tokens.vocabulary[term] ?? term;
}

export const assistantSuggestions: string[] = tokens.assistant.suggestions;
