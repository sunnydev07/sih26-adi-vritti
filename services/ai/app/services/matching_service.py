"""Fuzzy match scorer: Jaro-Winkler on folded names + weighted 7-field score."""

from __future__ import annotations

import unicodedata

WEIGHTS = {
    "name": 0.35, "dob": 0.20, "gender": 0.05, "guardian": 0.15,
    "institution": 0.10, "bankLast4": 0.05, "district": 0.10,
}


def fold(name: str | None) -> str:
    if not name:
        return ""
    n = unicodedata.normalize("NFKD", name.lower())
    n = "".join(c for c in n if not unicodedata.combining(c))
    n = "".join(c if c.isalpha() or c == " " else " " for c in n)
    n = " ".join(n.split())
    return (
        n.replace("meena", "mina")
        .replace("suneeta", "sunita")
        .replace("choudhary", "chaudhary")
        .replace("chaudhari", "chaudhary")
    )


def jaro_winkler(s1: str, s2: str) -> float:
    """Jaro-Winkler similarity in [0, 1].

    Kept equivalent to ``UsidResolver.jaroWinkler`` in the Core service,
    including the three fixes that were made only on the Java side:

    * emptiness is checked BEFORE equality, so two blank inputs score 0.0 rather
      than a perfect 1.0. This mattered in ``score()``: both records folding to
      an empty ``guardian_name`` earned the full 0.15-weighted guardian credit,
      so two people with no guardian on file scored higher on that field than
      two with matching ones.
    * the match window is floored at 0, so short strings do not compute a
      negative distance and match nothing (``jaro_winkler("ab", "ba")`` was 0.0);
    * the transposition scan is bounds-checked; it could index past the end of
      the flag list.

    Two further changes beyond the guards: the prefix bonus counts a
    **contiguous** prefix instead of positional matches (``zip`` with an early
    ``break``), and the result is rounded to 3dp like the Java side. Both matter
    because the score is compared against ``needsHumanReview`` thresholds, so an
    inflated number reads as "auto-resolved" when it should be "human review".
    """
    if not s1 or not s2:
        return 0.0
    if s1 == s2:
        return 1.0

    match_dist = max(max(len(s1), len(s2)) // 2 - 1, 0)
    m1, m2 = [False] * len(s1), [False] * len(s2)
    matches = 0
    for i, c in enumerate(s1):
        lo, hi = max(0, i - match_dist), min(i + match_dist + 1, len(s2))
        for j in range(lo, hi):
            if not m2[j] and c == s2[j]:
                m1[i] = m2[j] = True
                matches += 1
                break
    if matches == 0:
        return 0.0

    t = k = 0
    for i, used in enumerate(m1):
        if used:
            while k < len(m2) and not m2[k]:
                k += 1
            if k >= len(m2):
                break
            if s1[i] != s2[k]:
                t += 1
            k += 1

    m = float(matches)
    jaro = (m / len(s1) + m / len(s2) + (m - t / 2) / m) / 3
    prefix = 0
    for a, b in zip(s1[:4], s2[:4]):
        if a != b:
            break
        prefix += 1
    score = jaro + prefix * 0.1 * (1 - jaro)
    return min(1.0, round(score, 3))


def agree(a: str | None, b: str | None) -> float:
    if not a or not b:
        return 0.0
    return 1.0 if a.lower() == b.lower() else 0.0


def score(a: dict, b: dict) -> tuple[float, dict[str, float]]:
    parts = {
        "name": jaro_winkler(fold(a.get("full_name")), fold(b.get("full_name"))),
        "dob": agree(a.get("dob"), b.get("dob")),
        "gender": agree(a.get("gender"), b.get("gender")),
        "guardian": jaro_winkler(fold(a.get("guardian_name")), fold(b.get("guardian_name"))),
        "institution": agree(a.get("institution_code"), b.get("institution_code")),
        "bankLast4": agree(a.get("bank_account_last4"), b.get("bank_account_last4")),
        "district": agree(a.get("district"), b.get("district")),
    }
    total = round(sum(parts[k] * WEIGHTS[k] for k in parts), 3)
    return total, parts
