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

logger = logging.getLogger(__name__)


def _rule_based_fraud_check(app: dict[str, Any]) -> dict[str, Any]:
    """Deterministic anti-fraud heuristics for fallback and baseline checking."""
    flags: list[str] = []
    score = 0

    shared_bank_count = int(app.get("shared_bank_count", 0))
    if shared_bank_count > 1:
        flags.append(f"SHARED_BANK_ACCOUNT: Bank account linked to {shared_bank_count} distinct USIDs")
        score += min(50, shared_bank_count * 25)

    income_delta = float(app.get("income_delta_pct", 0.0))
    if income_delta <= -0.70:
        flags.append("INCOME_ANOMALY: Sudden >70% decrease in reported family income without audit")
        score += 30

    guardian_score = float(app.get("guardian_match_score", 1.0))
    if guardian_score < 0.60:
        flags.append("GUARDIAN_MISMATCH: Low consistency between certificate and claim record")
        score += 25

    inst_change = app.get("institution_changed_mid_year", False)
    if inst_change:
        flags.append("MID_YEAR_INSTITUTION_CHANGE: Transferred institution without migration certificate")
        score += 20

    velocity = int(app.get("apps_last_7d", 1))
    if velocity >= 4:
        flags.append(f"HIGH_SUBMISSION_VELOCITY: {velocity} applications submitted within 7 days")
        score += 25

    risk_level = "low"
    recommendation = "auto_pass"
    if score >= 60:
        risk_level = "critical"
        recommendation = "freeze_disbursement"
    elif score >= 40:
        risk_level = "high"
        recommendation = "field_inspection"
    elif score >= 20:
        risk_level = "medium"
        recommendation = "document_audit"

    return {
        "is_suspicious": score >= 35,
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
        "shared_bank_count": int(application.get("shared_bank_count", 0)),
        "income_delta_pct": float(application.get("income_delta_pct", 0.0)),
        "guardian_match_score": float(application.get("guardian_match_score", 1.0)),
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

        if combined_score >= 70:
            level = "critical"
            rec = "freeze_disbursement"
        elif combined_score >= 45:
            level = "high"
            rec = "field_inspection"
        elif combined_score >= 25:
            level = "medium"
            rec = "document_audit"
        else:
            level = "low"
            rec = "auto_pass"

        return {
            "is_suspicious": combined_score >= 35,
            "fraud_risk_score": combined_score,
            "risk_level": level,
            "flags": base["flags"],
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
