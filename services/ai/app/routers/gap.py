from app.config import settings
from app.models.schemas import GapQuery
from app.security import require_service_token
from app.services import gap_service
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

router = APIRouter(prefix="/gap", tags=["gap"])


class HashRequest(BaseModel):
    # Bounded: this is an identifier, not a document.
    aadhaar_ref: str = Field(min_length=1, max_length=64)


@router.post("/hash")
def hash_key(
    req: HashRequest, _: None = Depends(require_service_token)
) -> dict:
    # Each ministry computes this locally under the shared rotating salt; only
    # hashed keys cross the wire, so no raw reference is ever shared. The token
    # gate on this route matters precisely because the request body here *is* a
    # raw reference.
    if not settings.gap_hmac_salt.strip():
        raise HTTPException(status_code=503, detail="GAP_HMAC_SALT is not configured")
    return {"hashed_key": gap_service.hashed_key(req.aadhaar_ref, settings.gap_hmac_salt)}


@router.post("/query")
def query(q: GapQuery, _: None = Depends(require_service_token)) -> dict:
    # Coverage analytics are served by Core (/v1/admin/coverage-gap), which owns
    # the database access and the officer authorisation. This route stays a
    # documented stub rather than returning invented figures.
    return {
        "total_enrolled": 0,
        "total_applicants": 0,
        "total_gap": 0,
        "schools": [],
        "filters": q.model_dump(),
        "note": "not implemented here; use GET /v1/admin/coverage-gap on Core",
    }
