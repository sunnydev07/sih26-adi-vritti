"""Real-time fraud & anomaly detection service using JEV 1.13 Free (System One).

Screens scholarship applications for:
- Ghost beneficiaries & synthetic identities
- Duplicate subsidy claims & shared bank accounts across USIDs
- Income ceiling evasion & rapid submission bursts
"""

from __future__ import annotations

import logging
from typing import Any

from app.services import jev_service
from app.services.jev_service import _safe_float, _safe_int

logger = logging.getLogger(__name__)

# One risk scale for both the rule engine and the model-combined verdict below.
# Two scales used to disagree (60/40/20 vs 70/45/25), so the same score was
# "medium" on one path and "low" on the other — and a score of 25 rendered as
# medium risk but NOT suspicious, a contradiction on the officer's screen.
_RISK_CRITICAL = 60
_RISK_HIGH = 40
_RISK_MEDIUM = 20


def _level_and_recommendation(score: int) -> tuple[str, str]:
    if score >= _RISK_CRITICAL:
        return "critical", "freeze_disbursement"
    if score >= _RISK_HIGH:
        return "high", "field_inspection"
    if score >= _RISK_MEDIUM:
        return "medium", "document_audit"
    return "low", "auto_pass"


def _rule_based_fraud_check(app: dict[str, Any]) -> dict[str, Any]:
    """Deterministic anti-fraud heuristics for fallback and baseline checking."""
    flags: list[str] = []
    score = 0

    shared_bank_count = _safe_int(app.get("shared_bank_count", 0))
    if shared_bank_count > 1:
        flags.append(f"SHARED_BANK_ACCOUNT: Bank account linked to {shared_bank_count} distinct USIDs")
        score += min(50, shared_bank_count * 25)

    income_delta = _safe_float(app.get("income_delta_pct", 0.0))
    if income_delta <= -0.70:
        flags.append("INCOME_ANOMALY: Sudden >70% decrease in reported family income without audit")
        score += 30

    guardian_score = _safe_float(app.get("guardian_match_score", 1.0))
    if guardian_score < 0.60:
        flags.append("GUARDIAN_MISMATCH: Low consistency between certificate and claim record")
        score += 25

    inst_change = app.get("institution_changed_mid_year", False)
    if inst_change:
        flags.append("MID_YEAR_INSTITUTION_CHANGE: Transferred institution without migration certificate")
        score += 20

    velocity = _safe_int(app.get("apps_last_7d", 1))
    if velocity >= 4:
        flags.append(f"HIGH_SUBMISSION_VELOCITY: {velocity} applications submitted within 7 days")
        score += 25

    risk_level, recommendation = _level_and_recommendation(score)

    return {
        "is_suspicious": risk_level != "low",
        "fraud_risk_score": min(100, score),
        "risk_level": risk_level,
        "flags": flags,
        "recommendation": recommendation,
        "fallback": True,
        "latency_ms": 0.0,
    }


async def screen_application(application: dict[str, Any]) -> dict[str, Any]:
    """Screen an application for fraud using JEV 1.13 Free multi-noul questions."""
    # First run the rule heuristics to gather telemetry flags
    base = _rule_based_fraud_check(application)

    state = {
        "scheme": application.get("scheme", ""),
        "shared_bank_count": _safe_int(application.get("shared_bank_count", 0)),
        "income_delta_pct": _safe_float(application.get("income_delta_pct", 0.0)),
        "guardian_match_score": _safe_float(application.get("guardian_match_score", 1.0)),
        "inst_changed_mid_year": application.get("institution_changed_mid_year", False),
        "flags_detected": base["flags"],
    }

    questions = {
        "is_ghost_beneficiary": {
            "type": "noul",
            "instructions": "Does this scholarship profile exhibit signs of synthetic identity or ghost beneficiary?",
        },
        "is_duplicate_subsidy": {
            "type": "noul",
            "instructions": "Is this applicant attempting an unauthorized overlapping subsidy or duplicate claim?",
        },
        "needs_field_investigation": {
            "type": "noul",
            "instructions": "Does this application warrant immediate field officer investigation before release of funds?",
        },
    }

    try:
        decisions, latency = await jev_service.decide(state, questions)
        ghost_prob = jev_service._extract_prob(decisions.get("is_ghost_beneficiary"))
        dupe_prob = jev_service._extract_prob(decisions.get("is_duplicate_subsidy"))
        investigate_prob = jev_service._extract_prob(decisions.get("needs_field_investigation"))

        ai_score = int(max(ghost_prob, dupe_prob, investigate_prob) * 100)
        combined_score = max(base["fraud_risk_score"], ai_score)

        flags = list(base["flags"])
        if ai_score > base["fraud_risk_score"]:
            # The verdict is driven by the model, not by any rule: say so, or
            # the officer sees "critical, no reasons" and cannot act on it.
            flags.append(
                f"AI_MODEL_RISK: model ghost/duplicate/investigation score "
                f"{ai_score}/100 exceeds the rule-based score "
                f"{base['fraud_risk_score']}/100"
            )

        risk_level, rec = _level_and_recommendation(combined_score)

        return {
            "is_suspicious": risk_level != "low",
            "fraud_risk_score": combined_score,
            "risk_level": risk_level,
            "flags": flags,
            "recommendation": rec,
            "latency_ms": latency,
            "fallback": False,
        }
    except jev_service.JevUnavailableError as exc:
        # Narrow on purpose. This used to be `except Exception`, which also
        # caught KeyboardInterrupt and SystemExit, so Ctrl-C during an
        # in-flight screen and a shutdown request both came back as a cheerful
        # rule-based verdict with `fallback` implied — an operator stopping the
        # container saw log traffic that looked like normal screening. httpx
        # errors stay unexpected (a bug in this module, a malformed response)
        # and are allowed to surface; the *only* condition the fallback exists
        # for is JEV being unreachable.
        logger.warning("JEV fraud screening fallback triggered: %s", exc)
        return base
