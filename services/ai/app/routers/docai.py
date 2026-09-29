from app.config import settings
from app.models.schemas import DocParseResponse, RagQuery
from app.security import require_service_token
from app.services import docai_service, rag_service
from fastapi import APIRouter, Depends, HTTPException, UploadFile, status
from starlette.concurrency import run_in_threadpool

router = APIRouter(tags=["docai", "rag"])

# `await file.read()` with no limit lets a single request pull arbitrary data
# into container memory, so the upload is read in bounded chunks and aborted
# as soon as it exceeds the configured ceiling.
CHUNK = 64 * 1024


@router.post("/docai/parse", response_model=DocParseResponse)
async def parse_doc(
    file: UploadFile,
    claim_type: str = "income",
    _: None = Depends(require_service_token),
) -> DocParseResponse:
    limit = settings.docai_max_upload_bytes
    raw = bytearray()
    while True:
        chunk = await file.read(CHUNK)
        if not chunk:
            break
        raw.extend(chunk)
        if len(raw) > limit:
            # Literal 413: Starlette renamed its constant to
            # HTTP_413_CONTENT_TOO_LARGE and deprecating the old name would pin us
            # to whichever version is installed.
            raise HTTPException(
                status_code=413,
                detail=f"Upload exceeds the {limit} byte limit",
            )

    # Errors are ignored on purpose: a non-UTF-8 upload still yields whatever
    # text the extractor can work with.
    text = bytes(raw).decode("utf-8", errors="ignore")

    # _normalise() and the five field patterns are pure CPU over the whole
    # upload, and this handler is async on a single-threaded event loop: calling
    # parse_document() directly froze every other request -- including /health --
    # for the duration of each parse. Offloaded so the loop keeps serving.
    parsed = await run_in_threadpool(docai_service.parse_document, text, claim_type)
    return DocParseResponse(**parsed)


@router.post("/rag/query")
def rag_query(q: RagQuery, _: None = Depends(require_service_token)) -> dict:
    return rag_service.answer(q.question, q.scheme)
