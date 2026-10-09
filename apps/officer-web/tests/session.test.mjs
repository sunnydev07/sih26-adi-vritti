import assert from "node:assert/strict";
import test from "node:test";

import {
  DEMO_SECRET,
  SESSION_TTL_SECONDS,
  mintSession,
  sessionSecret,
  signSession,
  usingDevSecret,
  verifyRequestSession,
  verifySession,
} from "../src/lib/session.ts";

/**
 * The officer console's guard is only worth having if a cookie cannot be forged.
 *
 * It could: `proxy.ts` checked that `adivritti_session` had *a value*, and the
 * login route wrote `demo:${phone}` into it. Setting the cookie by hand in devtools
 * reached `/dashboard`. These tests pin the replacement — an HS256 signature and
 * an expiry, both checked — and they are the regression guard for the exact
 * bypass they close.
 */

const SECRET = "a-real-deployment-secret-with-32-bytes-minimum";
const T0 = 1_780_000_000; // fixed "now", so nothing here depends on the clock
const DEV_ENV = { NODE_ENV: "development" };
const PROD_ENV = { NODE_ENV: "production" };

function claimsFor(exp = T0 + 3600, iat = T0) {
  return { sub: "demo:9999999999", role: "OFFICER", demo: true, iat, exp };
}

test("a correctly signed session verifies", async () => {
  const token = await signSession(claimsFor(), SECRET);
  const claims = await verifySession(token, SECRET, T0);
  assert.equal(claims?.sub, "demo:9999999999");
  assert.equal(claims?.role, "OFFICER");
  assert.equal(claims?.demo, true);
});

test("a hand-set cookie does not verify — the original bypass", async () => {
  // Exactly what an attacker typed into devtools before the fix.
  for (const forged of [
    "anything",
    "demo:9999999999",
    "a.b.c",
    "",
    "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkZW1vIn0.",
  ]) {
    assert.equal(
      await verifySession(forged, SECRET, T0),
      null,
      `forged cookie ${JSON.stringify(forged)} was accepted`,
    );
  }
  assert.equal(await verifySession(undefined, SECRET, T0), null);
  assert.equal(await verifySession(null, SECRET, T0), null);
});

test("a session signed with a different secret does not verify", async () => {
  const token = await signSession(claimsFor(), "some-other-secret-entirely-x");
  assert.equal(await verifySession(token, SECRET, T0), null);
});

test("tampering with the payload invalidates the signature", async () => {
  const token = await signSession(claimsFor(), SECRET);
  const [header, , signature] = token.split(".");
  const forged = Buffer.from(
    JSON.stringify({ ...claimsFor(), role: "ADMIN", sub: "someone-else" }),
  ).toString("base64url");
  assert.equal(await verifySession(`${header}.${forged}.${signature}`, SECRET, T0), null);
});

test("an expired session is refused even though its signature is valid", async () => {
  const token = await signSession(claimsFor(T0 + 10, T0), SECRET);
  assert.notEqual(await verifySession(token, SECRET, T0 + 9), null);
  // Exactly at exp: the token is no longer valid. An off-by-one that kept
  // `exp <= now` as `exp < now` would leave the console reachable for one extra
  // second per session, which is how these checks quietly stop being enforced.
  assert.equal(await verifySession(token, SECRET, T0 + 10), null);
  assert.equal(await verifySession(token, SECRET, T0 + 11), null);
});

test("a session whose iat is after its exp is refused", async () => {
  const token = await signSession(claimsFor(T0 - 100, T0), SECRET);
  assert.equal(await verifySession(token, SECRET, T0 - 200), null);
});

test("a payload with the wrong shape is refused", async () => {
  for (const bad of [
    { sub: "x", role: "SCHOLAR", demo: true, iat: T0, exp: T0 + 10 },
    { sub: "x", role: "OFFICER", demo: "yes", iat: T0, exp: T0 + 10 },
    { sub: "x", role: "OFFICER", demo: true, exp: T0 + 10 },
    { sub: 1, role: "OFFICER", demo: true, iat: T0, exp: T0 + 10 },
  ]) {
    const token = await signSession(bad, SECRET);
    assert.equal(await verifySession(token, SECRET, T0), null, `accepted ${JSON.stringify(bad)}`);
  }
});

test("a garbage signature segment is refused rather than throwing", async () => {
  const token = await signSession(claimsFor(), SECRET);
  const [header, payload] = token.split(".");
  assert.equal(await verifySession(`${header}.${payload}.@@@not-base64@@@`, SECRET, T0), null);
  assert.equal(await verifySession("only.two", SECRET, T0), null);
  assert.equal(await verifySession("", SECRET, T0), null);
});

test("production with no configured secret verifies nothing and mints nothing", async () => {
  assert.equal(sessionSecret(PROD_ENV), null);
  assert.equal(await mintSession("demo:1", true, null, SESSION_TTL_SECONDS, T0), null);
});

test("production refuses the published development secret", async () => {
  const env = { NODE_ENV: "production", OFFICER_SESSION_SECRET: DEMO_SECRET };
  assert.equal(sessionSecret(env), null);
  assert.equal(usingDevSecret(env), false);
});

test("production uses a real configured secret and does not call it a dev secret", () => {
  const env = { NODE_ENV: "production", OFFICER_SESSION_SECRET: SECRET };
  assert.equal(sessionSecret(env), SECRET);
  assert.equal(usingDevSecret(env), false);
});

test("development falls back to the published secret and says so", () => {
  assert.equal(sessionSecret(DEV_ENV), DEMO_SECRET);
  assert.equal(usingDevSecret(DEV_ENV), true);
});

test("a configured secret wins over the development fallback", () => {
  assert.equal(sessionSecret({ ...DEV_ENV, OFFICER_SESSION_SECRET: SECRET }), SECRET);
  assert.equal(usingDevSecret({ ...DEV_ENV, OFFICER_SESSION_SECRET: SECRET }), false);
});

test("minted sessions default to an eight-hour shift", async () => {
  const token = await mintSession("demo:9999999999", true, SECRET, SESSION_TTL_SECONDS, T0);
  const claims = await verifySession(token, SECRET, T0);
  assert.equal(claims?.exp - claims?.iat, SESSION_TTL_SECONDS);
  assert.equal(SESSION_TTL_SECONDS, 8 * 60 * 60);
});

test("a minted session stops verifying the moment it expires", async () => {
  const token = await mintSession("demo:9999999999", true, SECRET, 60, T0);
  assert.notEqual(await verifySession(token, SECRET, T0 + 59), null);
  assert.equal(await verifySession(token, SECRET, T0 + 61), null);
});

function cookieRequest(token) {
  return {
    cookies: {
      get: (name) => (name === "adivritti_session" && token ? { value: token } : undefined),
    },
  };
}

test("verifyRequestSession accepts a signed cookie for the BFF proxies", async () => {
  const env = { NODE_ENV: "production", OFFICER_SESSION_SECRET: SECRET };
  const token = await mintSession("demo:9999999999", true, SECRET);
  const claims = await verifyRequestSession(cookieRequest(token), env);
  assert.equal(claims?.sub, "demo:9999999999");
  assert.equal(claims?.role, "OFFICER");
});

test("verifyRequestSession rejects missing, forged, and secret-less requests", async () => {
  const env = { NODE_ENV: "production", OFFICER_SESSION_SECRET: SECRET };
  assert.equal(await verifyRequestSession(cookieRequest(undefined), env), null);
  assert.equal(await verifyRequestSession(cookieRequest("adivritti_session=forged"), env), null);
  // Production with no secret verifies nothing, even with a real token present.
  const token = await mintSession("demo:9999999999", true, SECRET);
  assert.equal(await verifyRequestSession(cookieRequest(token), PROD_ENV), null);
});
