"""Router for JEV System One decisions and Groq chat completions."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status

from app.config import settings
from app.models.schemas import (
    ChatCompletionRequest,
    ChatCompletionResponse,
    DbtExplainRequest,
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
from app.services import fraud_service, groq_service, help_service, jev_service, rag_service

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
        # Carried through rather than dropped: without it the console cannot tell
        # a live model verdict from the deterministic engine's, and labels both
        # "(Live)".
        fallback=result.get("fallback", False),
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
    """Grounded general-help chat via Groq (openai/gpt-oss-20b).

    This is NOT a status oracle: the app's contract is that the LLM never
    free-generates a status, amount, or eligibility verdict. Three interlocks
    enforce it — caller system messages are rejected at the schema boundary
    (the server owns the system prompt), personal-status questions are
    deflected to JAGO tools without ever reaching the model, and everything
    else is answered from retrieved official clauses that the reply must cite.
    """
    user_texts = [
        m.content.strip() for m in req.messages if m.role == "user" and m.content.strip()
    ]
    if not user_texts:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="at least one user message is required",
        )
    question = user_texts[-1][:2000]
    lang = req.lang if req.lang in ("hi", "en") else "hi"

    if help_service.is_status_question(question):
        deflection = help_service.answer(question, lang)
        return ChatCompletionResponse(
            reply=deflection["answer"],
            model=settings.groq_model,
            latency_ms=deflection["latency_ms"],
        )

    clauses = rag_service.retrieve_clauses(question, None, top_k=2)
    context = "\n".join(
        f"[{c['id']}] {c['scheme_name']} — {c['clause']}: {c['content']}" for c in clauses
    )
    system = (
        "You are Adi-Vritti's general help assistant. Answer ONLY general "
        "how-it-works questions using the official clauses below, citing the "
        "[ID] for every requirement or amount. You have NO personal data: NEVER "
        "state an application status, payment amount, or eligibility verdict. "
        "If asked for personal status, reply exactly with: "
        f"{help_service.DEFLECT_EN if lang == 'en' else help_service.DEFLECT_HI}"
        + (f"\n\nOfficial clauses:\n{context}" if context else "")
    )
    history = [{"role": m.role, "content": m.content} for m in req.messages[-10:]]
    try:
        reply, latency = await groq_service.chat_completion(
            messages=[{"role": "system", "content": system}, *history],
            temperature=min(req.temperature, 0.5),
            max_tokens=min(req.max_tokens or 1024, 1024),
        )
        return ChatCompletionResponse(
            reply=reply,
            model=settings.groq_model,
            latency_ms=latency,
        )
    except groq_service.GroqUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc


@router.post("/dbt-explain")
async def explain_dbt(
    payload: DbtExplainRequest,
    _: None = Depends(require_service_token),
) -> dict[str, str]:
    """Generate empathetic action steps for a DBT payment failure.

    The body is a validated model, not ``dict[str, Any]``: every field is
    length- and shape-bounded before it can reach the Groq prompt, so a caller
    cannot inject instructions through ``failure_code`` or blow the context
    budget through ``details``.
    """
    explanation = await groq_service.explain_dbt_failure(
        payload.failure_code, payload.details, payload.lang
    )
    return {"failure_code": payload.failure_code, "explanation": explanation}


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
