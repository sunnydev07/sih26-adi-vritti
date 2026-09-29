"""Request-model validation.

These bound the input that reaches the scoring and proxy code. An unbounded
string would otherwise be fed straight into the O(n*m) Jaro-Winkler comparison,
and a mutable default dict would let one request's parameters leak into
another's.
"""

import pytest
from pydantic import ValidationError

from app.models.schemas import (
    GapQuery,
    JagoToolCall,
    MatchRecord,
    MatchRequest,
    RagQuery,
)


def test_a_full_match_record_is_accepted():
    r = MatchRecord(
        full_name="Sunita Meena",
        dob="2012-05-01",
        gender="female",
        guardian_name="Soma Ram",
        institution_code="SCH-0001",
        district="Mandla",
        bank_account_last4="1234",
    )
    assert r.full_name == "Sunita Meena"


def test_bank_last4_must_be_exactly_four_digits():
    # A partial or over-long account fragment is not usable for matching, and
    # accepting one invites a bad comparison later.
    MatchRecord(full_name="A B", bank_account_last4="1234")
    for bad in ("123", "12345", "abcd", "", "12 34"):
        with pytest.raises(ValidationError):
            MatchRecord(full_name="A B", bank_account_last4=bad)


def test_full_name_is_required_and_bounded():
    with pytest.raises(ValidationError):
        MatchRecord(full_name="")
    with pytest.raises(ValidationError):
        MatchRecord(full_name="x" * 500)


def test_an_absent_optional_field_is_none_not_a_string():
    r = MatchRecord(full_name="A B")
    assert r.dob is None and r.bank_account_last4 is None


def test_match_request_needs_both_sides():
    with pytest.raises(ValidationError):
        MatchRequest(a=MatchRecord(full_name="A B"))


def test_jago_parameters_default_is_not_shared():
    # A bare `= {}` default is shared across instances in plain Python, which
    # would let one caller's parameters appear in another's request to Core.
    a = JagoToolCall(usid="11111111-1111-4111-8111-111111111111")
    b = JagoToolCall(usid="11111111-1111-4111-8111-111111111111")
    a.parameters["injected"] = True
    assert b.parameters == {}


def test_jago_requires_a_valid_uuid():
    with pytest.raises(ValidationError):
        JagoToolCall(usid="not-a-uuid")


def test_rag_question_is_bounded():
    RagQuery(question="who is eligible for NMSS?")
    with pytest.raises(ValidationError):
        RagQuery(question="")
    with pytest.raises(ValidationError):
        RagQuery(question="x" * 5000)


def test_gap_filters_are_bounded():
    GapQuery(district="Mandla")
    with pytest.raises(ValidationError):
        GapQuery(district="d" * 500)
