/**
 * API client wrapper. Today it serves typed `__mocks__` data with the exact
 * shapes the OpenAPI contract will return; swap each function body for the
 * auto-generated client from `packages/api-client/` once the backend is live.
 * No hardcoded demo data lives in components — everything flows through here.
 */
import {
  dashboardMetrics,
  disbursementRecords,
  disbursementSummary,
  exceptionQueue,
  failureBreakdown,
  recentExceptionsPreview,
} from "@/__mocks__/data";
import { coverageBlocks, coverageDistricts, coverageSchools, coverageStates, identityQueue, outreachList } from "@/__mocks__/geo-identity";

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export interface JevStpResult {
  autoApproveSafe: boolean;
  probability: number;
  riskLevel: number;
  routing: string;
  latencyMs: number;
  provider: string;
}

export const api = {
  async getDashboardMetrics() {
    await delay(250);
    return dashboardMetrics;
  },
  async getRecentExceptions() {
    await delay(200);
    return recentExceptionsPreview;
  },
  async getExceptionQueue() {
    await delay(300);
    return exceptionQueue;
  },
  async evaluateStpWithJev(item: { scheme: string; stpScore: number; riskScore: number; slaElapsedDays: number; slaLimitDays: number; claims: { status: string }[] }): Promise<JevStpResult> {
    const isClean = item.claims.every((c) => c.status === "gov-verified" || c.status === "corroborated");
    try {
      // Same-origin BFF proxy (src/app/api/decisions/stp/route.ts) holds the
      // service token server-side, so the browser bundle never sees it.
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2500);
      const res = await fetch("/api/decisions/stp", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          application: {
            scheme: item.scheme,
            all_claims_verified: isClean,
            income_below_ceiling: true,
            doc_confidence_avg: item.stpScore / 100,
            open_deficiencies: item.claims.filter((c) => c.status === "pending-review" || c.status === "expired").length,
            is_duplicate: false,
            days_elapsed: item.slaElapsedDays,
            sla_breached: item.slaElapsedDays > item.slaLimitDays,
          },
        }),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      if (res.ok) {
        const data = await res.json();
        return {
          autoApproveSafe: data.auto_approve_safe,
          probability: Math.round(data.probability * 100),
          riskLevel: data.risk_level,
          routing: data.routing,
          latencyMs: data.latency_ms || 120,
          provider: "JEV 1.13 Free (Live)",
        };
      }
    } catch {
      // offline fallback
    }

    await delay(180);
    const safe = item.stpScore >= 85 && isClean;
    return {
      autoApproveSafe: safe,
      probability: item.stpScore,
      riskLevel: safe ? 1 : item.riskScore > 50 ? 4 : 2,
      routing: safe ? "auto_approve" : "senior_officer_review",
      latencyMs: 85,
      provider: "JEV Rules (Deterministic Engine)",
    };
  },
  async getCoverageChildren(parentId: string | null) {
    await delay(200);
    if (parentId === null) return coverageStates;
    const districts = coverageDistricts.filter((d) => d.parentId === parentId);
    if (districts.length > 0) return districts;
    const blocks = coverageBlocks.filter((b) => b.parentId === parentId);
    if (blocks.length > 0) return blocks;
    return coverageSchools.filter((s) => s.parentId === parentId);
  },
  async getOutreachList() {
    await delay(200);
    return outreachList;
  },
  async getIdentityQueue() {
    await delay(250);
    return identityQueue;
  },
  async getDisbursements() {
    await delay(250);
    return { summary: disbursementSummary, breakdown: failureBreakdown, records: disbursementRecords };
  },
};
