/**
 * GoVSim — 7 hostile mock government systems.
 *
 * Deliberately dirty + flaky so the Core adapters prove resilience is real:
 * - 8% random HTTP 500s (disable with CHAOS_ENABLED=false)
 * - 1.5-4s latency spikes, occasional truncated JSON, 20 RPM rate limit
 * - Name variants across systems, missing Aadhaar on legacy rows,
 *   duplicate beneficiaries, UDISE+ students with no NSP record (the gap)
 *
 * Full quirk catalogue: docs/specs/govsim-contracts.md
 */
'use strict';

const express = require('express');

const PORT = process.env.PORT || 4000;
const CHAOS_ENABLED = process.env.CHAOS_ENABLED !== 'false';
const CHAOS_ERROR_RATE = parseFloat(process.env.CHAOS_ERROR_RATE || '0.08');

// --- Deterministic PRNG (mulberry32, seed = PS number) ---
let state = 26238;
function rnd() {
  state |= 0; state = (state + 0x6d2b79f5) | 0;
  let t = Math.imul(state ^ (state >>> 15), 1 | state);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];

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

// --- Chaos middleware ---
const hits = new Map(); // ip -> timestamps (20 RPM window)
function chaos(req, res, next) {
  if (req.headers['x-chaos'] === 'off') return next();
  const now = Date.now();
  const window = (hits.get(req.ip) || []).filter((t) => now - t < 60_000);
  window.push(now);
  hits.set(req.ip, window);
  if (window.length > 20) return res.status(429).json({ error: 'rate_limited', retry_after_seconds: 30 });
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
app.use(chaos);

const verify = (verified, extra = {}) => ({ verified, ...extra });

// NSP — SOAP-ish XML flavour, OTR lookup, legacy rows miss Aadhaar 15%
app.get('/nsp/verify', (req, res) => {
  const ok = req.query.usid === DEMO_STUDENT.usid || rnd() > 0.25;
  res.json(verify(ok, { system: 'NSP', name: DEMO_STUDENT.nspName, otr_id: DEMO_STUDENT.otrId,
    aadhaar_ref_key: rnd() < 0.15 ? null : DEMO_STUDENT.aadhaarRefKey, format: 'soap-xml-ish' }));
});
app.get('/nsp/applications', (req, res) => {
  res.json({ system: 'NSP', records: [{ otr_id: DEMO_STUDENT.otrId, name: DEMO_STUDENT.nspName,
    scheme: 'PRE_MATRIC', stage: 'district_nodal', sanctioned_amount_paise: 700000 }] });
});

// SFMP (Canara Bank) — flat CSV-in-JSON, different ID scheme + spelling
app.get('/sfmp/verify', (req, res) => {
  const ok = req.query.usid === DEMO_STUDENT.usid || rnd() > 0.35;
  res.json(verify(ok, { system: 'SFMP', name: DEMO_STUDENT.sfmpName, format: 'csv-in-json' }));
});
app.get('/sfmp/records', (req, res) => {
  res.json({ system: 'SFMP', csv: 'scholar_id,name,scheme\nSFMP-482913,"Mina, Sunita K.",NFST' });
});

// NOS — paginated, DIFFERENT date format on each page
app.get('/nos/verify', (req, res) => {
  res.json(verify(rnd() > 0.5, { system: 'NOS', name: DEMO_STUDENT.nosName }));
});
app.get('/nos/records', (req, res) => {
  const page = parseInt(req.query.page || '1', 10);
  res.json({ system: 'NOS', page, per_page: 20,
    selection_date: NOS_DATES[(page - 1) % NOS_DATES.length],
    date_format_note: 'page 1: DD/MM/YYYY, page 2: YYYY-MM-DD, page 3: epoch — good luck',
    records: [{ candidate: DEMO_STUDENT.nosName, year: '2024', category: 'ST' }] });
});

// UDISE+/APAAR — enrolment, AISHE codes; includes students with ZERO NSP record
app.get('/udise/verify', (req, res) => {
  res.json(verify(true, { system: 'UDISE+', enrolled: true, aishe_code: 'AISHE-MP-042' }));
});
app.get('/udise/students', (req, res) => {
  res.json({ system: 'UDISE+', district: req.query.district || 'Mandla',
    students: [{ name: DEMO_STUDENT.nspName, school: 'Govt HS Bichhiya, Mandla', class: 9, has_nsp_record: true },
      { name: 'Phulo Gond', school: 'Govt HS Bichhiya, Mandla', class: 9, has_nsp_record: false }] });
});

// PFMS/DBT — payment status + full rejection taxonomy
const PFMS_CODES = ['E001_AADHAAR_NOT_SEEDED', 'E002_ACCOUNT_DORMANT', 'E003_IFSC_CHANGED',
  'E004_NAME_MISMATCH', 'E005_FUNDS_NOT_RELEASED', 'E006_OTHER', null, null];
app.get('/pfms/verify', (req, res) => {
  res.json(verify(true, { system: 'PFMS', account_valid: true }));
});
app.get('/pfms/status', (req, res) => {
  res.json({ system: 'PFMS', pfms_ref: 'PFMS-9988776655', status: 'failed',
    failure_code: pick(PFMS_CODES), sanctioned_amount_paise: 700000, paid_amount_paise: 0 });
});

// UGC-NTA — NET/JRF results
app.get('/ugc-nta/verify', (req, res) => {
  res.json(verify(rnd() > 0.6, { system: 'UGC-NTA', exam: 'NET-JRF' }));
});

// DigiLocker proxy — stands in when the API Setu sandbox is down
app.get('/digilocker/verify', (req, res) => {
  res.json(verify(true, { system: 'DigiLocker-proxy',
    note: 'sandbox fallback — swap for live API Setu in TASK 6.1',
    documents: ['caste_certificate', 'income_certificate', 'domicile'] }));
});

app.get('/health', (req, res) => res.json({ status: 'ok', service: 'govsim', chaos: CHAOS_ENABLED }));

app.listen(PORT, () => console.log(`govsim listening on :${PORT} (chaos=${CHAOS_ENABLED})`));
