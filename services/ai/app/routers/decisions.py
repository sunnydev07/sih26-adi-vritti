"""Router for JEV System One decisions and Groq chat completions."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.models.schemas import (
    ChatCompletionRequest,
    ChatCompletionResponse,
    FraudScreenRequest,
    FraudScreenResponse,
    JagoIntentRequest,
    JagoIntentResponse,
    JevDecisionRequest,
    JevDecisionResponse,
    StpScoreRequest,
    StpScoreResponse,
)
from app.security import require_service_token
from app.services import fraud_service, groq_service, jev_service

router = APIRouter(prefix="/decisions", tags=["decisions"])


@router.post("/stp", response_model=StpScoreResponse)
async def score_stp(
    req: StpScoreRequest,
    _: None = Depends(require_service_token),
) -> StpScoreResponse:
    """Evaluate application for Straight-Through Processing (STP) auto-approval."""
    result = await jev_service.evaluate_stp(req.application)
    return StpScoreResponse(
        auto_approve_safe=result["auto_approve_safe"],
        probability=result["probability"],
        risk_level=result["risk_level"],
        routing=result["routing"],
        latency_ms=result.get("latency_ms", 0.0),
    )


@router.post("/intent", response_model=JagoIntentResponse)
async def classify_intent(
    req: JagoIntentRequest,
    _: None = Depends(require_service_token),
) -> JagoIntentResponse:
    """Classify student intent for JAGO+ scholarship assistant."""
    result = await jev_service.classify_jago_intent(req.user_message, req.lang)
    return JagoIntentResponse(
        intent=result["intent"],
        needs_usid=result["needs_usid"],
        confidence=result["confidence"],
        latency_ms=result.get("latency_ms", 0.0),
    )


@router.post("/generic", response_model=JevDecisionResponse)
async def generic_decision(
    req: JevDecisionRequest,
    _: None = Depends(require_service_token),
) -> JevDecisionResponse:
    """Execute generic typed decision request via JEV 1.13."""
    try:
        decisions, latency = await jev_service.decide(
            state=req.state,
            questions=req.questions,
            model=req.model,
        )
        return JevDecisionResponse(
            decisions=decisions,
            model=req.model or "jev-1.13",
            latency_ms=latency,
        )
    except jev_service.JevUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


@router.post("/chat", response_model=ChatCompletionResponse)
async def chat(
    req: ChatCompletionRequest,
    _: None = Depends(require_service_token),
) -> ChatCompletionResponse:
    """Execute grounded chat completion via Groq (openai/gpt-oss-20b)."""
    try:
        reply, latency = await groq_service.chat_completion(
            messages=req.messages,
            temperature=req.temperature,
            max_tokens=req.max_tokens or 1024,
        )
        return ChatCompletionResponse(
            reply=reply,
            model="openai/gpt-oss-20b",
            latency_ms=latency,
        )
    except groq_service.GroqUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


@router.post("/dbt-explain")
async def explain_dbt(
    payload: dict[str, Any],
    _: None = Depends(require_service_token),
) -> dict[str, str]:
    """Generate empathetic action steps for a DBT payment failure."""
    code = payload.get("failure_code", "E006_OTHER")
    lang = payload.get("lang", "hi")
    details = payload.get("details", {})
    explanation = await groq_service.explain_dbt_failure(code, details, lang)
    return {"failure_code": code, "explanation": explanation}


@router.post("/fraud-screen", response_model=FraudScreenResponse)
async def screen_fraud(
    req: FraudScreenRequest,
    _: None = Depends(require_service_token),
) -> FraudScreenResponse:
    """Screen application for fraud, ghost beneficiaries, and duplicate claims."""
    result = await fraud_service.screen_application(req.application)
    return FraudScreenResponse(
        is_suspicious=result["is_suspicious"],
        fraud_risk_score=result["fraud_risk_score"],
        risk_level=result["risk_level"],
        flags=result["flags"],
        recommendation=result["recommendation"],
        latency_ms=result.get("latency_ms", 0.0),
        fallback=result.get("fallback", False),
    )
