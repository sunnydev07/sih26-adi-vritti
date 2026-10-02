/**
 * Offline-first store shapes (NOT YET WIRED -- see below).
 *
 * The previous header claimed "SQLite (Drizzle) is the source of truth for
 * reads; writes go to the outbox and delta-sync on reconnect", which was
 * fiction: `deltaSync` returned `{ pushed: outbox.length }` without pushing
 * anything, and no screen reads from SQLite. That lie is worse than a gap,
 * because a reviewer testing airplane mode would conclude sync works.
 *
 * Current truth: screens render from the API client (`lib/jago.ts`) with the
 * bundled mock fallback; `enqueueOutbox` only stages entries in memory and
 * `deltaSync` is a no-op returning `{ pushed: 0 }` until the API client is
 * wired in. Wiring real persistence (expo-sqlite +
 * drizzle-orm are kept in package.json for exactly that) is tracked
 * follow-up work -- do NOT demo offline sync until this file grows a test.
 */
import type { OutboxEntry } from "@/types";

export const SCHEMA_VERSION = 1;

export const TABLES = {
  dashboard: "dashboard_cache",
  claims: "claims_cache",
  messages: "messages_cache",
  outbox: "outbox",
} as const;

/**
 * Outbox entry id.
 *
 * `Date.now()` alone is not an id: it has millisecond resolution, and two taps on
 * "upload document" inside the same millisecond produced the same string. The
 * outbox is keyed by id, so a collision silently overwrites the first entry —
 * the queued action is gone with nothing to indicate it. `randomUUID` when the
 * runtime has it, with a counter+random fallback for the runtimes that do not.
 */
let outboxSequence = 0;

function nextOutboxId(): string {
  const globalCrypto = globalThis.crypto;
  if (globalCrypto && typeof globalCrypto.randomUUID === "function") {
    return `obx-${globalCrypto.randomUUID()}`;
  }
  outboxSequence += 1;
  const random = Math.random().toString(36).slice(2, 10);
  return `obx-${Date.now()}-${outboxSequence}-${random}`;
}

export function enqueueOutbox(entries: OutboxEntry[], kind: OutboxEntry["kind"], payload: Record<string, string>): OutboxEntry[] {
  const entry: OutboxEntry = {
    id: nextOutboxId(),
    kind,
    payload,
    createdAt: new Date().toISOString(),
  };
  return [...entries, entry];
}

/** Delta sync: push outbox FIFO, then pull server changes since `since`.
 * STUB -- see the file header. Deliberately pushes nothing (returns 0) rather
 * than reporting `outbox.length` as pushed, which would let a caller believe
 * queued entries reached the server. */
export async function deltaSync(outbox: OutboxEntry[], since: string): Promise<{ pushed: number; since: string }> {
  void outbox;
  // Wired to the real API client in integration phase; mock keeps ordering.
  return { pushed: 0, since };
}
