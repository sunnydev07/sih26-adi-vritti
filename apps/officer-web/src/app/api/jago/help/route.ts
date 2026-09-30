/**
 * BFF proxy for the Ask Adi help lane.
 *
 * The browser calls same-origin `/api/jago/help`; this handler attaches the
 * server-side AI_SERVICE_TOKEN and forwards to the AI service. 502 responses
 * tell the client to use its offline mirror (the judge demo runs WIFI OFF).
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
  const { question, lang } = (body ?? {}) as { question?: unknown; lang?: unknown };
  if (typeof question !== "string" || question.trim().length === 0 || question.length > 500) {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  if (lang !== "hi" && lang !== "en") {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  try {
    const upstream = await postUpstream("/jago/help", {
      question: question.slice(0, 500),
      lang,
    });
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
