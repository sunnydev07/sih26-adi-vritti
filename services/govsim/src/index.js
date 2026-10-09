/**
 * GoVSim — 7 hostile mock government systems.
 *
 * Deliberately dirty + flaky so the Core adapters prove resilience is real:
 * - 8% random HTTP 500s (disable with CHAOS_ENABLED=false)
 * - 1.5-4s latency spikes, occasional truncated JSON, 20 RPM rate limit
 * - Name variants across systems, missing Aadhaar on legacy rows,
 *   duplicate beneficiaries, UDISE+ students with no NSP record (the gap)
 *
 * Verify OUTCOMES are deterministic per USID (stable hash; the demo student
 * always passes), so the demo is reproducible and every system — including
 * /digilocker/verify — has a genuine rejection path. Only the TRANSPORT
 * chaos above stays random.
 *
 * Full quirk catalogue: docs/specs/govsim-contracts.md
 */
'use strict';

const express = require('express');

const PORT = process.env.PORT || 4000;
const CHAOS_ENABLED = process.env.CHAOS_ENABLED !== 'false';
// A non-numeric value parses to NaN, and `rnd() < NaN` is always false, so a
// typo silently disabled chaos entirely. Fall back to the documented 8%.
const _parsedChaosRate = parseFloat(process.env.CHAOS_ERROR_RATE || '0.08');
const CHAOS_ERROR_RATE = Number.isFinite(_parsedChaosRate) ? _parsedChaosRate : 0.08;

// --- Deterministic PRNG (mulberry32, seed = PS number) ---
let state = 26238;
function rnd() {
  state |= 0; state = (state + 0x6d2b79f5) | 0;
  let t = Math.imul(state ^ (state >>> 15), 1 | state);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

// --- Dirty demo dataset: one student, three spellings, plus gap/dupe rows ---
const DEMO_STUDENT = {
  usid: '11111111-1111-4111-8111-111111111111',
  nspName: 'Sunita Meena',
  sfmpName: 'Sunita K. Mina',
  nosName: 'SUNITA MEENA',
  dob: '2012-05-01',
  district: 'Mandla',
  otrId: '12345678901234',
  aadhaarRefKey: 'AVR-demo-student-01',
};
const NOS_DATES = ['17/08/2024', '2024-08-17', '1723852800']; // DD/MM/YYYY, ISO, epoch — per page!

// --- Deterministic verify outcomes (FNV-1a hash over the USID) ---
// Transport chaos stays random, but a verify DECISION must be reproducible:
// the same USID gets the same answer on every call and every run, so the demo
// is stable and Core's retry path cannot be confused with a changed answer.
// The demo student always passes; other USIDs pass at the per-system rate,
// which keeps a genuine rejection path for every system (including
// /digilocker/verify, which used to rubber-stamp `verified: true`).
function stableHash(str) {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i += 1) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}
const decide = (usid, salt, passRate) =>
  usid === DEMO_STUDENT.usid || (stableHash(`${salt}:${usid}`) % 100) < Math.round(passRate * 100);

// --- Chaos middleware ---
// ip -> timestamps (20 RPM window).
//
// The map was previously only ever *filtered* on read and never swept, so every
// distinct client address that ever hit the service kept a key (and its array)
// alive for the lifetime of the process. Behind a load balancer or a NAT gateway
// the source address is shared, but a service exposed directly — or an attacker
// rotating through a source range — grows this map without bound. Entries are
// now dropped once their whole window has aged out, so the map holds at most the
// addresses seen in the last 60s.
const RATE_WINDOW_MS = 60_000;
const RATE_LIMIT = 20;
const hits = new Map(); // ip -> timestamps
let lastSweep = 0;

function sweep(now) {
  // Amortised: a full pass costs O(active addresses) and there is no reason to
  // run it on every request when a window is a minute long.
  if (now - lastSweep < RATE_WINDOW_MS) return;
  lastSweep = now;
  for (const [ip, window] of hits) {
    if (!window.some((t) => now - t < RATE_WINDOW_MS)) hits.delete(ip);
  }
}

function chaos(req, res, next) {
  if (req.headers['x-chaos'] === 'off') return next();
  const now = Date.now();
  sweep(now);
  const window = (hits.get(req.ip) || []).filter((t) => now - t < RATE_WINDOW_MS);
  window.push(now);
  hits.set(req.ip, window);
  if (window.length > RATE_LIMIT) return res.status(429).json({ error: 'rate_limited', retry_after_seconds: 30 });
  if (CHAOS_ENABLED && rnd() < CHAOS_ERROR_RATE) return res.status(500).json({ error: 'govsim_random_500' });
  const latencySpike = CHAOS_ENABLED && rnd() < 0.1;
  const delay = latencySpike ? 1500 + rnd() * 2500 : rnd() * 120;
  setTimeout(() => {
    if (CHAOS_ENABLED && rnd() < 0.02) return res.send('{"truncated": true, "half_a_payload');
    next();
  }, delay);
}

const app = express();
app.use(express.json());

// Health is a readiness probe, not a hostile system: it bypasses the chaos
// middleware (random 500s, multi-second latency, rate-limit counter) so the
// compose healthcheck, the CI `stack` job, and the test-suite readiness poll
// get a truthful answer about whether the process is up. Health must never
// consume the 20 RPM per-process rate-limit budget either: the readiness poll
// fires before the suite runs, so counting it would move the limit window and
// make the pinned 21st-request rate-limit assertion flaky.
app.get('/health', (req, res) => res.json({ status: 'ok', service: 'govsim', chaos: CHAOS_ENABLED }));

app.use(chaos);

const verify = (verified, extra = {}) => ({ verified, ...extra });

// NSP — SOAP-ish XML flavour, OTR lookup, legacy rows miss Aadhaar 15%
app.get('/nsp/verify', (req, res) => {
  const usid = String(req.query.usid || '');
  const ok = decide(usid, 'nsp', 0.75);
  // Stable per USID, not a coin flip per request: "15% legacy rows" is a row
  // property, so the same student must not alternately have and lack Aadhaar
  // across calls, or duplicate-linking on Aadhaar flakes.
  const hasAadhaar = decide(usid, 'nsp-aadhaar', 0.85);
  res.json(verify(ok, { system: 'NSP', name: DEMO_STUDENT.nspName, otr_id: DEMO_STUDENT.otrId,
    aadhaar_ref_key: hasAadhaar ? DEMO_STUDENT.aadhaarRefKey : null, format: 'soap-xml-ish' }));
});
app.get('/nsp/applications', (req, res) => {
  res.json({ system: 'NSP', records: [{ otr_id: DEMO_STUDENT.otrId, name: DEMO_STUDENT.nspName,
    scheme: 'PRE_MATRIC', stage: 'district_nodal', sanctioned_amount_paise: 700000 }] });
});

// SFMP (Canara Bank) — flat CSV-in-JSON, different ID scheme + spelling
app.get('/sfmp/verify', (req, res) => {
  const ok = decide(String(req.query.usid || ''), 'sfmp', 0.65);
  res.json(verify(ok, { system: 'SFMP', name: DEMO_STUDENT.sfmpName, format: 'csv-in-json' }));
});
app.get('/sfmp/records', (req, res) => {
  res.json({ system: 'SFMP', csv: 'scholar_id,name,scheme\nSFMP-482913,"Mina, Sunita K.",NFST' });
});

// NOS — paginated, DIFFERENT date format on each page
app.get('/nos/verify', (req, res) => {
  res.json(verify(decide(String(req.query.usid || ''), 'nos', 0.5),
    { system: 'NOS', name: DEMO_STUDENT.nosName }));
});
app.get('/nos/records', (req, res) => {
  // A hand-typed query string must still get an invariant shape: non-positive
  // or non-numeric pages are a 400, not a response with selection_date and
  // page silently dropped or nulled.
  const page = parseInt(req.query.page || '1', 10);
  if (!Number.isInteger(page) || page < 1) {
    return res.status(400).json({ error: 'bad_request', message: 'page must be a positive integer' });
  }
  res.json({ system: 'NOS', page, per_page: 20,
    selection_date: NOS_DATES[(page - 1) % NOS_DATES.length],
    date_format_note: 'page 1: DD/MM/YYYY, page 2: YYYY-MM-DD, page 3: epoch — good luck',
    records: [{ candidate: DEMO_STUDENT.nosName, year: '2024', category: 'ST' }] });
});

// UDISE+/APAAR — enrolment, AISHE codes; includes students with ZERO NSP record.
// A genuine rejection path like every other system: ~1 in 10 non-demo USIDs
// is not found enrolled, deterministically, so Core's corroboration tier and
// deficiency flow for enrolment claims are actually exercisable.
app.get('/udise/verify', (req, res) => {
  const ok = decide(String(req.query.usid || ''), 'udise', 0.9);
  res.json(verify(ok, { system: 'UDISE+', enrolled: ok, aishe_code: 'AISHE-MP-042' }));
});
app.get('/udise/students', (req, res) => {
  res.json({ system: 'UDISE+', district: req.query.district || 'Mandla',
    students: [{ name: DEMO_STUDENT.nspName, school: 'Govt HS Bichhiya, Mandla', class: 9, has_nsp_record: true },
      { name: 'Phulo Gond', school: 'Govt HS Bichhiya, Mandla', class: 9, has_nsp_record: false }] });
});

// PFMS/DBT — payment status + full rejection taxonomy.
// A genuine rejection path: ~1 in 10 non-demo USIDs fails bank-account
// validation, deterministically, so the "failures become deficiencies" half of
// the demo is exercisable through PFMS and not only DigiLocker/NOS.
const PFMS_CODES = ['E001_AADHAAR_NOT_SEEDED', 'E002_ACCOUNT_DORMANT', 'E003_IFSC_CHANGED',
  'E004_NAME_MISMATCH', 'E005_FUNDS_NOT_RELEASED', 'E006_OTHER', null, null];
app.get('/pfms/verify', (req, res) => {
  const ok = decide(String(req.query.usid || ''), 'pfms', 0.9);
  res.json(verify(ok, { system: 'PFMS', account_valid: ok }));
});
app.get('/pfms/status', (req, res) => {
  // Stable per payment reference, not a fresh draw per poll: repeated polls
  // for the same payment cycled through E001–E006, so the DBT-Doctor narrative
  // and JAGO tool output changed between identical calls. The demo reference
  // is pinned to E001 (Aadhaar seeding — Beat 4's story).
  const ref = String(req.query.pfms_ref || 'PFMS-9988776655');
  const failureCode = ref === 'PFMS-9988776655'
    ? 'E001_AADHAAR_NOT_SEEDED'
    : PFMS_CODES[stableHash(`pfms:${ref}`) % PFMS_CODES.length];
  res.json({ system: 'PFMS', pfms_ref: ref, status: failureCode ? 'failed' : 'paid',
    failure_code: failureCode, sanctioned_amount_paise: 700000,
    paid_amount_paise: failureCode ? 0 : 700000 });
});

// UGC-NTA — NET/JRF results
app.get('/ugc-nta/verify', (req, res) => {
  res.json(verify(decide(String(req.query.usid || ''), 'ugc-nta', 0.4),
    { system: 'UGC-NTA', exam: 'NET-JRF' }));
});

// DigiLocker proxy — stands in when the API Setu sandbox is down.
// Deterministic per USID like the other systems: the demo student always
// verifies, roughly 1 in 8 other USIDs is rejected with a reason_code, so
// Core's corroboration tier actually exercises its rejection path.
// A successful verification also carries deterministic document `fields`:
// Core persists adapter-sourced values into the claims wallet (never
// caller-supplied ones), so the eligibility engine can evaluate value rules.
function digilockerFields(usid) {
  if (usid === DEMO_STUDENT.usid) {
    return { family_income_annual_paise: 24000000, st_or_pvtg_status: 'ST', class_level: 9 };
  }
  const h = stableHash(`digilocker-fields:${usid}`);
  return {
    family_income_annual_paise: 12000000 + (h % 25) * 1000000,
    st_or_pvtg_status: h % 10 === 0 ? 'PVTG' : 'ST',
    class_level: 9 + (h % 4),
  };
}
app.get('/digilocker/verify', (req, res) => {
  const usid = String(req.query.usid || '');
  const ok = decide(usid, 'digilocker', 0.875);
  if (ok) {
    res.json(verify(true, { system: 'DigiLocker-proxy',
      note: 'sandbox fallback — swap for live API Setu in TASK 6.1',
      documents: ['caste_certificate', 'income_certificate', 'domicile'],
      fields: digilockerFields(usid) }));
    return;
  }
  res.json(verify(false, { system: 'DigiLocker-proxy',
    reason_code: 'DOCUMENT_MISMATCH',
    note: 'no matching issued document for this USID' }));
});

app.listen(PORT, () => console.log(`govsim listening on :${PORT} (chaos=${CHAOS_ENABLED})`));
