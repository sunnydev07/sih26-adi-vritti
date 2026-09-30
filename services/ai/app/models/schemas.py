"""Shared pydantic v2 models."""

from __future__ import annotations

from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field


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
    # request's parameters leak into another's.
    parameters: dict[str, Any] = Field(default_factory=dict)


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


class JevDecisionRequest(BaseModel):
    state: dict[str, Any]
    questions: dict[str, Any]
    model: str | None = None


class JevDecisionResponse(BaseModel):
    decisions: dict[str, Any]
    model: str
    latency_ms: float


class StpScoreRequest(BaseModel):
    application: dict[str, Any]


class StpScoreResponse(BaseModel):
    auto_approve_safe: bool
    probability: float
    risk_level: int
    routing: str
    latency_ms: float


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
    confidence: float
    latency_ms: float


class ChatCompletionRequest(BaseModel):
    messages: list[dict[str, str]]
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)
    max_tokens: int | None = Field(default=1024, ge=1, le=8192)


class ChatCompletionResponse(BaseModel):
    reply: str
    model: str
    latency_ms: float


class FraudScreenRequest(BaseModel):
    application: dict[str, Any]


class FraudScreenResponse(BaseModel):
    is_suspicious: bool
    fraud_risk_score: int
    risk_level: str
    flags: list[str]
    recommendation: str
    latency_ms: float
    fallback: bool
