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
