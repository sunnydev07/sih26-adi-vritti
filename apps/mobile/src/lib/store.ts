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
 * bundled mock fallback; the in-memory `enqueueOutbox` stages entries that die
 * with the JS context, and `deltaSync` is a no-op returning `{ pushed: 0 }`
 * until the API client is wired in. The DURABLE outbox below
 * (`enqueueOutboxPersistent` / `pushOutbox`, expo-sqlite with a memory
 * fallback) survives restarts and replays FIFO with per-entry idempotency
 * keys — but no sender is wired yet, so replay stays dormant until the API
 * client lands. Do NOT demo offline sync until that sender exists and this
 * file grows a test.
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
 * Collision-free ids for outbox entries, chat messages, and idempotency keys.
 *
 * `Date.now()` alone is not an id: it has millisecond resolution, and two taps
 * inside the same millisecond produced the same string. Keyed collections
 * silently overwrote the first entry. `randomUUID` when the runtime has it,
 * with a counter+random fallback for the runtimes that do not.
 */
let idSequence = 0;

export function newId(prefix: string): string {
  const globalCrypto = globalThis.crypto;
  if (globalCrypto && typeof globalCrypto.randomUUID === "function") {
    return `${prefix}-${globalCrypto.randomUUID()}`;
  }
  idSequence += 1;
  const random = Math.random().toString(36).slice(2, 10);
  return `${prefix}-${Date.now()}-${idSequence}-${random}`;
}

function nextOutboxId(): string {
  return newId("obx");
}

export function enqueueOutbox(entries: OutboxEntry[], kind: OutboxEntry["kind"], payload: Record<string, string>): OutboxEntry[] {
  const entry: OutboxEntry = {
    id: nextOutboxId(),
    kind,
    payload,
    createdAt: new Date().toISOString(),
    idempotencyKey: newId("idem"),
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

/* ------------------------------------------------------------------ */
/* Persistent outbox (expo-sqlite). Queued work must survive a restart:  */
/* in-memory entries died with the JS context, so two refetches queued   */
/* offline were gone after a relaunch. Rows carry their idempotency key,  */
/* minted once at enqueue and replayed verbatim, so a retry after a lost  */
/* response resumes the same operation instead of duplicating it. No      */
/* sender is wired yet — `pushOutbox` takes one — so replay stays dormant */
/* until the API client lands.                                            */
/* ------------------------------------------------------------------ */

import * as SQLite from "expo-sqlite";

const OUTBOX_DB = "adivritti.db";

let outboxDb: SQLite.SQLiteDatabase | null = null;
let memoryOutbox: OutboxEntry[] = [];

function openOutboxDb(): SQLite.SQLiteDatabase | null {
  if (outboxDb) return outboxDb;
  try {
    const db = SQLite.openDatabaseSync(OUTBOX_DB);
    db.execSync(
      "CREATE TABLE IF NOT EXISTS outbox (" +
        "id TEXT PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL, " +
        "idempotency_key TEXT NOT NULL, created_at TEXT NOT NULL)",
    );
    outboxDb = db;
    return db;
  } catch {
    // Web preview and other runtimes without SQLite: memory only, same as before.
    return null;
  }
}

function rowToEntry(row: {
  id: string;
  kind: string;
  payload: string;
  idempotency_key: string;
  created_at: string;
}): OutboxEntry {
  let payload: Record<string, string> = {};
  try {
    const parsed: unknown = JSON.parse(row.payload);
    if (parsed && typeof parsed === "object") payload = parsed as Record<string, string>;
  } catch {
    payload = {};
  }
  return {
    id: row.id,
    kind: (row.kind === "message" || row.kind === "submission" || row.kind === "consent"
      ? row.kind
      : "submission") as OutboxEntry["kind"],
    payload,
    createdAt: row.created_at,
    idempotencyKey: row.idempotency_key,
  };
}

/** Enqueue durably: SQLite when available, memory fallback otherwise. */
export function enqueueOutboxPersistent(
  kind: OutboxEntry["kind"],
  payload: Record<string, string>,
): OutboxEntry {
  const entry: OutboxEntry = {
    id: nextOutboxId(),
    kind,
    payload,
    createdAt: new Date().toISOString(),
    idempotencyKey: newId("idem"),
  };
  const db = openOutboxDb();
  if (db) {
    try {
      db.runSync(
        "INSERT INTO outbox (id, kind, payload, idempotency_key, created_at) VALUES (?, ?, ?, ?, ?)",
        entry.id,
        entry.kind,
        JSON.stringify(payload),
        entry.idempotencyKey,
        entry.createdAt,
      );
      return entry;
    } catch {
      // Full disk, locked DB, corruption: keep the entry in memory rather
      // than losing the user's queued action.
    }
  }
  memoryOutbox = [...memoryOutbox, entry];
  return entry;
}

/** Durable queue contents, oldest first. */
export function listOutboxPersistent(): OutboxEntry[] {
  const db = openOutboxDb();
  if (db) {
    try {
      return db
        .getAllSync<{
          id: string;
          kind: string;
          payload: string;
          idempotency_key: string;
          created_at: string;
        }>("SELECT id, kind, payload, idempotency_key, created_at FROM outbox ORDER BY created_at ASC, id ASC")
        .map(rowToEntry);
    } catch {
      // Fall through to memory.
    }
  }
  return [...memoryOutbox];
}

function forgetOutboxEntry(id: string): void {
  memoryOutbox = memoryOutbox.filter((e) => e.id !== id);
  const db = openOutboxDb();
  if (db) {
    try {
      db.runSync("DELETE FROM outbox WHERE id = ?", id);
    } catch {
      // The row stays and will be retried; duplication is prevented by the
      // idempotency key, not by the delete succeeding.
    }
  }
}

/**
 * Replay the durable queue FIFO. Stops at the first unacknowledged entry so
 * order is preserved; acknowledged rows are deleted. Returns how many the
 * sender accepted. Pass the real API sender when it exists.
 */
export async function pushOutbox(
  sender: (entry: OutboxEntry) => Promise<boolean>,
): Promise<{ pushed: number }> {
  let pushed = 0;
  for (const entry of listOutboxPersistent()) {
    let ok = false;
    try {
      ok = await sender(entry);
    } catch {
      ok = false;
    }
    if (!ok) break;
    forgetOutboxEntry(entry.id);
    pushed += 1;
  }
  return { pushed };
}
