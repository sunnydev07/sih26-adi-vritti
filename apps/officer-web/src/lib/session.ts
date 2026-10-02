/**
 * Signed officer-console session.
 *
 * ## Why this exists
 *
 * The console guard used to be a presence check: `proxy.ts` asked whether a cookie
 * named `adivritti_session` existed and let the request through if it did. The
 * login route minted the cookie value `demo:${phone}` for anyone who typed ten
 * digits and four characters, with no OTP ever sent. Setting the cookie by hand
 * in devtools therefore reached `/dashboard` — on the public Vercel URL too, since
 * `POST /api/session` minted it whenever `NODE_ENV !== "production"`.
 *
 * ## What this is
 *
 * A compact HS256 JWT, signed with `OFFICER_SESSION_SECRET`, carrying an 8-hour
 * expiry. The guard now verifies the signature and the expiry instead of the
 * presence, so a tampered or expired cookie is a redirect to `/login` rather than
 * a page. This is a *console* session, not an officer credential: Core still
 * authorises every API call with its own server-side credential, and the claims
 * here decide nothing about that.
 *
 * ## Runtime note
 *
 * Signing uses `globalThis.crypto.subtle` rather than `node:crypto` on purpose.
 * Next 16's `proxy.ts` may run on either runtime, and WebCrypto is present in
 * both, so the guard works without a `runtime` export that would pin it to one.
 */

/** Cookie name shared with `src/proxy.ts` and the login route. */
export const SESSION_COOKIE = "adivritti_session";

/** Eight hours, matching the officer's shift. */
export const SESSION_TTL_SECONDS = 60 * 60 * 8;

export const DEMO_SECRET =
  "dev-only-officer-console-secret-do-not-use-in-production";

/** What a verified cookie carries. Nothing here grants access to Core. */
export interface SessionClaims {
  /** Subject: the officer's phone number, or "demo" for a demo session. */
  sub: string;
  /** Always OFFICER. Present so a future multi-role console can branch on it. */
  role: "OFFICER";
  /** True when the session was minted by the local demo path, not by Core. */
  demo: boolean;
  /** Issued-at, seconds since the epoch. */
  iat: number;
  /** Expiry, seconds since the epoch. */
  exp: number;
}

const encoder = new TextEncoder();

function base64url(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = "";
  for (const byte of view) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64url(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/")
    + "=".repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function hmacKey(secret: string, usage: KeyUsage[]): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    usage,
  );
}

/**
 * The signing secret, or null when there is none that may be used.
 *
 * In production a missing secret is fatal to *authentication*, not to boot: the
 * console stays reachable, but no cookie is ever minted and every cookie is
 * rejected. That is the fail-closed outcome — a deployment that forgot to set the
 * variable shows a login page that never succeeds, instead of a console anyone
 * can reach by setting a cookie.
 */
export function sessionSecret(env: {
  OFFICER_SESSION_SECRET?: string;
  NODE_ENV?: string;
}): string | null {
  const configured = env.OFFICER_SESSION_SECRET?.trim();
  if (configured) {
    if (configured === DEMO_SECRET) {
      // Only ever acceptable outside production; refusing it here means a copied
      // .env.example cannot quietly become the production key.
      if (env.NODE_ENV === "production") return null;
    }
    return configured;
  }
  if (env.NODE_ENV === "production") return null;
  return DEMO_SECRET;
}

/** True when the process is running without a secret anyone should trust. */
export function usingDevSecret(env: {
  OFFICER_SESSION_SECRET?: string;
  NODE_ENV?: string;
}): boolean {
  return sessionSecret(env) === DEMO_SECRET;
}

export async function signSession(
  claims: SessionClaims,
  secret: string,
): Promise<string> {
  const header = base64url(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = base64url(encoder.encode(JSON.stringify(claims)));
  const signingInput = `${header}.${payload}`;
  const signature = await crypto.subtle.sign(
    "HMAC",
    await hmacKey(secret, ["sign"]),
    encoder.encode(signingInput),
  );
  return `${signingInput}.${base64url(signature)}`;
}

function isSessionClaims(value: unknown): value is SessionClaims {
  if (typeof value !== "object" || value === null) return false;
  const c = value as Partial<SessionClaims>;
  return typeof c.sub === "string"
    && c.role === "OFFICER"
    && typeof c.demo === "boolean"
    && typeof c.iat === "number"
    && typeof c.exp === "number";
}

/**
 * Verify signature and expiry. Returns null for anything that is not a valid,
 * unexpired session — a wrong shape, a bad signature, a tampered payload, an
 * expired token, or no secret to verify against.
 *
 * The signature check goes through `subtle.verify`, which is constant-time; a
 * hand-rolled `===` comparison over the signature bytes would leak how much of a
 * forgery was correct.
 */
export async function verifySession(
  token: string | undefined | null,
  secret: string | null,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): Promise<SessionClaims | null> {
  if (!token || !secret) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, payload, signature] = parts;
  if (!header || !payload || !signature) return null;

  let signatureBytes: Uint8Array;
  let payloadBytes: Uint8Array;
  try {
    signatureBytes = fromBase64url(signature);
    payloadBytes = fromBase64url(payload);
  } catch {
    return null;
  }

  let ok: boolean;
  try {
    ok = await crypto.subtle.verify(
      "HMAC",
      await hmacKey(secret, ["verify"]),
      signatureBytes as unknown as ArrayBuffer,
      encoder.encode(`${header}.${payload}`),
    );
  } catch {
    return null;
  }
  if (!ok) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(new TextDecoder().decode(payloadBytes));
  } catch {
    return null;
  }
  if (!isSessionClaims(parsed)) return null;
  if (parsed.exp <= nowSeconds) return null;
  // A token minted far in the future has a forged or mis-set clock; treat the
  // iat > exp case as invalid rather than as a session that never expires.
  if (parsed.iat > parsed.exp) return null;
  return parsed;
}

/** Build and sign a session, or null when there is no usable secret. */
export async function mintSession(
  subject: string,
  demo: boolean,
  secret: string | null,
  ttlSeconds: number = SESSION_TTL_SECONDS,
  nowSeconds: number = Math.floor(Date.now() / 1000),
): Promise<string | null> {
  if (!secret) return null;
  const claims: SessionClaims = {
    sub: subject,
    role: "OFFICER",
    demo,
    iat: nowSeconds,
    exp: nowSeconds + ttlSeconds,
  };
  return signSession(claims, secret);
}
