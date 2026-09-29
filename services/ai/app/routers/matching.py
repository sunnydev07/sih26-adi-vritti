from app.models.schemas import MatchRequest, MatchResponse
from app.security import require_service_token
from app.services import matching_service
from fastapi import APIRouter, Depends

router = APIRouter(prefix="/match", tags=["matching"])

# Below this a match is near-certainly a different person; at or above the upper
# bound the pair is near-certainly the same person. In between, a human decides.
AUTO_ACCEPT = 0.90
HUMAN_REVIEW_FLOOR = 0.60


@router.post("/score", response_model=MatchResponse)
def score(
    req: MatchRequest, _: None = Depends(require_service_token)
) -> MatchResponse:
    total, parts = matching_service.score(req.a.model_dump(), req.b.model_dump())
    return MatchResponse(
        confidence=total,
        needs_human_review=HUMAN_REVIEW_FLOOR <= total < AUTO_ACCEPT,
        breakdown=parts,
    )
