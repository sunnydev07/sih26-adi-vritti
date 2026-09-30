/**
 * SERVER-ONLY helper for BFF route handlers (src/app/api/*).
 *
 * NEVER import this module from client components or client libraries: it
 * reads AI_SERVICE_TOKEN, which must never enter the browser bundle. Route
 * handlers run on the server, so the token stays out of shipped JS.
 */

const UPSTREAM_TIMEOUT_MS = 2500;

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
