/**
 * SERVER-ONLY helper for BFF route handlers (src/app/api/*).
 *
 * NEVER import this module from client components or client libraries: it
 * reads AI_SERVICE_TOKEN, which must never enter the browser bundle. Route
 * handlers run on the server, so the token stays out of shipped JS.
 */

/**
 * Budget for one upstream call, in milliseconds.
 *
 * This must stay **greater** than the AI service's own upstream timeouts, or the
 * BFF gives up first and orphans the call it started: JEV (3.0s, see
 * `services/ai/app/services/jev_service.py`) and Groq (10.0s, see
 * `groq_service.py`) keep running to completion, the tokens are spent, and the
 * answer is thrown away when nobody is waiting for it. It was 2500ms — shorter
 * than JEV's 3s — so *every* slow JEV call was paid for twice: once upstream and
 * once as a wasted wait here.
 *
 * Groq's 10s budget does not fit under any browser-friendly BFF timeout, so the
 * BFF deliberately lets Groq-backed routes run long. A request the operator can
 * see still being processed beats a fast 502 that hides a paid-for call; the
 * browser aborts first and the upstream is simply abandoned, which is the
 * cheaper of the two failures and is why the 8s figure sits where it does.
 *
 * Overridable so a deployment with a different AI timeout can move the whole
 * chain in one place instead of re-tuning three.
 */
const UPSTREAM_TIMEOUT_MS = Number.parseInt(
  process.env.AI_UPSTREAM_TIMEOUT_MS ?? "8000",
  10,
);

/** Base URL of the AI service. Server-side default keeps local demo working. */
export function aiServiceBase(): string {
  const base = (process.env.AI_SERVICE_URL ?? "http://localhost:8000").trim();
  return base.replace(/\/+$/, "");
}

/** Shared service token, or null when the deployment did not configure one. */
export function aiServiceToken(): string | null {
  const token = (process.env.AI_SERVICE_TOKEN ?? "").trim();
  return token.length > 0 ? token : null;
}

/**
 * POST JSON to the AI service with a bounded timeout.
 * Throws when the token is unconfigured or the network/timeout fails, so
 * callers can translate everything into a 502 the client falls back from.
 */
export async function postUpstream(path: string, payload: unknown): Promise<Response> {
  const token = aiServiceToken();
  if (!token) {
    throw new Error("AI_SERVICE_TOKEN is not configured");
  }
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  try {
    return await fetch(`${aiServiceBase()}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-ai-service-token": token,
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
      cache: "no-store",
    });
  } finally {
    clearTimeout(timeoutId);
  }
}
