import { NextResponse, type NextRequest } from "next/server";

/**
 * Route guard for the officer console.
 *
 * Before this file, `/dashboard/*` was a public page: the login page's OTP check is a
 * client-side animation that never obtained a credential, and nothing server-side
 * checked one, so anyone with the URL could read the console — which is what the
 * README advertises as a deployment.
 *
 * Scope, stated plainly: this is a PRESENCE check on a session cookie, not
 * authentication. A determined caller can set the cookie themselves. Closing that
 * properly needs a real IdP (Core already accepts `JWT_JWK_SET_URI`) or a signed
 * session, and Core has no officer login endpoint to exchange credentials against —
 * see `src/app/api/session/route.ts`. The authority boundary is Core, which
 * re-checks the credential on every API call; this only stops the console being an
 * unauthenticated public page.
 *
 * Next 16 renamed the `middleware` file convention to `proxy` (middleware.ts is
 * deprecated), and the exported function must be named `proxy`.
 */
export function proxy(request: NextRequest) {
  const session = request.cookies.get(SESSION_COOKIE);
  if (session?.value) return NextResponse.next();

  const url = request.nextUrl.clone();
  url.pathname = "/login";
  url.search = "";
  return NextResponse.redirect(url);
}

/** Shared with `src/app/api/session/route.ts`; kept here to avoid a server-only import. */
export const SESSION_COOKIE = "adivritti_session";

export const config = {
  matcher: ["/dashboard/:path*"],
};