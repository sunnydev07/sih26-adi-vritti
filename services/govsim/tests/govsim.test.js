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

  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (serverExit) break;
    try {
      // The readiness probe itself needs the header: without it the poll would
      // randomly fail on an injected 500 or a truncated body.
      const res = await fetch(`${BASE}/health`, { headers: NO_CHAOS });
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

test('health reports ok and chaos enabled', async () => {
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

test('NOS records survives page=0, page=-1 and a non-numeric page', async () => {
  // A government portal with a hand-typed query string. The mock must answer,
  // not throw -- an adapter that 500s on `?page=abc` is the bug, not the mock.
  // Current behaviour, which the assertions pin:
  //   page=0     -> NOS_DATES[-1]  -> out of range, selection_date omitted
  //   page=-1    -> NOS_DATES[-2]  -> out of range, selection_date omitted
  //   page=abc   -> parseInt gives NaN, and JSON.stringify(NaN) is null
  const cases = [
    { query: '?page=0', page: 0 },
    { query: '?page=-1', page: -1 },
    { query: '?page=abc', page: null },
  ];
  for (const { query, page } of cases) {
    const res = await get(`/nos/records${query}`);
    assert.strictEqual(res.status, 200, `${query} should answer 200, got ${res.status}`);
    assert.notStrictEqual(res.json, null, `${query} returned unparseable JSON: ${res.text}`);
    assert.strictEqual(res.json.system, 'NOS');
    assert.strictEqual(res.json.page, page);
    assert.ok(Array.isArray(res.json.records) && res.json.records.length > 0,
      `${query} returned no records`);
    assert.ok(!('selection_date' in res.json),
      `${query} unexpectedly produced a selection_date; update this test if that is fixed`);
  }
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
