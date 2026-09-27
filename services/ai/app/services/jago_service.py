"""JAGO+ tool router: calls Core, returns STRUCTURED output for template-filling."""

from __future__ import annotations

import httpx

from app.config import settings

TOOLS = {
    "get_my_applications",
    "check_eligibility",
    "explain_deficiency",
    "why_is_payment_pending",
    "next_action",
    "list_required_documents",
    "get_disbursement_history",
}


async def invoke(tool: str, usid: str, params: dict) -> dict:
    if tool not in TOOLS:
        raise ValueError(f"Unknown JAGO tool: {tool}")
    async with httpx.AsyncClient(base_url=settings.core_service_url, timeout=10) as c:
        if tool in ("get_my_applications", "why_is_payment_pending", "get_disbursement_history"):
            r = await c.get(f"/v1/disbursements/{usid}")
            data = r.json() if r.status_code == 200 else {"disbursements": []}
        elif tool == "check_eligibility":
            r = await c.post("/v1/eligibility/evaluate", json={"usid": usid})
            data = r.json() if r.status_code == 200 else {"verdicts": []}
        else:
            r = await c.get(f"/v1/scholars/{usid}/dashboard")
            data = r.json() if r.status_code == 200 else {}
    # NEVER free-generate status: return structured output + template id only.
    return {"tool": tool, "usid": usid, "output": data, "template_id": f"jago.{tool}.v1"}
