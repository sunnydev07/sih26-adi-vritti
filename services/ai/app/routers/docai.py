from app.models.schemas import DocParseResponse, RagQuery
from app.services import docai_service, rag_service
from fastapi import APIRouter, UploadFile

router = APIRouter(tags=["docai", "rag"])


@router.post("/docai/parse", response_model=DocParseResponse)
async def parse_doc(file: UploadFile, claim_type: str = "income") -> DocParseResponse:
    raw = (await file.read()).decode("utf-8", errors="ignore")
    out = docai_service.parse_document(raw, claim_type)
    return DocParseResponse(**out)


@router.post("/rag/query")
def rag_query(q: RagQuery) -> dict:
    return rag_service.answer(q.question, q.scheme)
