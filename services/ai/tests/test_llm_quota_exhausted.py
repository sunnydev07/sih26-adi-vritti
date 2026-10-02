"""The LLM quota is spent: the product still works.

Groq and OpenCode Zen are metered. When the day's quota runs out they answer 429
(and a revoked key answers 401), and this repo's own demo — a DBT failure shown to
a student whose scholarship payment bounced — has to keep working.

That is not hypothetical here: it is the state these services are in whenever the
daily limit is hit, so the degradation path is the *normal* path, not an edge case.
The rule the code has to keep is that a synthetic-language failure is answered from
the static table, never from a 503 and never from a half-written model reply.

These tests use the same `_MockResponse` / `_MockAsyncClient` shape as
test_decisions.py so they exercise the real httpx error path
(``raise_for_status`` -> ``HTTPStatusError``) rather than a stubbed branch.
"""

from __future__ import annotations

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.services import groq_service, jev_service

AUTH = {"x-ai-service-token": "test-service-token"}
client = TestClient(app)

#: What each metered provider returns once the day's allowance is gone.
QUOTA_EXHAUSTED = {"groq": 429, "jev": 429}
#: And once a key is revoked or wrong.
KEY_REJECTED = {"groq": 401, "jev": 401}


@pytest.fixture(autouse=True)
def quota_is_the_whole_story(monkeypatch):
    """Every test here is about a provider refusing us.

    The mocked AsyncClient replaces ``httpx.AsyncClient`` globally, so nothing in
    this file can reach the network even where a real key sits in
    ``services/ai/.env``. Setting the flags on here too means a test that forgets
    to mock the client fails loudly (a real request) rather than silently
    depending on somebody's account quota.
    """
    monkeypatch.setattr(settings, "enable_groq", True)
    monkeypatch.setattr(settings, "enable_jev", True)
    monkeypatch.setattr(settings, "groq_api_key", "test-key")
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(
        _MockResponse(QUOTA_EXHAUSTED["groq"])))


def _http_error(status_code: int) -> httpx.HTTPStatusError:
    request = httpx.Request("POST", "http://upstream")
    response = httpx.Response(status_code, request=request, text="quota exhausted")
    return httpx.HTTPStatusError("HTTP error", request=request, response=response)


class _MockResponse:
    def __init__(self, status_code: int, json_data: dict | None = None, text: str = ""):
        self.status_code = status_code
        self._json_data = json_data or {}
        self.text = text

    def json(self):
        return self._json_data

    def raise_for_status(self):
        if self.status_code >= 400:
            raise _http_error(self.status_code)


class _MockAsyncClient:
    def __init__(self, response):
        self._response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, url, json=None, headers=None):
        return self._response


@pytest.mark.parametrize("label,status_code", [
    ("quota exhausted", QUOTA_EXHAUSTED["groq"]),
    ("key rejected", KEY_REJECTED["groq"]),
])
@pytest.mark.asyncio
async def test_dbt_explain_falls_back_to_the_static_table(monkeypatch, label, status_code):
    monkeypatch.setattr(settings, "enable_groq", True)
    monkeypatch.setattr(settings, "groq_api_key", "test-key")
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(_MockResponse(status_code))
    )

    explanation = await groq_service.explain_dbt_failure("E001_AADHAAR_NOT_SEEDED", {}, "hi")

    # The whole point: the student gets actionable instructions, not an error.
    assert "Aadhaar NPCI Seeding" in explanation
    assert explanation == groq_service.STATIC_DBT_FIXES["E001_AADHAAR_NOT_SEEDED"], label


@pytest.mark.asyncio
async def test_dbt_explain_endpoint_still_returns_200_when_groq_is_out(monkeypatch):
    monkeypatch.setattr(settings, "enable_groq", True)
    monkeypatch.setattr(settings, "groq_api_key", "test-key")
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(_MockResponse(429))
    )

    r = client.post(
        "/decisions/dbt-explain",
        json={"failure_code": "E004_NAME_MISMATCH", "lang": "hi"},
        headers=AUTH,
    )

    # 503 here would blank the DBT Failure Doctor beat in the demo path.
    assert r.status_code == 200
    assert r.json()["explanation"] == groq_service.STATIC_DBT_FIXES["E004_NAME_MISMATCH"]


@pytest.mark.asyncio
async def test_the_quota_error_never_reaches_the_caller(monkeypatch):
    # A chat-completions caller gets a 503, and its detail must not carry the
    # upstream body — which for a metered provider is where the quota state and
    # sometimes the account identifier live.
    monkeypatch.setattr(settings, "enable_groq", True)
    monkeypatch.setattr(settings, "groq_api_key", "gsk_live_secret_value")
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(_MockResponse(429))
    )

    r = client.post(
        "/decisions/chat",
        json={"messages": [{"role": "user", "content": "hello"}]},
        headers=AUTH,
    )

    assert r.status_code == 503
    body = r.text
    assert "gsk_live_secret_value" not in body
    assert "quota exhausted" not in body


@pytest.mark.asyncio
async def test_stp_scoring_falls_back_when_jev_quota_is_gone(monkeypatch):
    # The officer console's STP column must still render. It falls back to the
    # deterministic engine and says so, which is the difference between a
    # degraded screen and a blank one.
    monkeypatch.setattr(settings, "enable_jev", True)
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(_MockResponse(429))
    )

    r = client.post(
        "/decisions/stp",
        json={"application": {"scheme": "PRE_MATRIC", "days_elapsed": 30}},
        headers=AUTH,
    )

    assert r.status_code == 200
    payload = r.json()
    assert payload["fallback"] is True
    assert payload["routing"] in {"auto_approve", "senior_officer_review", "manual_review"}


@pytest.mark.asyncio
async def test_jago_intent_is_answered_without_jev(monkeypatch):
    monkeypatch.setattr(settings, "enable_jev", True)
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(
        httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(_MockResponse(429))
    )

    result = await jev_service.classify_jago_intent("mera payment kab aayega", "hi")

    # Routed, not refused: a keyword classifier is a worse answer than a model, but
    # it is an answer, and it is labelled as such.
    assert result["intent"] == "why_is_payment_pending"
    assert result["fallback"] is True
    assert result["latency_ms"] == 0.0


def test_a_blank_key_is_the_same_outcome_as_a_spent_one():
    # No configured key and an exhausted one have to look the same to a caller:
    # both are "the model is not available", neither is a 500.
    assert groq_service.chat_completion.__doc__
    with pytest.raises(groq_service.GroqUnavailableError):
        import asyncio

        original = settings.groq_api_key
        try:
            settings.groq_api_key = ""
            asyncio.run(
                groq_service.chat_completion([{"role": "user", "content": "hi"}])
            )
        finally:
            settings.groq_api_key = original
