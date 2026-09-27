from app.config import settings
from app.models.schemas import GapQuery
from app.services import gap_service
from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/gap", tags=["gap"])


class HashRequest(BaseModel):
    aadhaar_ref: str


@router.post("/hash")
def hash_key(req: HashRequest) -> dict:
    # Each ministry computes this locally under the shared rotating salt;
    # only hashed keys are exchanged — no raw PII transfer.
    return {"hashed_key": gap_service.hashed_key(req.aadhaar_ref, settings.gap_hmac_salt)}


@router.post("/query")
def query(q: GapQuery) -> dict:
    return {
        "total_enrolled": 0,
        "total_applicants": 0,
        "total_gap": 0,
        "schools": [],
        "filters": q.model_dump(),
    }
