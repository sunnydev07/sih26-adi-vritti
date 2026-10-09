"""
Adi-Vritti Synthetic Data Generator.

Fixed seed (26238 = PS number) for reproducible results during judging.
NEVER uses real PII — all names/IDs are invented.

Usage:
    python generate.py [--demo] [--out ./output]

    --demo   small volumes for fast local iteration
    default  full volumes per spec (50k students, 18k applications, ...)

Outputs (into <out>/):
    students.csv, nsp_applications.csv, sfmp_records.csv, nos_records.csv,
    identity_puzzles.csv, duplicate_beneficiaries.csv, failed_disbursements.csv,
    seed.sql      (INSERT scripts for Postgres)
    fixtures.json (small hand-picked demo fixtures, incl. the E2E demo student)
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import hmac
import json
import random
import sys
import uuid
from datetime import date, timedelta
from pathlib import Path

SEED = 26238  # PS number as seed

# Deterministic namespace for seeded UUIDs: re-runs emit identical keys, so
# seed.sql is stable across regenerations and idempotent on re-apply.
SEED_NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "https://adivritti.in/synthetic-seed")

# Dev-stack vault material. These are the SAME published dev-only defaults as
# infra/docker-compose.yml and application-dev.yml, used ONLY to derive seeded
# reference keys that the dev Core build resolves. Never real secrets.
_DEV_VAULT_HMAC_KEY_B64 = "ZGV2LW9ubHktaG1hYy1rZXktMzItYnl0ZXMtbG9uZyE="
_DEV_VAULT_KEY_ID = "v1"
_DEV_GAP_HMAC_SALT = "dev-salt-rotate-in-prod"


def vault_ref_key(aadhaar_ref: str) -> str:
    """Derive the AVR1:<keyId>:<hex> reference key AadhaarVault would store.

    The generator never sees a real Aadhaar number (only synthetic
    ``ref_key`` tokens), so this HMACs the token under the dev vault key.
    Shape matches AadhaarVault.referenceKey exactly; Core's
    findByAadhaarRefKey reads $.aadhaarRefKey, which is where seed.sql puts it.
    """
    key = base64.b64decode(_DEV_VAULT_HMAC_KEY_B64)
    digest = hmac.new(key, aadhaar_ref.encode(), hashlib.sha256).hexdigest()
    return f"AVR1:{_DEV_VAULT_KEY_ID}:{digest}"


def gap_hashed_key(aadhaar_ref: str) -> str:
    """Coverage-candidate hashed key under the dev GAP_HMAC_SALT."""
    return hmac.new(_DEV_GAP_HMAC_SALT.encode(), aadhaar_ref.encode(),
                    hashlib.sha256).hexdigest()


def academic_year(today: date | None = None) -> str:
    """Current Indian academic year as 'YYYY-YY' (April-March).

    Derived from the wall clock so seeded years never go stale. The rules
    tree is versioned per AY, so rolling into a new AY means shipping a new
    rules folder — the seed follows the same clock.
    """
    today = today or date.today()
    start = today.year if today.month >= 4 else today.year - 1
    return f"{start}-{str(start + 1)[-2:]}"

STATES = {
    "Madhya Pradesh": ["Mandla", "Jhabua", "Dindori"],
    "Jharkhand": ["Gumla", "Simdega", "Khunti"],
    "Odisha": ["Koraput", "Malkangiri", "Rayagada"],
    "Chhattisgarh": ["Bastar", "Dantewada", "Surguja"],
}

# Tribal surnames pool (invented persons, real community names for realism)
SURNAMES = [
    "Meena", "Mina", "Munda", "Oraon", "Bhil", "Gond", "Baiga", "Saharia",
    "Korku", "Ho", "Santhal", "Bhumia", "Parja", "Koya", "Dorla", "Halba",
]
FIRST_F = ["Sunita", "Meena", "Phulo", "Jhano", "Budhni", "Sukri", "Munni", "Lalita", "Gangi", "Rupni"]
FIRST_M = ["Birsa", "Soma", "Mangru", "Deva", "Lakhan", "Sukhram", "Jitan", "Ramesh", "Mohan", "Kanhai"]
GUARDIANS_F = ["Sukhi Devi", "Mango Devi", "Fulmani", "Budhni Devi", "Somari"]
GUARDIANS_M = ["Soma Ram", "Mangru Ram", "Deva Ram", "Lakhan Singh", "Budhram"]

# Indic transliteration folding variants: same person, different spellings
NAME_VARIANTS = {
    "Meena": ["Meena", "Mina", "मीना"],
    "Sunita": ["Sunita", "Suneeta", "सुनीता"],
    "Mohammed": ["Mohammed", "Mohammad", "Md."],
    "Chaudhary": ["Chaudhary", "Choudhary", "Chaudhari"],
}

PFMS_FAILURE_TAXONOMY = [
    ("E001_AADHAAR_NOT_SEEDED", 0.30, "Aadhaar not seeded with bank account",
     "Visit the nearest bank branch with Aadhaar card and request Aadhaar seeding (NPCI mapping)."),
    ("E002_ACCOUNT_DORMANT", 0.20, "Bank account dormant/inactive",
     "One deposit or withdrawal reactivates the account. Visit the branch or use micro-ATM."),
    ("E003_IFSC_CHANGED", 0.15, "IFSC changed after bank merger",
     "Update the IFSC to the new merged-bank code in the application (1-tap update), then re-trigger payment."),
    ("E004_NAME_MISMATCH", 0.20, "Name mismatch between Aadhaar and bank account",
     "Correct the differing name token via the correction route shown, then re-verify the claim."),
    ("E005_FUNDS_NOT_RELEASED", 0.10, "Sanction issued but funds not released by ministry",
     "Escalate with the sanction order number at the district nodal office; track PFMS stage."),
    ("E006_OTHER", 0.05, "Other / technical failure",
     "Retry after 48 hours; if it persists raise a grievance with the PFMS reference number."),
]


def ref_key(rng: random.Random, prefix: str, n: int) -> str:
    digest = hashlib.sha256(f"{prefix}-{n}-{rng.random()}".encode()).hexdigest()[:16]
    return f"{prefix}-{digest}"


def unique_id(rng: random.Random, seen: set[str], make) -> str:
    """Draw from make() until an unseen id appears.

    The OTR / SFMP / PFMS id spaces are random draws, so without this full
    volumes collide with non-trivial probability. Bounded redraw keeps the
    generator total while guaranteeing uniqueness.
    """
    for _ in range(100):
        v = make()
        if v not in seen:
            seen.add(v)
            return v
    raise RuntimeError("id space exhausted while generating synthetic ids")


def pick_name(rng: random.Random) -> tuple[str, str]:
    if rng.random() < 0.5:
        first = rng.choice(FIRST_F)
        guardian = rng.choice(GUARDIANS_M)
    else:
        first = rng.choice(FIRST_M)
        guardian = rng.choice(GUARDIANS_F)
    return f"{first} {rng.choice(SURNAMES)}", guardian


def variant_name(rng: random.Random, name: str) -> str:
    for canon, variants in NAME_VARIANTS.items():
        if canon in name:
            return name.replace(canon, rng.choice(variants))
    parts = name.split()
    if len(parts) == 2 and rng.random() < 0.5:
        # insert/tweak middle initial: "Sunita Meena" -> "Sunita K. Mina"
        mid = rng.choice("KMRSDB")
        last = rng.choice(NAME_VARIANTS.get(parts[1], [parts[1]]))
        return f"{parts[0]} {mid}. {last}"
    return name


def gen_students(rng: random.Random, n: int, start_id: int = 1,
                 ref_year: int | None = None):
    ref_year = ref_year if ref_year is not None else date.today().year
    rows = []
    # School codes are derived from the school NAME (stable map), so joins on
    # school_code vs school name always agree. (A row counter mod 300 mapped
    # one code to many schools.)
    school_codes: dict[str, str] = {}
    for i in range(start_id, start_id + n):
        state = rng.choice(list(STATES))
        district = rng.choice(STATES[state])
        school = f"Govt HS {rng.choice(['Bichhiya', 'Ghughri', 'Narayanpur', 'Kondagaon', 'Boirdadar'])} {district}"
        code = school_codes.get(school)
        if code is None:
            code = f"SCH-{len(school_codes) + 1:04d}"
            school_codes[school] = code
        name, guardian = pick_name(rng)
        # Class first, then an age-consistent DOB (age ~= class + 5 .. class + 6.5
        # on 1 April of ref_year), so age/class eligibility reads stay sane.
        cls = rng.randint(9, 12)
        dob = date(ref_year - (cls + 6), 1, 1) + timedelta(days=rng.randint(0, 545))
        rows.append({
            "student_id": f"UDISE-{i:07d}",
            "full_name": name,
            "dob": dob.isoformat(),
            "gender": rng.choice(["female", "male"]),
            "guardian_name": guardian,
            "state": state,
            "district": district,
            "block": f"Block-{rng.randint(1, 8)}",
            "school": school,
            "school_code": code,
            "class_level": cls,
            "pvtg_status": "true" if rng.random() < 0.08 else "false",
            "aadhaar_ref_key": ref_key(rng, "AVR", i),
            # Fixed up after the NSP draw: True iff the student owns an NSP row,
            # so the flag can never contradict nsp_applications.csv.
            "has_nsp_application": False,
        })
    return rows


# Stage -> owning actor + SLA budget, mirroring SlaCalculator (Java) so seeded
# sla_deadline values produce the same breach math as live rows.
_STAGE_ACTOR = {
    "submitted": "institute",
    "institute_verification": "institute",
    "district_nodal": "district_nodal",
    "state_dept": "state_dept",
    "ministry": "ministry",
    "pfms_payment": "pfms",
    "disbursed": "pfms",
}
_SLA_DAYS = {
    "submitted": 2, "institute_verification": 7, "district_nodal": 10,
    "state_dept": 10, "ministry": 15, "pfms_payment": 7, "disbursed": 0,
}


def write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--demo", action="store_true")
    ap.add_argument("--out", default=str(Path(__file__).parent / "output"))
    args = ap.parse_args()

    rng = random.Random(SEED)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    ref_year = date.today().year
    ay = academic_year()
    nos_years = (str(ref_year - 2), str(ref_year - 1), str(ref_year))

    scale = 1 / 100 if args.demo else 1
    n_students = max(200, int(50_000 * scale))
    n_nsp = max(100, int(18_000 * scale))
    n_sfmp = max(20, int(400 * scale))
    n_nos = 60  # keep full — tiny anyway
    n_puzzles = max(60, int(1_200 * scale))
    n_dupes = 40
    n_failures = max(60, int(900 * scale))

    students = gen_students(rng, n_students, ref_year=ref_year)

    schemes = ["PRE_MATRIC", "POST_MATRIC", "TOP_CLASS"]
    stages = ["submitted", "institute_verification", "district_nodal",
              "state_dept", "ministry", "pfms_payment", "disbursed"]
    seen_otr: set[str] = set()
    nsp = []
    for i in range(n_nsp):
        s = rng.choice(students)
        nsp.append({
            "otr_id": unique_id(rng, seen_otr,
                                lambda: f"{rng.randint(10**13, 10**14 - 1)}"),
            "student_id": s["student_id"],
            "scheme": rng.choice(schemes),
            "academic_year": ay,
            "stage": rng.choice(stages),
            # 15% legacy rows miss Aadhaar ref
            "aadhaar_ref_key": "" if rng.random() < 0.15 else s["aadhaar_ref_key"],
            "applicant_name": variant_name(rng, s["full_name"]) if rng.random() < 0.3 else s["full_name"],
            "sanctioned_amount_paise": rng.choice([350000, 700000, 1200000, 2500000]),
        })
    write_csv(out / "nsp_applications.csv", nsp)

    # The coverage-gap flag is DERIVED from NSP membership, so the CSV column,
    # the NSP table and the seeded outreach_status can never disagree.
    nsp_ids = {r["student_id"] for r in nsp}
    for s in students:
        s["has_nsp_application"] = s["student_id"] in nsp_ids
    write_csv(out / "students.csv", students)

    seen_sfmp: set[str] = set()
    sfmp = []
    for i in range(n_sfmp):
        s = rng.choice(students)
        sfmp.append({
            "scholar_id": unique_id(rng, seen_sfmp,
                                    lambda: f"SFMP-{rng.randint(100000, 999999)}"),
            # student_id is the join key back to students.csv and the seed's
            # scholar_system_link rows (same person, different ID scheme).
            "student_id": s["student_id"],
            "fellow_name": variant_name(rng, s["full_name"]),
            "scheme": "NFST",
            "fellowship_amount_paise": 3780000,
            "aadhaar_ref_key": "" if rng.random() < 0.15 else s["aadhaar_ref_key"],
        })
    write_csv(out / "sfmp_records.csv", sfmp)

    nos = []
    for year in nos_years:
        for i in range(n_nos // 3):
            s = rng.choice(students)
            nos.append({
                "candidate_id": f"NOS-{year}-{i + 1:03d}",
                "selection_year": year,
                "candidate_name": s["full_name"],
                "category": "PVTG" if (s["pvtg_status"] == "true" and i < 3) else "ST",
                "country": rng.choice(["USA", "UK", "Canada", "Australia"]),
            })
    write_csv(out / "nos_records.csv", nos)

    puzzles = []
    for i in range(n_puzzles):
        s = rng.choice(students)
        kind = rng.choice(["name_variant", "transliteration", "missing_aadhaar", "date_format"])
        puzzles.append({
            "puzzle_id": f"PUZ-{i + 1:05d}",
            "student_id": s["student_id"],
            "kind": kind,
            "nsp_name": variant_name(rng, s["full_name"]),
            "sfmp_name": variant_name(rng, s["full_name"]),
            "aadhaar_present": "false" if kind == "missing_aadhaar" else "true",
            "same_person": "true",
        })
    write_csv(out / "identity_puzzles.csv", puzzles)

    dupes = []
    for i in range(n_dupes):
        s = rng.choice(students)
        dupes.append({
            "dupe_id": f"DUP-{i + 1:03d}",
            "student_id": s["student_id"],
            "scheme_a": "POST_MATRIC",
            "scheme_b": rng.choice(["TOP_CLASS", "NFST"]),
            "note": "same person receiving benefits from multiple schemes",
        })
    write_csv(out / "duplicate_beneficiaries.csv", dupes)

    failures = []
    codes = [c for c, _, _, _ in PFMS_FAILURE_TAXONOMY]
    weights = [w for _, w, _, _ in PFMS_FAILURE_TAXONOMY]
    seen_pfms: set[str] = set()
    for i in range(n_failures):
        s = rng.choice(students)
        # ~25% clean draws become paid rows downstream (mirrors govsim's null
        # rate); without a clean draw the seed can never show the success path.
        code = None if rng.random() < 0.25 else rng.choices(codes, weights=weights)[0]
        failures.append({
            "pfms_ref": unique_id(rng, seen_pfms,
                                  lambda: f"PFMS-{rng.randint(10**9, 10**10 - 1)}"),
            "student_id": s["student_id"],
            "scheme": rng.choice(schemes),
            "sanctioned_amount_paise": rng.choice([350000, 700000, 1200000]),
            "failure_code": code,
        })
    write_csv(out / "failed_disbursements.csv", failures)

    # SQL seed: scholars + every read model the service layer queries.
    #
    # Previously only `scholar` rows were emitted, so the dashboard, claims,
    # applications, disbursements and coverage-gap endpoints had nothing to
    # read in a seeded dev stack, and the two real defects below made even the
    # scholar rows unusable:
    #   1. `uuid_generate_v4()` needs the uuid-ossp extension, which the
    #      pgvector image path does not guarantee (V1 uses gen_random_uuid()
    #      precisely to avoid it). seed.sql now uses gen_random_uuid() for
    #      fresh rows and FIXED UUIDs for a small deterministic demo set.
    #   2. the vault key was stored under "ref", while
    #      ScholarRepository.findByAadhaarRefKey reads $.aadhaarRefKey, so the
    #      duplicate lookup could never match seeded data.
    #
    # Determinism: row UUIDs are uuid5(SEED namespace, student_id), so a
    # re-run emits the same keys. Inserts are ON CONFLICT DO NOTHING, so the
    # file is idempotent. Reference keys are HMAC-SHA256 under the dev vault
    # key (the same shape AadhaarVault.referenceKey produces), so seeded
    # scholars resolve through the same lookup path as live data.
    # Claim payloads CANNOT be produced here: claim.value_encrypted is
    # AES-256-GCM under CLAIM_VAULT_KEY and this stdlib-only generator has no
    # AES. Use POST /v1/verify to populate the wallet instead; fixtures.json
    # carries the demo USIDs for that walkthrough.
    demo_usids = [str(uuid.uuid5(SEED_NAMESPACE, f"demo-scholar-{i}")) for i in range(5)]
    # Full mode draws NSP/failure rows from the whole 50k pool while the base
    # scholar window covers only the first 5000: seed the UNION so no
    # application/disbursement is silently skipped (and say so on stdout).
    student_by_id = {s["student_id"]: s for s in students}
    base_window = students[: 500 if args.demo else 5000]
    base_ids = {s["student_id"] for s in base_window}
    referenced = ({r["student_id"] for r in nsp}
                  | {r["student_id"] for r in failures})
    extra_ids = sorted(referenced - base_ids)
    seed_rows = base_window + [student_by_id[sid] for sid in extra_ids]
    if extra_ids:
        print(f"seed: +{len(extra_ids)} scholars referenced from outside the base window")
    seed_usid = {}
    for idx, s in enumerate(seed_rows):
        if idx < len(demo_usids):
            seed_usid[s["student_id"]] = demo_usids[idx]
        else:
            seed_usid[s["student_id"]] = str(uuid.uuid5(SEED_NAMESPACE, s["student_id"]))
    with (out / "seed.sql").open("w", encoding="utf-8") as f:
        f.write("-- Adi-Vritti synthetic seed. Generated with SEED=26238. No real PII.\n")
        f.write("-- Idempotent: safe to re-run (ON CONFLICT DO NOTHING).\n")
        for s in seed_rows:
            ref = vault_ref_key(s["aadhaar_ref_key"])
            # Escape ONCE, on the final SQL literal: json.dumps never emits a
            # single quote, so pre-escaping the name would store O''Brien.
            demo = json.dumps({
                "name": s["full_name"],
                "aadhaarRefKey": ref,
                "district": s["district"],
                "state": s["state"],
                "block": s["block"],
                "school": s["school"],
                "school_code": s["school_code"],
                "class_level": s["class_level"],
                "gender": s["gender"],
                "pvtg_status": s["pvtg_status"] == "true",
                "guardian_name": s["guardian_name"],
            }, ensure_ascii=False).replace("'", "''")
            f.write(
                "INSERT INTO scholar (usid, demographics) VALUES "
                f"('{seed_usid[s['student_id']]}', '{demo}'::jsonb) "
                "ON CONFLICT (usid) DO NOTHING;\n"
            )

        # DPDP consent artefacts: Core denies personal-data reads (claims,
        # dashboard, disbursements, JAGO tools) without a live purpose-bound
        # grant, so every seeded scholar carries the three read-path purposes,
        # granted by the scholar. Consent ids are uuid5 over (usid, purpose):
        # deterministic across regenerations, so re-applying the file hits the
        # PK and ON CONFLICT skips instead of duplicating rows. Seeded scholars
        # take the adult gate path (seed demographics carry no dob); the minor
        # / guardian-consent path is covered by Core unit tests.
        for s in seed_rows:
            usid = seed_usid[s["student_id"]]
            granted_by = s["full_name"].replace("'", "''")
            for purpose in ("application_submission", "claim_verification",
                            "disbursement_tracking"):
                cid = uuid.uuid5(SEED_NAMESPACE, f"consent-{usid}-{purpose}")
                f.write(
                    "INSERT INTO consent_artefact (id, usid, purpose, scope, granted_by) VALUES "
                    f"('{cid}', '{usid}', '{purpose}', '[]'::jsonb, '{granted_by}') "
                    "ON CONFLICT (id) DO NOTHING;\n"
                )

        # Identity links: NSP for every seeded scholar; SFMP joined from the
        # ACTUAL sfmp feed on student_id (first row wins), so the DB link id
        # is byte-identical to the CSV scholar_id the join narrative needs.
        # Students with no SFMP row get no SFMP link — synthesising a new id
        # format here made every CSV-vs-DB join miss 100%.
        sfmp_by_student: dict[str, str] = {}
        for r in sfmp:
            sfmp_by_student.setdefault(r["student_id"], r["scholar_id"])
        for s in seed_rows:
            usid = seed_usid[s["student_id"]]
            f.write(
                "INSERT INTO scholar_system_link "
                "(id, usid, system_name, external_id, match_confidence, resolution_method) VALUES "
                f"(gen_random_uuid(), '{usid}', 'NSP', 'NSP-{s['student_id']}', 1.0, 'deterministic') "
                "ON CONFLICT (system_name, external_id) DO NOTHING;\n"
            )
            sid = sfmp_by_student.get(s["student_id"])
            if sid is not None:
                f.write(
                    "INSERT INTO scholar_system_link "
                    "(id, usid, system_name, external_id, match_confidence, resolution_method) VALUES "
                    f"(gen_random_uuid(), '{usid}', 'SFMP', '{sid}', 0.95, 'corroborated') "
                    "ON CONFLICT (system_name, external_id) DO NOTHING;\n"
                )
        # Applications mirror the NSP feed row-for-row: the REAL stage (not a
        # hardcoded 'submitted'), the owning actor mapped from the stage, and
        # an sla_deadline from the same per-stage budget SlaCalculator uses —
        # so STP scores and the exception queue vary like live data. The
        # application table has no OTR/amount columns; the OTR id survives in
        # the deterministic row id (uuid5 over otr_id) for traceability.
        # Ids are deterministic, so re-applying the file is a true no-op.
        for row in nsp:
            usid = seed_usid.get(row["student_id"])
            if usid is None:
                continue
            app_id = uuid.uuid5(SEED_NAMESPACE, f"app-{row['otr_id']}")
            actor = _STAGE_ACTOR[row["stage"]]
            sla = _SLA_DAYS[row["stage"]]
            f.write(
                "INSERT INTO application "
                "(id, usid, scheme, academic_year, stage, current_actor, sla_deadline) VALUES "
                f"('{app_id}', '{usid}', '{row['scheme']}', '{ay}', "
                f"'{row['stage']}', '{actor}', NOW() + INTERVAL '{sla} days') "
                "ON CONFLICT (id) DO NOTHING;\n"
            )
        # Disbursements mirror the PFMS failure sample (failed rows carry the
        # taxonomy code; the null-code draw becomes a paid row). Row ids are
        # deterministic (uuid5 over pfms_ref), so re-applying the file cannot
        # duplicate rows the way gen_random_uuid() + bare ON CONFLICT did.
        fail_sample = failures[: 200 if args.demo else 900]
        for fail in fail_sample:
            usid = seed_usid.get(fail["student_id"])
            if usid is None:
                continue
            code = fail["failure_code"]
            disb_id = uuid.uuid5(SEED_NAMESPACE, f"disb-{fail['pfms_ref']}")
            if code:
                f.write(
                    "INSERT INTO disbursement "
                    "(id, usid, scheme, sanctioned_amount_paise, paid_amount_paise, "
                    "pfms_ref, failure_code, failure_reason, status) VALUES "
                    f"('{disb_id}', '{usid}', '{fail['scheme']}', "
                    f"{fail['sanctioned_amount_paise']}, 0, '{fail['pfms_ref']}', "
                    f"'{code}', 'Seeded PFMS failure {code}', 'failed') "
                    "ON CONFLICT (id) DO NOTHING;\n"
                )
            else:
                f.write(
                    "INSERT INTO disbursement "
                    "(id, usid, scheme, sanctioned_amount_paise, paid_amount_paise, "
                    "pfms_ref, failure_code, failure_reason, status) VALUES "
                    f"('{disb_id}', '{usid}', '{fail['scheme']}', "
                    f"{fail['sanctioned_amount_paise']}, {fail['sanctioned_amount_paise']}, "
                    f"'{fail['pfms_ref']}', NULL, NULL, 'paid') "
                    "ON CONFLICT (id) DO NOTHING;\n"
                )
        # Coverage candidates: the enrolled cohort per school, hashed under the
        # dev GAP_HMAC_SALT. Students with NO NSP record are the gap
        # ('unreached') the officer console aggregates; students WITH an NSP
        # record are emitted as 'reached', so Core can derive the enrolment
        # and applicant denominators from the same table instead of reporting
        # 0 (CoverageGapService: enrolled = all rows, gap = unreached subset).
        # Previously only unreached rows were emitted and pvtg_status was
        # hard-coded FALSE, so denominators were 0 and pvtg counts always 0.
        # (nsp_ids was computed right after the NSP draw so the students.csv
        # flag and this section share one definition of membership.)
        enrol_pool = students[: 200 if args.demo else 2000]
        by_id = {s["student_id"]: s for s in enrol_pool}
        gap_pool = [s for s in enrol_pool if s["student_id"] not in nsp_ids]
        reached_ids = sorted(nsp_ids & set(by_id))
        for s in gap_pool:
            hkey = gap_hashed_key(s["aadhaar_ref_key"])
            gender = "F" if s["gender"] == "female" else "M"
            pvtg = "TRUE" if s["pvtg_status"] == "true" else "FALSE"
            f.write(
                "INSERT INTO coverage_candidate "
                "(id, hashed_key, state, district, block, school, class_level, gender, "
                "pvtg_status, outreach_status) VALUES "
                f"(gen_random_uuid(), '{hkey}', '{s['state']}', '{s['district']}', "
                f"'{s['block']}', '{s['school']}', {s['class_level']}, '{gender}', {pvtg}, 'unreached') "
                "ON CONFLICT (hashed_key) DO NOTHING;\n"
            )
        for sid in reached_ids:
            s = by_id[sid]
            hkey = gap_hashed_key(s["aadhaar_ref_key"])
            gender = "F" if s["gender"] == "female" else "M"
            pvtg = "TRUE" if s["pvtg_status"] == "true" else "FALSE"
            f.write(
                "INSERT INTO coverage_candidate "
                "(id, hashed_key, state, district, block, school, class_level, gender, "
                "pvtg_status, outreach_status) VALUES "
                f"(gen_random_uuid(), '{hkey}', '{s['state']}', '{s['district']}', "
                f"'{s['block']}', '{s['school']}', {s['class_level']}, '{gender}', {pvtg}, 'reached') "
                "ON CONFLICT (hashed_key) DO NOTHING;\n"
            )

    # Small hand-picked E2E demo fixture (demo_usids anchor the first 5 seed rows)
    demo_student = students[0]
    fixtures = {
        "seed": SEED,
        "demo_usids": demo_usids,
        "demo_student": {
            "student_id": demo_student["student_id"],
            "usid": demo_usids[0],
            "full_name": demo_student["full_name"],
            "nsp_variant": variant_name(random.Random(SEED + 1), demo_student["full_name"]),
            "aadhaar_ref_key": vault_ref_key(demo_student["aadhaar_ref_key"]),
            "district": demo_student["district"],
            "school": demo_student["school"],
        },
        "pfms_taxonomy": [
            {"code": c, "cause": cause, "fix": fix}
            for c, _, cause, fix in PFMS_FAILURE_TAXONOMY
        ],
        "counts": {
            "students": n_students,
            "nsp_applications": n_nsp,
            "sfmp_records": n_sfmp,
            "nos_records": len(nos),
            "identity_puzzles": n_puzzles,
            "duplicate_beneficiaries": n_dupes,
            "failed_disbursements": n_failures,
        },
    }
    (out / "fixtures.json").write_text(json.dumps(fixtures, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"done: {n_students} students, {n_nsp} nsp, {n_sfmp} sfmp, "
          f"{len(nos)} nos, {n_puzzles} puzzles, {n_dupes} dupes, "
          f"{n_failures} failures -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
