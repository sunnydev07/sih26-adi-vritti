/**
 * BFF proxy for the JEV STP decision lane.
 *
 * The browser calls same-origin `/api/decisions/stp`; this handler attaches
 * the server-side AI_SERVICE_TOKEN and forwards to the AI service. 502
 * responses tell the client to use its deterministic rules fallback.
 */
import { NextResponse } from "next/server";
import { postUpstream } from "@/lib/ai-upstream";

export async function POST(request: Request) {
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
