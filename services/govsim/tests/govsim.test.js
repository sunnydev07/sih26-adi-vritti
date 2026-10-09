const assert = require('node:assert');
const { spawn } = require('node:child_process');
const path = require('node:path');
const { after, before, test } = require('node:test');

// Contract tests against a real GoVSim process.
//
// The simulator's chaos is deliberate and is NOT weakened here: the server is
// started with CHAOS_ENABLED=true and the 8% error rate, because a suite that
// ran against a sanitised simulator would prove nothing about the Core
// adapters. Determinism comes from the `x-chaos: off` request header, which
// short-circuits the chaos middleware in src/index.js -- for every test that is
// not specifically about chaos.
//
// PORT: overridable if 41237 is taken. `trust proxy` is deliberately NOT set in
// src/index.js, so req.ip is the loopback socket address for every request and
// the rate-limit window is per-process rather than per-client. Faking
// X-Forwarded-For would achieve nothing, so the rate-limit test is written to
// run last and the rest of the file avoids the counter entirely.
const PORT = Number(process.env.GOVSIM_TEST_PORT || 41237);
const BASE = `http://127.0.0.1:${PORT}`;
const NO_CHAOS = { 'x-chaos': 'off' };

let server;
let serverExit = null;

before(async () => {
  server = spawn(process.execPath, [path.join(__dirname, '..', 'src', 'index.js')], {
    env: {
      ...process.env,
      PORT: String(PORT),
      // Pinned rather than inherited, so the suite exercises the hostile
      // configuration even if the developer's shell exports different values.
      CHAOS_ENABLED: 'true',
      CHAOS_ERROR_RATE: '0.08',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  server.on('error', (err) => {
    serverExit = err;
  });
  server.on('exit', (code) => {
    serverExit = serverExit || new Error(`govsim exited early with code ${code}`);
  });
  server.stderr.on('data', (chunk) => process.stderr.write(`[govsim] ${chunk}`));

  // The readiness probe hits /health WITHOUT the header now: /health bypasses
  // the chaos middleware (registered before it in src/index.js), so a plain
  // probe is the correct assertion that the process is up and the bypass holds.
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (serverExit) break;
    try {
      const res = await fetch(`${BASE}/health`);
      if (res.ok) {
        await res.text();
        return;
      }
      await res.text();
    } catch {
      // not listening yet
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  if (serverExit) {
    throw new Error(
      `could not start govsim on :${PORT} (${serverExit.message}). ` +
        'Set GOVSIM_TEST_PORT to a free port and retry.');
  }
  throw new Error(`govsim did not become healthy on :${PORT} within 6s`);
});

after(() => {
  if (server && !server.killed) server.kill();
});

// `json` is null when the body is not valid JSON -- which is exactly what the
// truncated-JSON injection produces, so callers can assert on that too.
async function get(pathname, headers = NO_CHAOS) {
  const res = await fetch(`${BASE}${pathname}`, { headers });
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = null;
  }
  return { status: res.status, text, json };
}

test('health reports ok without the chaos header and skips the rate-limit window', async () => {
  // No NO_CHAOS header on purpose: this is the assertion that /health bypasses
  // the chaos middleware. With chaos on (pinned above) and no header, any
  // pass through chaos() would randomly 500, truncate, delay, or at minimum
  // consume a slot of the shared 20 RPM window -- all of which this test pins
  // against. Five consecutive plain probes must all answer 200.
  for (let i = 0; i < 5; i += 1) {
    const plain = await get('/health', {});
    assert.strictEqual(plain.status, 200, `plain /health probe ${i} failed: ${plain.text}`);
    assert.strictEqual(plain.json.status, 'ok');
    assert.strictEqual(plain.json.service, 'govsim');
  }
  const { status, json } = await get('/health');
  assert.strictEqual(status, 200);
  assert.strictEqual(json.status, 'ok');
  assert.strictEqual(json.service, 'govsim');
  // Guards against the suite being made green by quietly switching chaos off.
  assert.strictEqual(json.chaos, true);
});

test('x-chaos: off bypasses the chaos middleware entirely', async () => {
  // 30 requests, deliberately more than the 20/minute limit. The header returns
  // from the chaos middleware before the rate-limit counter is touched, so
  // proving that all 30 come back 200 also proves the middleware was skipped --
  // if it had run, at least 10 of these would have been 429. That is a
  // deterministic signal, where "none of these 30 got a 500" would only be a
  // probabilistic one (it catches the 8% injection ~96% of the time).
  const responses = await Promise.all(
    Array.from({ length: 30 }, () => get('/health', NO_CHAOS)));

  const statuses = responses.map((r) => r.status);
  assert.deepStrictEqual([...new Set(statuses)], [200], `unexpected statuses: ${statuses}`);

  // Truncated JSON (2% injection) would leave json null here.
  const unparseable = responses.filter((r) => r.json === null);
  assert.deepStrictEqual(unparseable.map((r) => r.text), [], 'truncated body leaked through');
});

test('NOS date formats rotate per page', async () => {
  const pages = await Promise.all([1, 2, 3].map((n) => get(`/nos/records?page=${n}`)));
  for (const page of pages) {
    assert.strictEqual(page.status, 200);
    assert.strictEqual(page.json.system, 'NOS');
  }
  assert.deepStrictEqual(
    pages.map((p) => p.json.selection_date),
    ['17/08/2024', '2024-08-17', '1723852800'],
    'DD/MM/YYYY, ISO then epoch -- the quirk Core has to parse defensively');

  // And it wraps rather than running off the end of the array.
  const wrapped = await get('/nos/records?page=4');
  assert.strictEqual(wrapped.json.selection_date, '17/08/2024');
});

test('NOS records rejects a non-positive or non-numeric page with a 400', async () => {
  // A government portal with a hand-typed query string. The mock answers with
  // an invariant shape: page=0/-1/abc used to answer 200 with selection_date
  // silently dropped and page nulled, so a parser expecting the documented
  // shape branched three ways. Fixed per the test's own note.
  for (const query of ['?page=0', '?page=-1', '?page=abc']) {
    const res = await get(`/nos/records${query}`);
    assert.strictEqual(res.status, 400, `${query} should answer 400, got ${res.status}`);
    assert.strictEqual(res.json.error, 'bad_request');
  }
  // Valid pages are unchanged.
  const first = await get('/nos/records?page=1');
  assert.strictEqual(first.status, 200);
  assert.strictEqual(first.json.selection_date, '17/08/2024');
  assert.strictEqual(first.json.page, 1);
});

test('PFMS taxonomy covers all six codes', async () => {
  const codes = ['E001_AADHAAR_NOT_SEEDED', 'E002_ACCOUNT_DORMANT', 'E003_IFSC_CHANGED',
    'E004_NAME_MISMATCH', 'E005_FUNDS_NOT_RELEASED', 'E006_OTHER'];
  assert.strictEqual(codes.length, 6);

  // The endpoint draws from that taxonomy plus null (a no-failure draw), so
  // every observed failure_code must be either null or one of the six.
  const draws = await Promise.all(
    Array.from({ length: 8 }, () => get('/pfms/status')));
  for (const draw of draws) {
    assert.strictEqual(draw.status, 200);
    assert.strictEqual(draw.json.system, 'PFMS');
    const observed = draw.json.failure_code;
    assert.ok(observed === null || codes.includes(observed),
      `unexpected failure_code from the endpoint: ${observed}`);
  }
});

test('PFMS status is stable per payment reference', async () => {
  // Repeated polls for the same payment used to cycle through the taxonomy,
  // so the DBT-Doctor narrative changed between identical calls. Same ref,
  // same answer, every call; the demo reference is pinned to E001.
  const demo = await get('/pfms/status?pfms_ref=PFMS-9988776655');
  assert.strictEqual(demo.json.pfms_ref, 'PFMS-9988776655');
  assert.strictEqual(demo.json.failure_code, 'E001_AADHAAR_NOT_SEEDED');
  assert.strictEqual(demo.json.status, 'failed');
  for (let i = 0; i < 3; i += 1) {
    const again = await get('/pfms/status?pfms_ref=REF-STABLE-1');
    assert.strictEqual(again.json.failure_code,
      (await get('/pfms/status?pfms_ref=REF-STABLE-1')).json.failure_code,
      'same ref gave different codes across polls');
  }
  const def = await get('/pfms/status');
  assert.strictEqual(def.json.pfms_ref, 'PFMS-9988776655');
  assert.strictEqual(def.json.failure_code, 'E001_AADHAAR_NOT_SEEDED');
});

test('verify outcomes are deterministic per USID and the demo student always passes', async () => {
  // Verify DECISIONS are a pure function of the USID (stable hash in
  // src/index.js), while transport chaos stays random. Same USID, same
  // answer, every call -- this is what makes the demo reproducible.
  const demo = '11111111-1111-4111-8111-111111111111';
  const paths = ['/nsp/verify', '/sfmp/verify', '/nos/verify', '/ugc-nta/verify', '/digilocker/verify',
    '/udise/verify', '/pfms/verify'];
  for (const path of paths) {
    const first = await get(`${path}?usid=${demo}`);
    assert.strictEqual(first.status, 200);
    assert.strictEqual(first.json.verified, true, `${path} must verify the demo student`);
    const second = await get(`${path}?usid=${demo}`);
    assert.strictEqual(second.json.verified, true, `${path} changed its answer for the same USID`);
  }
  const probe = '22222222-2222-4222-8222-222222222222';
  for (const path of paths) {
    const a = await get(`${path}?usid=${probe}`);
    const b = await get(`${path}?usid=${probe}`);
    assert.strictEqual(a.json.verified, b.json.verified,
      `${path} gave different answers for the same USID`);
  }
});

test('digilocker has a genuine, stable rejection path', async () => {
  // The proxy used to rubber-stamp verified:true. Now ~1 in 8 non-demo USIDs
  // is rejected with a reason_code, deterministically -- probe until one is
  // found (expected after ~8 draws; capped far above any flake probability).
  let rejected = null;
  for (let i = 0; i < 200 && rejected === null; i += 1) {
    const usid = `probe-${i}-0000-4000-8000-000000000000`;
    const res = await get(`/digilocker/verify?usid=${usid}`);
    assert.strictEqual(res.status, 200);
    if (res.json.verified === false) rejected = { usid, body: res.json };
  }
  assert.ok(rejected, 'expected a rejected USID within 200 deterministic draws');
  assert.strictEqual(rejected.body.reason_code, 'DOCUMENT_MISMATCH');
  const again = await get(`/digilocker/verify?usid=${rejected.usid}`);
  assert.strictEqual(again.json.verified, false, 'rejection must be stable across calls');
});

test('udise and pfms have genuine, stable rejection paths', async () => {
  // Both used to rubber-stamp verified:true, so the corroboration tier and
  // the failures-become-deficiencies flow were untestable through them. Now
  // ~1 in 10 non-demo USIDs is rejected, deterministically.
  for (const path of ['/udise/verify', '/pfms/verify']) {
    let rejected = null;
    for (let i = 0; i < 200 && rejected === null; i += 1) {
      const usid = `rej-probe-${i}-0000-4000-8000-000000000000`;
      const res = await get(`${path}?usid=${usid}`);
      assert.strictEqual(res.status, 200);
      if (res.json.verified === false) rejected = { usid, body: res.json };
    }
    assert.ok(rejected, `expected a rejected USID on ${path} within 200 deterministic draws`);
    const again = await get(`${path}?usid=${rejected.usid}`);
    assert.strictEqual(again.json.verified, false, `${path} rejection must be stable across calls`);
  }
});

test('digilocker verify carries deterministic document fields on success', async () => {
  // Core persists adapter-sourced field values into the claims wallet (never
  // caller-supplied ones). The fields must be a pure function of the USID,
  // exactly like the verify decision.
  const demo = '11111111-1111-4111-8111-111111111111';
  const first = await get(`/digilocker/verify?usid=${demo}&claim=income`);
  assert.strictEqual(first.status, 200);
  assert.strictEqual(first.json.verified, true);
  assert.deepStrictEqual(first.json.fields,
    { family_income_annual_paise: 24000000, st_or_pvtg_status: 'ST', class_level: 9 });
  // ~1 in 8 non-demo USIDs is rejected, so draw until a passing one turns up
  // (expected on the first draw; capped far above any flake probability).
  let passingUsid = null;
  let a = null;
  for (let i = 0; i < 50 && passingUsid === null; i += 1) {
    const usid = `field-probe-${i}-0000-4000-8000-000000000000`;
    const res = await get(`/digilocker/verify?usid=${usid}&claim=income`);
    assert.strictEqual(res.status, 200);
    if (res.json.verified === true) {
      passingUsid = usid;
      a = res;
    }
  }
  assert.ok(passingUsid, 'no passing USID found for the fields draw');
  const b = await get(`/digilocker/verify?usid=${passingUsid}&claim=income`);
  assert.deepStrictEqual(a.json.fields, b.json.fields, 'fields changed for the same USID');
  assert.strictEqual(typeof a.json.fields.family_income_annual_paise, 'number');
  assert.ok(['ST', 'PVTG'].includes(a.json.fields.st_or_pvtg_status));
  assert.ok(Number.isInteger(a.json.fields.class_level)
    && a.json.fields.class_level >= 9 && a.json.fields.class_level <= 12);
});

// Kept last: it is the only test that lets requests through the chaos
// middleware, so it is the only one that advances the 60-second rate-limit
// window, and that window is shared by the whole process.
test('rate limiter returns 429 on the 21st request in the window', async () => {
  const firstTwenty = await Promise.all(
    Array.from({ length: 20 }, () => get('/pfms/status', {}).then((r) => r.status)));

  // Any 500 in here is the 8% injection doing its job, and the truncated-JSON
  // branch would show up as a parse failure -- neither is what this test is
  // about. What matters is that the counter reached exactly 20 and stopped.
  assert.ok(!firstTwenty.includes(429), `429 arrived before the 21st request: ${firstTwenty}`);
  for (const status of firstTwenty) {
    assert.ok(status === 200 || status === 500, `unexpected status: ${status}`);
  }

  // The rate-limit check runs BEFORE the chaos injection in src/index.js, so
  // this is 429 regardless of what the PRNG did above.
  const twentyFirst = await get('/pfms/status', {});
  assert.strictEqual(twentyFirst.status, 429);
  assert.strictEqual(twentyFirst.json.error, 'rate_limited');
  assert.strictEqual(twentyFirst.json.retry_after_seconds, 30);
});
