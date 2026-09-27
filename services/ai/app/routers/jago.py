from app.models.schemas import JagoToolCall
from app.services import jago_service
from fastapi import APIRouter, HTTPException

router = APIRouter(prefix="/jago", tags=["jago"])


@router.post("/tool/{name}")
async def invoke_tool(name: str, call: JagoToolCall) -> dict:
    try:
        return await jago_service.invoke(name, str(call.usid), call.parameters)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
