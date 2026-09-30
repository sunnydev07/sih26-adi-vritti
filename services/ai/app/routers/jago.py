from app.models.schemas import JagoHelpRequest, JagoHelpResponse, JagoToolCall
from app.security import require_service_token
from app.services import help_service, jago_service
from fastapi import APIRouter, Depends, HTTPException, status

router = APIRouter(prefix="/jago", tags=["jago"])


@router.post("/help", response_model=JagoHelpResponse)
async def ask_help(
    body: JagoHelpRequest,
    _: None = Depends(require_service_token),
) -> dict:
    """Ask Adi help lane: explain the app, schemes, and process.

    Deterministic and offline-safe (no LLM, no Core calls). Questions about
    the caller's own file are deflected with lane="status" and a suggested
    tool — personal answers must be template-filled from tool output, never
    free-generated here.
    """
    return help_service.answer(body.question, body.lang)


@router.post("/tool/{name}")
async def invoke_tool(
    name: str,
    call: JagoToolCall,
    _: None = Depends(require_service_token),
) -> dict:
    try:
        return await jago_service.invoke(name, str(call.usid), call.parameters)
    except ValueError as e:
        # Unknown tool name: the caller's fault.
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e)) from e
    except jago_service.CoreUnavailableError as e:
        # Upstream failure. Surfaced as 502 rather than being folded into a
        # successful-looking empty payload, which is what used to happen.
        #
        # str(e) is deliberately the same generic message for every upstream
        # failure: forwarding Core's status and route (as this used to) disclosed
        # the internal layout and, worse, turned 403-vs-404 into a probe for
        # which USIDs exist. The specifics are in the log, via e.reason.
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e)) from e
