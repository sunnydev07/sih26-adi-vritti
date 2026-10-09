"""JEV 1.13 (Free Tier) decision client via OpenCode Zen System One API.

Provides sub-500ms typed decisions for STP auto-approval, intent classification,
and anomaly detection with deterministic rule-based fallbacks.
"""

from __future__ import annotations

import logging
import math
import time
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

TIMEOUT_SECONDS = 3.0


class JevUnavailableError(RuntimeError):
    """Raised when OpenCode Zen JEV API is unreachable, times out, or fails."""
    pass


def _safe_float(value: Any, default: float = 0.0) -> float:
    """float() that cannot throw: unparseable or non-finite input -> default.

    Upstream and caller-supplied dicts are unvalidated, so a bare float() here
    turned "not-a-number" into a 500. NaN/inf are rejected too: NaN poisons
    every comparison it touches and inf breaks the 0..1 output contract.
    """
    try:
        result = float(value)
    except (TypeError, ValueError):
        return default
    return result if math.isfinite(result) else default


def _safe_int(value: Any, default: int = 0) -> int:
    """int() that cannot throw: unparseable input -> default."""
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _clamp_prob(value: float) -> float:
    return min(1.0, max(0.0, value))


def _extract_prob(val: Any) -> float:
    """Extract a 0..1 probability from a noul answer structure.

    Upstream JSON is unvalidated: {"noul": "high"}, {"noul": null} and
    {"probability": "bad"} all used to raise through float() and 500 the
    request, because the only thing callers catch is JevUnavailableError.
    Anything unparseable is "unknown" (0.5); anything numeric is clamped, so a
    model returning 2.0 can never produce a stored probability of 2.0.
    """
    if isinstance(val, bool):
        return 1.0 if val else 0.0
    if isinstance(val, dict):
        if "noul" in val:
            return _clamp_prob(_safe_float(val["noul"], 0.5))
        if "probability" in val:
            return _clamp_prob(_safe_float(val["probability"], 0.5))
        return 0.5
    if isinstance(val, (int, float)):
        return _clamp_prob(_safe_float(val, 0.5))
    if isinstance(val, str):
        return _clamp_prob(_safe_float(val.strip(), 0.5))
    return 0.5


async def decide(
    state: dict[str, Any],
    questions: dict[str, Any],
    *,
    model: str | None = None,
    timeout: float = TIMEOUT_SECONDS,
) -> tuple[dict[str, Any], float]:
    """Execute structured decision request against OpenCode Zen JEV.

    Returns (decisions_dict, latency_ms).
    """
    if not settings.enable_jev:
        raise JevUnavailableError("JEV integration is disabled by feature flag")

    api_key = settings.opencode_zen_api_key.strip()
    if not api_key:
        raise JevUnavailableError("OPENCODE_ZEN_API_KEY is not configured")

    selected_model = model or settings.opencode_zen_model or "jev-1.13-free"
    endpoint = settings.opencode_zen_url

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "X-App": "Adi-Vritti",
    }
    payload = {
        "model": selected_model,
        "state": state,
        "questions": questions,
    }

    start = time.perf_counter()
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(endpoint, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
    except httpx.TimeoutException as exc:
        elapsed = (time.perf_counter() - start) * 1000
        logger.warning("OpenCode Zen JEV timed out after %.1fms", elapsed)
        raise JevUnavailableError(f"JEV timed out after {timeout}s") from exc
    except httpx.HTTPStatusError as exc:
        elapsed = (time.perf_counter() - start) * 1000
        # Status code only: the exception string routinely embeds request
        # headers (Authorization: Bearer …), which must never reach the log.
        logger.warning(
            "OpenCode Zen JEV HTTP %d in %.1fms",
            exc.response.status_code,
            elapsed,
        )
        raise JevUnavailableError(f"JEV HTTP {exc.response.status_code}") from exc
    except httpx.RequestError as exc:
        # Transport-level only (connect, read, pool). Narrow on purpose: a
        # malformed response body (ValueError from resp.json()) or a bug in
        # this module is NOT "JEV is down" — those propagate as 500s so the
        # fault stays visible instead of being laundered into a 503. Only the
        # exception type is logged: the message embeds request headers.
        elapsed = (time.perf_counter() - start) * 1000
        logger.warning(
            "OpenCode Zen JEV transport error in %.1fms: %s", elapsed, type(exc).__name__
        )
        raise JevUnavailableError(
            f"JEV transport error: {type(exc).__name__}"
        ) from exc

    elapsed = (time.perf_counter() - start) * 1000
    decisions = data.get("answers") or data.get("decisions") or data
    return decisions, round(elapsed, 2)


# --- High-Level Decision Workflows with Deterministic Fallbacks ---

def _fallback_stp(app: dict[str, Any]) -> dict[str, Any]:
    """Rule-based fallback when JEV is unavailable."""
    verified = app.get("all_claims_verified", False)
    income_ok = app.get("income_below_ceiling", True)
    doc_conf = _safe_float(app.get("doc_confidence_avg", 0.0))
    deficiencies = _safe_int(app.get("open_deficiencies", 0))
    is_dupe = app.get("is_duplicate", False)

    if is_dupe or deficiencies > 0:
        return {
            "auto_approve_safe": False,
            "probability": 0.05,
            "risk_level": 4,
            "routing": "reject_with_deficiency" if deficiencies > 0 else "senior_officer_review",
            "fallback": True,
        }
    if verified and income_ok and doc_conf >= 0.85:
        return {
            "auto_approve_safe": True,
            "probability": 0.96,
            "risk_level": 1,
            "routing": "auto_approve",
            "fallback": True,
        }
    return {
        "auto_approve_safe": False,
        "probability": 0.50,
        "risk_level": 2,
        "routing": "senior_officer_review",
        "fallback": True,
    }


async def evaluate_stp(application: dict[str, Any]) -> dict[str, Any]:
    """Evaluate an application for Straight-Through Processing (STP) using JEV 1.13 Free."""
    state = {
        "scheme": application.get("scheme", ""),
        "all_claims_verified": application.get("all_claims_verified", False),
        "income_below_ceiling": application.get("income_below_ceiling", True),
        "doc_confidence_avg": _safe_float(application.get("doc_confidence_avg", 0.0)),
        "open_deficiencies": _safe_int(application.get("open_deficiencies", 0)),
        "is_duplicate": application.get("is_duplicate", False),
        "days_elapsed": _safe_int(application.get("days_elapsed", 0)),
        "sla_breached": application.get("sla_breached", False),
    }
    questions = {
        "auto_approve_safe": {
            "type": "noul",
            "instructions": "Is this scholarship application safe to automatically approve without human intervention?",
        },
        "needs_senior_review": {
            "type": "noul",
            "instructions": "Does this application require senior officer review due to defects or doubts?",
        },
    }

    try:
        decisions, latency = await decide(state, questions)
        safe_prob = _extract_prob(decisions.get("auto_approve_safe"))
        review_prob = _extract_prob(decisions.get("needs_senior_review"))

        is_safe = safe_prob >= 0.70 and review_prob < 0.40
        if is_safe:
            routing = "auto_approve"
            risk = 1
        elif review_prob >= 0.60:
            routing = "senior_officer_review"
            risk = 4
        else:
            routing = "field_verification_needed"
            risk = 2

        return {
            "auto_approve_safe": is_safe,
            "probability": round(safe_prob, 4),
            "risk_level": risk,
            "routing": routing,
            "latency_ms": latency,
            "fallback": False,
        }
    except JevUnavailableError:
        fb = _fallback_stp(application)
        fb["latency_ms"] = 0.0
        return fb


def _fallback_intent(message: str) -> dict[str, Any]:
    """Keyword-based intent classifier fallback."""
    msg = message.lower()
    if any(w in msg for w in ["paisa", "payment", "pending", "status", "ruk", "late", "kya hua"]):
        return {"intent": "why_is_payment_pending", "needs_usid": True, "confidence": 0.85, "fallback": True}
    if any(w in msg for w in ["patra", "eligib", "yogy", "apply", "milega", "mil sakta"]):
        return {"intent": "check_eligibility", "needs_usid": True, "confidence": 0.80, "fallback": True}
    if any(w in msg for w in ["kagaz", "document", "praman", "certificate"]):
        return {"intent": "list_required_documents", "needs_usid": False, "confidence": 0.80, "fallback": True}
    if any(w in msg for w in ["history", "itihaas", "kab mila", "purana"]):
        return {"intent": "get_disbursement_history", "needs_usid": True, "confidence": 0.80, "fallback": True}
    if any(w in msg for w in ["namaste", "hello", "hi", "johar"]):
        return {"intent": "greeting_or_chitchat", "needs_usid": False, "confidence": 0.95, "fallback": True}
    return {"intent": "get_my_applications", "needs_usid": True, "confidence": 0.60, "fallback": True}


async def classify_jago_intent(user_message: str, lang: str = "hi") -> dict[str, Any]:
    """Classify user intent for the JAGO+ scholarship assistant using JEV 1.13 Free."""
    state = {
        "user_message": user_message[:500],
        "language": lang,
    }
    questions = {
        "why_is_payment_pending": {
            "type": "noul",
            "instructions": "Is the student asking about scholarship payment, disbursement status, bank transfer, or delayed money?",
        },
        "check_eligibility": {
            "type": "noul",
            "instructions": "Is the student asking whether they qualify or are eligible for a scholarship scheme?",
        },
        "list_required_documents": {
            "type": "noul",
            "instructions": "Is the student asking what documents, certificates, or papers are required to apply?",
        },
    }

    try:
        decisions, latency = await decide(state, questions)
        if not isinstance(decisions, dict) or all(
            decisions.get(name) is None for name in questions
        ):
            # JEV answered with nothing usable (empty object, nulls, or a
            # non-object): labelling that a live model decision would put a
            # fabricated verdict in the UI and the audit trail.
            fb = _fallback_intent(user_message)
            fb["latency_ms"] = latency
            fb["fallback"] = True
            fb["fallback_reason"] = "empty_model_response"
            return fb
        scored = {}
        for intent_name in questions:
            scored[intent_name] = _extract_prob(decisions.get(intent_name))

        best_intent = max(scored, key=scored.get)
        confidence = scored[best_intent]

        if confidence < 0.40:
            # JEV answered, but not with anything we can use. The verdict is
            # still a *fallback* — it came from _fallback_intent, not from the
            # model. This used to overwrite the flag with False, which labelled
            # a keyword guess as a live model decision in every UI and audit
            # record that reads it. Latency is the model's real round-trip and
            # stays as measured; the flag describes provenance, not speed.
            fb = _fallback_intent(user_message)
            fb["latency_ms"] = latency
            fb["fallback"] = True
            fb["fallback_reason"] = "low_confidence"
            return fb

        needs_usid = best_intent in ("why_is_payment_pending", "check_eligibility")
        return {
            "intent": best_intent,
            "needs_usid": needs_usid,
            "confidence": round(confidence, 2),
            "latency_ms": latency,
            "fallback": False,
        }
    except JevUnavailableError:
        fb = _fallback_intent(user_message)
        fb["latency_ms"] = 0.0
        return fb
