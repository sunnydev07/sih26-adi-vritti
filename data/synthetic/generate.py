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
import csv
import hashlib
import json
import random
import sys
from datetime import date, timedelta
from pathlib import Path

SEED = 26238  # PS number as seed

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


def gen_students(rng: random.Random, n: int, start_id: int = 1):
    rows = []
    school_seq = 0
    for i in range(start_id, start_id + n):
        state = rng.choice(list(STATES))
        district = rng.choice(STATES[state])
        school_seq += 1
        school = f"Govt HS {rng.choice(['Bichhiya', 'Ghughri', 'Narayanpur', 'Kondagaon', 'Boirdadar'])} {district}"
        name, guardian = pick_name(rng)
        dob = date(2008, 1, 1) + timedelta(days=rng.randint(0, 365 * 8))
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
            "school_code": f"SCH-{school_seq % 300 + 1:04d}",
            "class_level": rng.randint(9, 12),
            "pvtg_status": rng.random() < 0.08,
            "aadhaar_ref_key": ref_key(rng, "AVR", i),
            "has_nsp_application": rng.random() < 0.55,  # ~45% gap
        })
    return rows


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

    scale = 1 / 100 if args.demo else 1
    n_students = max(200, int(50_000 * scale))
    n_nsp = max(100, int(18_000 * scale))
    n_sfmp = max(20, int(400 * scale))
    n_nos = 60  # keep full — tiny anyway
    n_puzzles = max(60, int(1_200 * scale))
    n_dupes = 40
    n_failures = max(60, int(900 * scale))

    students = gen_students(rng, n_students)
    write_csv(out / "students.csv", students)

    schemes = ["PRE_MATRIC", "POST_MATRIC", "TOP_CLASS"]
    stages = ["submitted", "institute_verification", "district_nodal",
              "state_dept", "ministry", "pfms_payment", "disbursed"]
    nsp = []
    for i in range(n_nsp):
        s = rng.choice(students)
        nsp.append({
            "otr_id": f"{rng.randint(10**13, 10**14 - 1)}",
            "student_id": s["student_id"],
            "scheme": rng.choice(schemes),
            "academic_year": "2026-27",
            "stage": rng.choice(stages),
            # 15% legacy rows miss Aadhaar ref
            "aadhaar_ref_key": "" if rng.random() < 0.15 else s["aadhaar_ref_key"],
            "applicant_name": variant_name(rng, s["full_name"]) if rng.random() < 0.3 else s["full_name"],
            "sanctioned_amount_paise": rng.choice([350000, 700000, 1200000, 2500000]),
        })
    write_csv(out / "nsp_applications.csv", nsp)

    sfmp = []
    for i in range(n_sfmp):
        s = rng.choice(students)
        sfmp.append({
            "scholar_id": f"SFMP-{rng.randint(100000, 999999)}",
            "fellow_name": variant_name(rng, s["full_name"]),
            "scheme": "NFST",
            "fellowship_amount_paise": 3780000,
            "aadhaar_ref_key": "" if rng.random() < 0.15 else s["aadhaar_ref_key"],
        })
    write_csv(out / "sfmp_records.csv", sfmp)

    nos = []
    for year in ("2023", "2024", "2025"):
        for i in range(n_nos // 3):
            s = rng.choice(students)
            nos.append({
                "candidate_id": f"NOS-{year}-{i + 1:03d}",
                "selection_year": year,
                "candidate_name": s["full_name"],
                "category": "PVTG" if (s["pvtg_status"] and i < 3) else "ST",
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
    for i in range(n_failures):
        s = rng.choice(students)
        failures.append({
            "pfms_ref": f"PFMS-{rng.randint(10**9, 10**10 - 1)}",
            "student_id": s["student_id"],
            "scheme": rng.choice(schemes),
            "sanctioned_amount_paise": rng.choice([350000, 700000, 1200000]),
            "failure_code": rng.choices(codes, weights=weights)[0],
        })
    write_csv(out / "failed_disbursements.csv", failures)

    # SQL inserts (scholars only — keeps seed.sql small; bulk via COPY in prod)
    with (out / "seed.sql").open("w", encoding="utf-8") as f:
        f.write("-- Adi-Vritti synthetic seed. Generated with SEED=26238. No real PII.\n")
        for s in students[: 500 if args.demo else 5000]:
            name = s["full_name"].replace("'", "''")
            f.write(
                "INSERT INTO scholar (usid, demographics) VALUES "
                f"(uuid_generate_v4(), '{{\"name\": \"{name}\", \"ref\": \"{s['aadhaar_ref_key']}\"}}');\n"
            )

    # Small hand-picked E2E demo fixture
    demo_student = students[0]
    fixtures = {
        "seed": SEED,
        "demo_student": {
            "student_id": demo_student["student_id"],
            "full_name": demo_student["full_name"],
            "nsp_variant": variant_name(random.Random(SEED + 1), demo_student["full_name"]),
            "aadhaar_ref_key": demo_student["aadhaar_ref_key"],
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
