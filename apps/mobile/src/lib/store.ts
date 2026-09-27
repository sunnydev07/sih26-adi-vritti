/**
 * Offline-first store shapes. SQLite (Drizzle) is the source of truth for
 * reads; writes go to the outbox and delta-sync on reconnect. On-device OCR
 * and the signed status cache work with zero network.
 */
import type { OutboxEntry } from "@/types";

export const SCHEMA_VERSION = 1;

export const TABLES = {
  dashboard: "dashboard_cache",
  claims: "claims_cache",
  messages: "messages_cache",
  outbox: "outbox",
} as const;

export function enqueueOutbox(entries: OutboxEntry[], kind: OutboxEntry["kind"], payload: Record<string, string>): OutboxEntry[] {
  const entry: OutboxEntry = {
    id: `obx-${Date.now()}`,
    kind,
    payload,
    createdAt: new Date().toISOString(),
  };
  return [...entries, entry];
}

/** Delta sync: push outbox FIFO, then pull server changes since `since`. */
export async function deltaSync(outbox: OutboxEntry[], since: string): Promise<{ pushed: number; since: string }> {
  // Wired to the real API client in integration phase; mock keeps ordering.
  return { pushed: outbox.length, since };
}
