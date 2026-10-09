import { NextResponse, type NextRequest } from "next/server";
import {
  SESSION_COOKIE,
  SESSION_TTL_SECONDS,
  mintSession,
  sessionSecret,
  usingDevSecret,
} from "@/lib/session";

/**
 * Officer sign-in.
 *
 * The login form used to call `router.push("/dashboard")` straight from a client-side
 * OTP length check — it never obtained a credential, so the "sign-in" was a UI
 * animation in front of a public console. The next version fixed the guard but left
 * the minting weak: it wrote the cookie value `demo:${phone}` verbatim, and the
 * guard only checked that *some* value was present. Anyone could set
 * `adivritti_session=anything` in devtools, and the public deployment minted one on
 * demand.
 *
 * This route now mints a signed HS256 session (`src/lib/session.ts`) and
 * `src/proxy.ts` verifies that signature and its expiry. A hand-set cookie is a
 * redirect to `/login` again.
 *
 * The correct handoff is still to exchange the officer's phone + OTP for a
 * credential at Core. **Core has no officer login endpoint**:
 * `docs/openapi/core.yaml` documents 13 paths and none of them is one. So this route
 * cannot mint a real credential, and it does not pretend to.
 *
 * Three outcomes, all explicit:
 *   - Core answers 2xx on /v1/auth/officer/login -> sign the returned token into a
 *     console session.
 *   - development (non-production)               -> sign a clearly-marked demo session.
 *   - production without Core                     -> 501, and the UI says so.
 *
 * Demo is ON by default in development: localhost dev is a trusted loop, the
 * dev Core profile already permits unauthenticated /v1 access, and requiring
 * an env var first stranded every new developer at the OTP screen (nothing
 * sends an SMS — there is no SMS integration). Set OFFICER_CONSOLE_DEMO=0 to
 * opt out. Production always refuses: a public URL must never mint a cookie
 * anyone can request.
 *
 * The demo session authorises nothing beyond this Next app: the BFF still calls Core
 * with its own server-side credential, and Core still decides what that credential
 * may do. It is signed with a published development secret, so it is a session —
 * not authentication — and `src/proxy.ts` says so in its own javadoc.
 */

function coreBase(): string {
  return (process.env.CORE_URL ?? "http://localhost:8080").trim().replace(/\/+$/, "");
}

function demoEnabled(): boolean {
  return process.env.NODE_ENV !== "production"
    && process.env.OFFICER_CONSOLE_DEMO !== "0";
}

/** Preflight for the login page: is one-click demo entry available? */
export async function GET() {
  return NextResponse.json({
    demo: demoEnabled(),
    // The login page needs to know whether signing is possible at all, so it can
    // say "this deployment has no session secret" instead of accepting an OTP
    // and then silently failing to set a cookie.
    signable: sessionSecret(process.env) !== null,
    usingDevSecret: usingDevSecret(process.env),
  });
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
  if (
    phone.replace(/\D/g, "").length < 10
    || otp.length < 4
    || phone.length > 32
    || otp.length > 64
  ) {
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
    if (!payload?.token) {
      return NextResponse.json(
        { error_code: "INTERNAL_ERROR", message: "Core returned no session token" },
        { status: 502 },
      );
    }
    // The Core bearer token is deliberately NOT stored or echoed: the console
    // session carries only a stable, non-credential subject, and the BFF calls
    // Core with its own server-side credential. Returning the token here would
    // hand it to page JS, extensions and log scrapers on every officer login.
    return withSession(`officer:${phone.replace(/\D/g, "")}`, false);
  }

  if (coreResponse && coreResponse.status !== 404) {
    return NextResponse.json(
      { error_code: "AUTHENTICATION_FAILED", message: "Core rejected those credentials" },
      { status: 401 },
    );
  }

  if (demoEnabled()) {
    return withSession(`demo:${phone}`, true);
  }

  return NextResponse.json(
    {
      error_code: "LOGIN_NOT_WIRED",
      message:
        "Officer sign-in is not wired in this build: Core exposes no /v1/auth/officer/login.",
      detail:
        "Run the console in development for demo access, or add the Core login endpoint.",
    },
    { status: 501 },
  );
}

/**
 * Sign a console session and set it. Returns a 503 rather than an unsigned cookie
 * when there is no usable secret: a cookie the guard will reject is worse than an
 * error, because the login page would report success and the next navigation would
 * bounce back to `/login` with nothing to show for it.
 */
async function withSession(subject: string, demo: boolean): Promise<NextResponse> {
  const secret = sessionSecret(process.env);
  if (!secret) {
    return NextResponse.json(
      {
        error_code: "SESSION_NOT_CONFIGURED",
        message: "This deployment has no OFFICER_SESSION_SECRET, so no session can be "
          + "issued. Set it to a long random value and restart.",
      },
      { status: 503 },
    );
  }
  const token = await mintSession(subject, demo, secret);
  if (!token) {
    return NextResponse.json(
      { error_code: "SESSION_NOT_CONFIGURED", message: "Could not sign a session." },
      { status: 503 },
    );
  }

  const res = NextResponse.json({ ok: true, demo }, { status: 200 });
  res.cookies.set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: SESSION_TTL_SECONDS,
  });
  return res;
}

/**
 * Sign-out. The session cookie is httpOnly, so client JS cannot delete it —
 * only a server Set-Cookie can. Without this route an 8-hour session on a
 * shared kiosk stayed usable until expiry no matter what the UI did.
 */
export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.set(SESSION_COOKIE, "", {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 0,
  });
  return res;
}
