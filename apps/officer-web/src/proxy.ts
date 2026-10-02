import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE, sessionSecret, verifySession } from "@/lib/session";

/**
 * Route guard for the officer console.
 *
 * Before this file, `/dashboard/*` was a public page: the login page's OTP check is a
 * client-side animation that never obtained a credential, and nothing server-side
 * checked one, so anyone with the URL could read the console.
 *
 * It was then downgraded to a presence check on the session cookie, which is not
 * authentication either: `cookie.value` existing says nothing about who set it, so
 * `adivritti_session=anything` typed into devtools still reached the console. The
 * guard now *verifies* the cookie — HS256 signature and expiry, via
 * `verifySession` — and anything that does not verify is a redirect to `/login`.
 *
 * Scope, stated plainly: this is a signed console session, not an officer
 * credential. It stops the console being an unauthenticated public page and stops a
 * cookie from being forged by hand. It is not a substitute for an IdP: Core accepts
 * `JWT_JWK_SET_URI` and re-authorises every API call with its own server-side
 * credential, and this session grants nothing there.
 *
 * Next 16 renamed the `middleware` file convention to `proxy` (middleware.ts is
 * deprecated), and the exported function must be named `proxy`.
 */
export async function proxy(request: NextRequest) {
  const session = request.cookies.get(SESSION_COOKIE);
  const secret = sessionSecret(process.env);

  if (secret) {
    const claims = await verifySession(session?.value, secret);
    if (claims) return NextResponse.next();
  }

  const url = request.nextUrl.clone();
  url.pathname = "/login";
  url.search = "";
  const response = NextResponse.redirect(url);
  // Do not let a cached page for the guarded route be served after a logout or
  // an expiry.
  response.headers.set("Cache-Control", "no-store");
  return response;
}

export const config = {
  matcher: ["/dashboard/:path*"],
};
