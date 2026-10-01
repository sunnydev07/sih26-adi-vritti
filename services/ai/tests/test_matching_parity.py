"""Parity with services/core UsidResolver.

The Python matcher was written from the same spec as the Java resolver but never
received the three fixes the Java side documents in its own comments. These tests
pin the Java behaviour so the two cannot drift again silently.

Every value in JAVA_REFERENCE was read off ``UsidResolver.jaroWinkler`` and is
mirrored in ``UsidResolverTest.capturedValuesPinThePythonParityTable``, so a
change to either algorithm fails on both sides instead of silently forking.
"""

from __future__ import annotations

import pytest

from app.services import matching_service as ms


def test_two_empty_names_score_zero_not_a_perfect_match() -> None:
    # The defect: `if s1 == s2: return 1.0` ran BEFORE the empty guard, so two
    # records that both lack a guardian name earned the full 0.15-weighted
    # guardian credit. The Java resolver fixed exactly this.
    assert ms.jaro_winkler("", "") == 0.0
    assert ms.jaro_winkler("", "abc") == 0.0
    assert ms.jaro_winkler("abc", "") == 0.0


def test_identical_non_empty_names_still_score_one() -> None:
    assert ms.jaro_winkler("sunita meena", "sunita meena") == 1.0


def test_missing_guardian_does_not_inflate_the_total() -> None:
    base = {
        "full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
        "district": "Mandla", "institution_code": None, "bank_account_last4": None,
    }
    without_guardian, parts = ms.score(dict(base), dict(base))
    assert parts["guardian"] == 0.0, (
        "a missing guardian name must contribute nothing, not full credit"
    )
    with_guardian, _ = ms.score({**base, "guardian_name": "Sukhi Devi"},
                                {**base, "guardian_name": "Sukhi Devi"})
    assert without_guardian < with_guardian


def test_a_strong_match_hits_the_java_value() -> None:
    # Raw pair: UsidResolver scores this at 0.910. The Python build returned 0.921
    # because its prefix bonus counted positional matches rather than a contiguous
    # prefix.
    assert ms.jaro_winkler("sunita meena", "sunita k. mina") == pytest.approx(0.910, abs=0.001)

    # The same pair as score() actually sees it: both sides fold "meena" to "mina",
    # so this is the number the resolver compares to its human-review threshold.
    _, parts = ms.score(
        {"full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
         "district": "Mandla", "guardian_name": None},
        {"full_name": "Sunita K. Mina", "dob": "2012-05-01", "gender": "female",
         "district": "Mandla", "guardian_name": None},
    )
    assert parts["name"] == pytest.approx(0.969, abs=0.001)


@pytest.mark.parametrize("a,b", [
    ("meena", "mina"), ("phulo gond", "phoolo gund"), ("lakhon ho", "lacon ho"),
    ("budhni devi", "budhni"), ("", "x"), ("a", "b"), ("ab", "ac"),
])
def test_java_parity_pairs(a: str, b: str) -> None:
    """Values produced by UsidResolver.jaroWinkler for the same inputs."""
    expected = JAVA_REFERENCE[(a, b)]
    assert ms.jaro_winkler(a, b) == pytest.approx(expected, abs=0.001)


def test_short_strings_do_not_crash_or_match_nothing_needlessly() -> None:
    # `max(len)//2 - 1` went negative for 1- and 2-char strings, so the window
    # was empty and *no* pair could match. Java floors it at 0. Note "ab"/"ba"
    # is legitimately 0.0 even on the Java side (window 0 cannot see the swap),
    # so the assertion below uses a pair the floored window does reach.
    assert ms.jaro_winkler("a", "a") == 1.0
    assert ms.jaro_winkler("ab", "ac") == pytest.approx(0.700, abs=0.001)


def test_prefix_bonus_counts_a_contiguous_prefix_only() -> None:
    # 'm' and 'a' are both common, so a positional count inflated this; the
    # strings share a 4-char prefix, and both sides land on 0.938.
    assert ms.jaro_winkler("mandla", "mandala") == pytest.approx(0.938, abs=0.001)


# Captured from services/core UsidResolverTest.capturedValuesPinThePythonParityTable.
JAVA_REFERENCE = {
    ("meena", "mina"): 0.805,
    ("phulo gond", "phoolo gund"): 0.866,
    ("lakhon ho", "lacon ho"): 0.831,
    ("budhni devi", "budhni"): 0.909,
    ("", "x"): 0.0,
    ("a", "b"): 0.0,
    ("ab", "ac"): 0.700,
}