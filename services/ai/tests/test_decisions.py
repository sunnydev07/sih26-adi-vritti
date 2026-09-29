"""Tests for JEV 1.13 decisions and Groq chat completions."""

from __future__ import annotations

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.services import groq_service, jev_service

AUTH = {"x-ai-service-token": "test-service-token"}
client = TestClient(app)


class _MockResponse:
    def __init__(self, status_code: int, json_data: dict, text: str = ""):
        self.status_code = status_code
        self._json_data = json_data
        self.text = text

    def json(self):
        return self._json_data

    def raise_for_status(self):
        if self.status_code >= 400:
            request = httpx.Request("POST", "http://test")
            response = httpx.Response(self.status_code, request=request, text=self.text)
            raise httpx.HTTPStatusError("HTTP error", request=request, response=response)


class _MockAsyncClient:
    def __init__(self, response: _MockResponse | None = None, exc: Exception | None = None):
        self._response = response
        self._exc = exc
        self.last_request = {}

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, url, json=None, headers=None):
        self.last_request = {"url": url, "json": json, "headers": headers}
        if self._exc:
            raise self._exc
        return self._response


# --- JEV Service Tests ---

@pytest.mark.asyncio
async def test_jev_decide_success(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(settings, "enable_jev", True)

    mock_resp = _MockResponse(
        200,
        {
            "decisions": {
                "auto_approve_safe": {"probability": 0.98},
                "risk_level": {"value": 1},
                "routing": {"choice": "auto_approve"},
            }
        },
    )

    def mock_client_factory(*args, **kwargs):
        return _MockAsyncClient(response=mock_resp)

    monkeypatch.setattr(httpx, "AsyncClient", mock_client_factory)

    decisions, latency = await jev_service.decide(
        state={"scheme": "PRE_MATRIC"},
        questions={"auto_approve_safe": {"type": "noul"}},
    )

    assert "auto_approve_safe" in decisions
    assert latency >= 0


@pytest.mark.asyncio
async def test_jev_decide_missing_key_raises(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "")
    with pytest.raises(jev_service.JevUnavailableError, match="not configured"):
        await jev_service.decide(state={}, questions={})


@pytest.mark.asyncio
async def test_evaluate_stp_with_jev(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(settings, "enable_jev", True)

    mock_resp = _MockResponse(
        200,
        {
            "answers": {
                "auto_approve_safe": {"type": "noul", "noul": 0.85},
                "needs_senior_review": {"type": "noul", "noul": 0.15},
            }
        },
    )
    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(response=mock_resp))

    result = await jev_service.evaluate_stp({
        "scheme": "POST_MATRIC",
        "all_claims_verified": True,
        "income_below_ceiling": True,
        "doc_confidence_avg": 0.92,
        "open_deficiencies": 0,
        "is_duplicate": False,
    })

    assert result["auto_approve_safe"] is True
    assert result["probability"] == 0.85
    assert result["risk_level"] == 1
    assert result["routing"] == "auto_approve"
    assert result["fallback"] is False


@pytest.mark.asyncio
async def test_evaluate_stp_fallback(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "")  # forces fallback

    result = await jev_service.evaluate_stp({
        "all_claims_verified": True,
        "income_below_ceiling": True,
        "doc_confidence_avg": 0.90,
        "open_deficiencies": 0,
        "is_duplicate": False,
    })

    assert result["auto_approve_safe"] is True
    assert result["fallback"] is True
    assert result["routing"] == "auto_approve"


@pytest.mark.asyncio
async def test_classify_intent_fallback(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "")
    result = await jev_service.classify_jago_intent("Mera paisa kyu nahi aaya?")
    assert result["intent"] == "why_is_payment_pending"
    assert result["needs_usid"] is True
    assert result["fallback"] is True


@pytest.mark.asyncio
async def test_classify_intent_with_jev(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "test-zen-key")
    monkeypatch.setattr(settings, "enable_jev", True)
    mock_resp = _MockResponse(
        200,
        {
            "answers": {
                "why_is_payment_pending": {"type": "noul", "noul": 0.95},
                "check_eligibility": {"type": "noul", "noul": 0.05},
                "list_required_documents": {"type": "noul", "noul": 0.02},
            }
        },
    )
    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(response=mock_resp))
    res = await jev_service.classify_jago_intent("Where is my payment?")
    assert res["intent"] == "why_is_payment_pending"
    assert res["confidence"] == 0.95
    assert res["fallback"] is False


# --- Groq Service Tests ---

@pytest.mark.asyncio
async def test_groq_chat_completion_success(monkeypatch):
    monkeypatch.setattr(settings, "groq_api_key", "test-groq-key")
    monkeypatch.setattr(settings, "enable_groq", True)

    mock_resp = _MockResponse(
        200,
        {"choices": [{"message": {"content": "This is a fast Groq response."}}]},
    )
    monkeypatch.setattr(httpx, "AsyncClient", lambda *a, **k: _MockAsyncClient(response=mock_resp))

    reply, latency = await groq_service.chat_completion([{"role": "user", "content": "Hello"}])
    assert reply == "This is a fast Groq response."
    assert latency >= 0


@pytest.mark.asyncio
async def test_groq_chat_missing_key_raises(monkeypatch):
    monkeypatch.setattr(settings, "groq_api_key", "")
    with pytest.raises(groq_service.GroqUnavailableError, match="not configured"):
        await groq_service.chat_completion([{"role": "user", "content": "Hi"}])


@pytest.mark.asyncio
async def test_explain_dbt_failure_fallback(monkeypatch):
    monkeypatch.setattr(settings, "groq_api_key", "")
    explanation = await groq_service.explain_dbt_failure("E001_AADHAAR_NOT_SEEDED")
    assert "Aadhaar NPCI Seeding" in explanation


# --- Router Endpoint Tests ---

def test_router_stp_endpoint():
    res = client.post(
        "/decisions/stp",
        json={
            "application": {
                "all_claims_verified": True,
                "income_below_ceiling": True,
                "doc_confidence_avg": 0.92,
                "open_deficiencies": 0,
            }
        },
        headers=AUTH,
    )
    assert res.status_code == 200
    data = res.json()
    assert "auto_approve_safe" in data
    assert "routing" in data


def test_router_intent_endpoint():
    res = client.post(
        "/decisions/intent",
        json={"user_message": "Payment pending status check", "lang": "hi"},
        headers=AUTH,
    )
    assert res.status_code == 200
    data = res.json()
    assert data["intent"] == "why_is_payment_pending"


def test_router_dbt_explain_endpoint(monkeypatch):
    monkeypatch.setattr(settings, "groq_api_key", "")
    res = client.post(
        "/decisions/dbt-explain",
        json={"failure_code": "E002_ACCOUNT_DORMANT"},
        headers=AUTH,
    )
    assert res.status_code == 200
    data = res.json()
    assert "dormant" in data["explanation"].lower()


def test_router_rejected_without_auth():
    res = client.post("/decisions/stp", json={"application": {}})
    assert res.status_code in (401, 403)


def test_rag_query_endpoint(monkeypatch):
    monkeypatch.setattr(settings, "groq_api_key", "")
    res = client.post(
        "/rag/query",
        json={"question": "What is the Pre-Matric income ceiling?", "scheme": "PRE_MATRIC"},
        headers=AUTH,
    )
    assert res.status_code == 200
    data = res.json()
    assert "citations" in data
    assert len(data["citations"]) > 0
    assert any("PRE-1.3" in c["clause_id"] for c in data["citations"])


def test_fraud_screening_endpoint(monkeypatch):
    monkeypatch.setattr(settings, "opencode_zen_api_key", "")
    res = client.post(
        "/decisions/fraud-screen",
        json={
            "application": {
                "shared_bank_count": 3,
                "income_delta_pct": -0.85,
                "guardian_match_score": 0.45,
            }
        },
        headers=AUTH,
    )
    assert res.status_code == 200
    data = res.json()
    assert data["is_suspicious"] is True
    assert data["risk_level"] in ("high", "critical")
    assert len(data["flags"]) > 0
