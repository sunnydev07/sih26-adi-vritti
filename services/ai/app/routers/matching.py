from app.models.schemas import MatchRequest, MatchResponse
from app.services import matching_service
from fastapi import APIRouter

router = APIRouter(prefix="/match", tags=["matching"])


@router.post("/score", response_model=MatchResponse)
def score(req: MatchRequest) -> MatchResponse:
    total, parts = matching_service.score(req.a.model_dump(), req.b.model_dump())
    return MatchResponse(
        confidence=total,
        needs_human_review=0.60 <= total < 0.90,
        breakdown=parts,
    )
