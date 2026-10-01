import { NextResponse, type NextRequest } from "next/server";

/**
 * Officer sign-in.
 *
 * The login form used to call `router.push("/dashboard")` straight from a client-side
 * OTP length check — it never obtained a credential, so the "sign-in" was a UI
 * animation in front of a public console.
 *
 * The correct handoff is to exchange the officer's phone + OTP for a credential at
 * Core. **Core has no officer login endpoint**: `docs/openapi/core.yaml` documents 13
 * paths and none of them is one. So this route cannot mint a real session, and it does
 * not pretend to — minting a cookie anyone can request would restore exactly the hole
 * the guard closes, with the added problem of looking like real authentication.
 *
 * Three outcomes, all explicit:
 *   - Core answers 2xx on /v1/auth/officer/login -> set an httpOnly session cookie.
 *   - `OFFICER_CONSOLE_DEMO=1`                  -> set a clearly-marked demo cookie.
 *   - otherwise                                 -> 501, and the UI says so.
 *
 * The demo cookie is opt-in, is refused in production, and authorises nothing beyond
 * this Next app: the BFF still calls Core with its own server-side credential, and Core
 * still decides what that credential may do.
 */

const SESSION_COOKIE = "adivritti_session";

function coreBase(): string {
  return (process.env.CORE_URL ?? "http://localhost:8080").trim().replace(/\/+$/, "");
}

function demoEnabled(): boolean {
  return process.env.OFFICER_CONSOLE_DEMO === "1"
    && process.env.NODE_ENV !== "production";
}

export async function POST(request: NextRequest) {
  let body: { phone?: unknown; otp?: unknown };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return NextResponse.json(
      { error_code: "MALFORMED_REQUEST", message: "phone and otp are required" },
      { status: 400 },
    );
  }

  const phone = typeof body.phone === "string" ? body.phone.trim() : "";
  const otp = typeof body.otp === "string" ? body.otp.trim() : "";
  if (phone.replace(/\D/g, "").length < 10 || otp.length < 4) {
    return NextResponse.json(
      { error_code: "VALIDATION_FAILED", message: "Enter a 10-digit number and a 4+ digit OTP" },
      { status: 400 },
    );
  }

  // Preferred path: exchange the credentials at Core. When Core is running it either
  // authenticates or refuses; a 404 means the endpoint does not exist yet.
  let coreResponse: Response | null = null;
  try {
    coreResponse = await fetch(`${coreBase()}/v1/auth/officer/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ phone, otp }),
      cache: "no-store",
      signal: AbortSignal.timeout(4000),
    });
  } catch {
    coreResponse = null;
  }

  if (coreResponse && coreResponse.ok) {
    const payload = (await coreResponse.json().catch(() => null)) as { token?: string } | null;
    const token = payload?.token;
    if (!token) {
      return NextResponse.json(
        { error_code: "INTERNAL_ERROR", message: "Core returned no session token" },
        { status: 502 },
      );
    }
    return withSession({ token, demo: false });
  }

  if (coreResponse && coreResponse.status !== 404) {
    return NextResponse.json(
      { error_code: "AUTHENTICATION_FAILED", message: "Core rejected those credentials" },
      { status: 401 },
    );
  }

  if (demoEnabled()) {
    return withSession({ token: `demo:${phone}`, demo: true });
  }

  return NextResponse.json(
    {
      error_code: "LOGIN_NOT_WIRED",
      message:
        "Officer sign-in is not wired in this build: Core exposes no /v1/auth/officer/login.",
      detail:
        "Set OFFICER_CONSOLE_DEMO=1 to enter the console with demo data, or add the Core login endpoint.",
    },
    { status: 501 },
  );
}

function withSession(value: { token: string; demo: boolean }): NextResponse {
  const res = NextResponse.json(
    { ok: true, demo: value.demo },
    { status: 200 },
  );
  res.cookies.set(SESSION_COOKIE, value.token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60 * 8,
  });
  return res;
}