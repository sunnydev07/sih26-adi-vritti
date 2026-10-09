/**
 * Demo session — the ONLY gate between a cold start (or a deep link) and the app.
 *
 * Today there is no credential: no OTP is sent, no token is minted, and login
 * is a guarded tap. This module makes that explicit instead of navigational —
 * screens and deep links check for a session object rather than assuming the
 * user arrived through the login button, so an unauthenticated entry lands on
 * `/login` instead of inside someone's file.
 *
 * Deliberately in-memory: nothing is persisted, so killing the process signs
 * out (fail-closed). Persisting a token belongs here when a real OTP/session
 * endpoint ships (SecureStore for the token, with `usid` + `exp` parsing and
 * `Authorization: Bearer` injection in one API client) — until then the
 * honest "Demo preview" copy on every screen is what makes this acceptable.
 */
import { useSyncExternalStore } from "react";

export interface DemoSession {
  demo: true;
  /** Caller-supplied phone digits. Identity claim, not a credential. */
  phone: string;
  startedAt: string;
}

let current: DemoSession | null = null;
const listeners = new Set<() => void>();

function emit(): void {
  listeners.forEach((l) => l());
}

function subscribe(notify: () => void): () => void {
  listeners.add(notify);
  return () => {
    listeners.delete(notify);
  };
}

function snapshot(): DemoSession | null {
  return current;
}

export function currentSession(): DemoSession | null {
  return current;
}

/** Mint an explicitly-demo session after the guarded login tap. */
export function signInDemo(phone: string): DemoSession {
  current = { demo: true, phone, startedAt: new Date().toISOString() };
  emit();
  return current;
}

/** Clear the session. Client-side only — there is no server session to revoke yet. */
export function signOut(): void {
  current = null;
  emit();
}

/** Reactive session for gates and guards. */
export function useSession(): DemoSession | null {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
