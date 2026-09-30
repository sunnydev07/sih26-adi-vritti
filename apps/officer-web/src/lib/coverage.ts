/**
 * Pure coverage presentation helpers. They compute from whatever
 * CoverageRegion the API seam returns — no mock data lives here, so screens
 * may import this module directly (unlike `@/__mocks__/*`, which only the
 * `src/lib/api.ts` seam may touch).
 */
import type { CoverageRegion } from "@/types";

export function coveragePct(r: CoverageRegion): number {
  if (r.totalStudents === 0) return 0;
  return Math.round((r.applications / r.totalStudents) * 100);
}

export function coverageColor(pct: number): string {
  if (pct < 20) return "#f43f5e";
  if (pct < 60) return "#f59e0b";
  return "#10b981";
}
