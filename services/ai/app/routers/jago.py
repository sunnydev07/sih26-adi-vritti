from app.models.schemas import JagoToolCall
from app.security import require_service_token
from app.services import jago_service
from fastapi import APIRouter, Depends, HTTPException, status

router = APIRouter(prefix="/jago", tags=["jago"])


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
