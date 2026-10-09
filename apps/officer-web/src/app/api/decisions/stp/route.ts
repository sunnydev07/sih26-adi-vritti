/**
 * BFF proxy for the JEV STP decision lane.
 *
 * The browser calls same-origin `/api/decisions/stp`; this handler attaches
 * the server-side AI_SERVICE_TOKEN and forwards to the AI service. 502
 * responses tell the client to use its deterministic rules fallback.
 *
 * Two guards before any money is spent upstream:
 *   - 401 unless the caller holds a verified console session (proxy.ts only
 *     guards /dashboard/*, so this handler checks the session itself —
 *     otherwise anyone could burn JEV/Groq quota anonymously);
 *   - 413 on oversized or deeply-nested bodies (the AI schemas accept
 *     unbounded dicts, so the BFF is the cost/DoS boundary).
 */
import { NextResponse, type NextRequest } from "next/server";
import { postUpstream } from "@/lib/ai-upstream";
import { verifyRequestSession } from "@/lib/session";

/** Largest forwarded body: STP inputs are small flat facts, not documents. */
const MAX_BFF_BODY_BYTES = 8 * 1024;

/**
 * The AI service accepts unbounded dicts, so an unbounded forward is a token-
 * cost amplifier. Allow only small JSON values: bounded keys, shallow nesting,
 * short strings.
 */
function isForwardable(value: unknown, depth: number): boolean {
  if (depth > 3) return false;
  if (typeof value === "string") return value.length <= 1024;
  if (typeof value === "number" || typeof value === "boolean" || value === null) {
    return true;
  }
  if (Array.isArray(value)) {
    return value.length <= 64 && value.every((v) => isForwardable(v, depth + 1));
  }
  if (typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    return (
      entries.length <= 64
      && entries.every(([k, v]) => k.length <= 128 && isForwardable(v, depth + 1))
    );
  }
  return false;
}

export async function POST(request: NextRequest) {
  if (!(await verifyRequestSession(request, process.env))) {
    return NextResponse.json({ error: "unauthenticated" }, { status: 401 });
  }
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  const { application } = (body ?? {}) as { application?: unknown };
  if (typeof application !== "object" || application === null) {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  if (
    JSON.stringify(body ?? {}).length > MAX_BFF_BODY_BYTES
    || !isForwardable(application, 0)
  ) {
    return NextResponse.json({ error: "payload_too_large" }, { status: 413 });
  }
  try {
    const upstream = await postUpstream("/decisions/stp", { application });
    let data: unknown = null;
    try {
      data = await upstream.json();
    } catch {
      data = null;
    }
    if (!upstream.ok || data == null) {
      return NextResponse.json({ error: "ai_unavailable" }, { status: 502 });
    }
    return NextResponse.json(data);
  } catch {
    return NextResponse.json({ error: "ai_unavailable" }, { status: 502 });
  }
}
