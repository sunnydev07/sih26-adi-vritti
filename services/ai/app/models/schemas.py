"""Shared pydantic v2 models."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator


class MatchRecord(BaseModel):
    # Every field is bounded: this is request-body input to a scoring function,
    # and an unbounded string would be fed straight into the O(n*m) Jaro-Winkler
    # comparison.
    full_name: str = Field(min_length=1, max_length=200)
    dob: str | None = Field(default=None, max_length=32)
    gender: str | None = Field(default=None, max_length=32)
    guardian_name: str | None = Field(default=None, max_length=200)
    institution_code: str | None = Field(default=None, max_length=64)
    district: str | None = Field(default=None, max_length=128)
    bank_account_last4: str | None = Field(default=None, max_length=4, pattern=r"^\d{4}$")


class MatchRequest(BaseModel):
    a: MatchRecord
    b: MatchRecord


class MatchResponse(BaseModel):
    confidence: float = Field(ge=0, le=1)
    needs_human_review: bool
    breakdown: dict[str, float]


class GapQuery(BaseModel):
    state: str | None = Field(default=None, max_length=64)
    district: str | None = Field(default=None, max_length=128)
    block: str | None = Field(default=None, max_length=128)
    school: str | None = Field(default=None, max_length=256)
    pvtg_filter: bool | None = None


class JagoToolCall(BaseModel):
    usid: UUID
    # default_factory, not a bare `{}`: a shared mutable default would let one
    # request's parameters leak into another's. Bounded: parameters are
    # forwarded to Core (query string on GET), so a 10k-key body is a DoS and
    # a cost amplifier, not a filter set.
    parameters: dict[str, Any] = Field(default_factory=dict, max_length=64)


class RagQuery(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    scheme: str | None = Field(default=None, max_length=64)


class DocParseResponse(BaseModel):
    fields: dict[str, Any]
    tamper_signals: list[str]
    # 0.0 is a legitimate answer (nothing extractable), so this is not bounded
    # away from zero.
    confidence: float = Field(ge=0, le=1)
    parsed_at: datetime


#: The claim types the contract's ``VerifyRequest.claim_type`` enum allows
#: (docs/openapi/core.yaml). /docai/parse echoes this value straight into the
#: response body and into the extraction dispatch, so it is a ``Literal`` here
#: too: an arbitrary string used to be persisted verbatim as
#: ``fields.claim_type``, which is unbounded storage under a caller's control
#: and a second, unchecked path into the extractor.
ClaimType = Literal[
    "identity",
    "st_status",
    "income",
    "domicile",
    "academic",
    "enrolment",
    "institution",
    "net_jrf",
    "disability",
    "bank_account",
]


class DbtExplainRequest(BaseModel):
    """Bounded body for /decisions/dbt-explain.

    This used to be a bare ``dict[str, Any]``, which meant ``failure_code`` and
    ``details`` were whatever the caller sent: an arbitrary-length string was
    interpolated into the Groq prompt verbatim and a nested structure of any
    shape was stringified into it. Both are a prompt-injection surface and a
    token-cost amplifier, and an over-long body became a 500 from Groq rather
    than a 422 from the boundary that should have rejected it.
    """

    failure_code: str = Field(
        default="E006_OTHER", min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_]+$"
    )
    lang: str = Field(default="hi", min_length=2, max_length=16, pattern=r"^[a-zA-Z-]+$")
    # A failure's context is a handful of short scalar facts (bank last4, a
    # timestamp, a gateway message). Nested objects are not part of that shape,
    # so only scalars are accepted; each one is length-bounded.
    details: dict[str, str | int | float | bool | None] = Field(
        default_factory=dict,
        max_length=32,
    )

    @field_validator("details")
    @classmethod
    def _bound_detail_values(cls, v: dict[str, str | int | float | bool | None]) -> dict:
        for key, value in v.items():
            if not isinstance(key, str) or len(key) > 64:
                raise ValueError("each detail key must be a string of at most 64 characters")
            if isinstance(value, str) and len(value) > 500:
                raise ValueError(
                    f"detail '{key}' is {len(value)} characters; the limit is 500"
                )
        return v


class JevDecisionRequest(BaseModel):
    # Bounded: these dicts are serialised into a metered upstream call, so a
    # megabyte of state is token cost and event-loop time, not a decision.
    # Per-value shape is enforced where the values are consumed (the services
    # coerce defensively); the boundary only needs to cap the blast radius.
    state: dict[str, Any] = Field(max_length=64)
    questions: dict[str, Any] = Field(max_length=64)
    model: str | None = Field(default=None, max_length=64, pattern=r"^[A-Za-z0-9._-]+$")


class JevDecisionResponse(BaseModel):
    decisions: dict[str, Any]
    model: str
    latency_ms: float


class StpScoreRequest(BaseModel):
    application: dict[str, Any] = Field(max_length=64)


class StpScoreResponse(BaseModel):
    auto_approve_safe: bool
    probability: float = Field(ge=0, le=1)
    risk_level: int
    routing: str
    latency_ms: float
    # True when the verdict came from the deterministic rule engine because the
    # model was unavailable or over quota. The service already computed this and
    # the route dropped it, so the officer console's "JEV 1.13 Free (Live)" label
    # was applied to a rule-based verdict — the same mislabelling task 3.2 fixed
    # inside jev_service, one layer out.
    fallback: bool


class JagoIntentRequest(BaseModel):
    user_message: str = Field(min_length=1, max_length=1000)
    lang: str = Field(default="hi", max_length=16)


class JagoHelpRequest(BaseModel):
    # General help only: no USID field on purpose. A question about the
    # caller's own file is deflected (lane="status") with a suggested tool —
    # personal data must only ever come from JAGO tools + templates.
    question: str = Field(min_length=1, max_length=500)
    lang: str = Field(default="hi", max_length=16)


class JagoHelpCitation(BaseModel):
    kind: str
    id: str
    title: str


class JagoHelpResponse(BaseModel):
    lane: str
    answer: str
    lang: str
    citations: list[JagoHelpCitation]
    source: str
    suggested_tool: str | None = None
    suggestions: list[str]
    latency_ms: float


class JagoIntentResponse(BaseModel):
    intent: str
    needs_usid: bool
    confidence: float = Field(ge=0, le=1)
    latency_ms: float


class ChatMessage(BaseModel):
    # No "system" role: the server owns the system prompt (grounding +
    # never-invent-status rules). A caller-supplied system message is how
    # instructions get smuggled past those rules, so it is a 422, not input.
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatCompletionRequest(BaseModel):
    messages: list[ChatMessage] = Field(min_length=1, max_length=20)
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)
    max_tokens: int | None = Field(default=1024, ge=1, le=8192)
    lang: str = Field(default="hi", max_length=16)


class ChatCompletionResponse(BaseModel):
    reply: str
    model: str
    latency_ms: float


class FraudScreenRequest(BaseModel):
    application: dict[str, Any] = Field(max_length=64)


class FraudScreenResponse(BaseModel):
    is_suspicious: bool
    fraud_risk_score: int
    risk_level: str
    flags: list[str]
    recommendation: str
    latency_ms: float
    fallback: bool
