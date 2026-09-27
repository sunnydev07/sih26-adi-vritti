"""Shared pydantic v2 models."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class MatchRecord(BaseModel):
    full_name: str
    dob: str | None = None
    gender: str | None = None
    guardian_name: str | None = None
    institution_code: str | None = None
    district: str | None = None
    bank_account_last4: str | None = None


class MatchRequest(BaseModel):
    a: MatchRecord
    b: MatchRecord


class MatchResponse(BaseModel):
    confidence: float = Field(ge=0, le=1)
    needs_human_review: bool
    breakdown: dict[str, float]


class GapQuery(BaseModel):
    state: str | None = None
    district: str | None = None
    block: str | None = None
    school: str | None = None
    pvtg_filter: bool | None = None


class JagoToolCall(BaseModel):
    usid: UUID
    parameters: dict = {}


class RagQuery(BaseModel):
    question: str
    scheme: str | None = None


class DocParseResponse(BaseModel):
    fields: dict
    tamper_signals: list[str]
    confidence: float
    parsed_at: datetime
