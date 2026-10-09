/**
 * BFF proxy for the Ask Adi help lane.
 *
 * The browser calls same-origin `/api/jago/help`; this handler attaches the
 * server-side AI_SERVICE_TOKEN and forwards to the AI service. 502 responses
 * tell the client to use its offline mirror (the judge demo runs WIFI OFF).
 *
 * 401 unless the caller holds a verified console session: proxy.ts only guards
 * /dashboard/*, so this handler checks the session itself — otherwise anyone
 * could burn Groq quota anonymously. (The 500-char question cap below is also
 * the body-size bound, so no separate 413 is needed on this route.)
 */
import { NextResponse, type NextRequest } from "next/server";
import { postUpstream } from "@/lib/ai-upstream";
import { verifyRequestSession } from "@/lib/session";

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
