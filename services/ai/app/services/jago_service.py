"""JAGO+ tool router: calls Core, returns STRUCTURED output for template-filling."""

from __future__ import annotations

import logging

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

TOOLS = {
    "get_my_applications",
    "check_eligibility",
    "explain_deficiency",
    "why_is_payment_pending",
    "next_action",
    "list_required_documents",
    "get_disbursement_history",
}

TIMEOUT_SECONDS = 10.0

# A wrong route silently answers 404, and a missing/expired token answers 401/403.
# Both used to be folded into an empty-but-successful payload, so a completely
# broken integration looked exactly like a student with no applications.
ROUTE_FOR_TOOL: dict[str, tuple[str, str]] = {
    # `get_my_applications` goes through Core's JAGO tool endpoint, which reads
    # the application rows. Pointing it at the disbursement endpoint returned
    # payment data for a question about applications.
    "get_my_applications": ("POST", "/v1/jago/tool/get_my_applications"),
    "why_is_payment_pending": ("GET", "/v1/disbursements/{usid}"),
    "get_disbursement_history": ("GET", "/v1/disbursements/{usid}"),
    "check_eligibility": ("POST", "/v1/eligibility/evaluate"),
    "explain_deficiency": ("GET", "/v1/scholars/{usid}/dashboard"),
    "next_action": ("GET", "/v1/scholars/{usid}/dashboard"),
    "list_required_documents": ("GET", "/v1/scholars/{usid}/dashboard"),
}

# Every upstream failure collapses to this one string in the response body.
# The per-request status was previously forwarded verbatim, which turned a 502
# into an authorization oracle: 403 vs 404 says whether a USID exists or whether
# the caller's credential merely lacks a role, and that is precisely the
# distinction Core's ScholarAccessGuard erases so its endpoints cannot be used
# to probe which USIDs are real. Repeating it here would undo that, and the
# internal route layout came with it. The specifics go to the log instead.
CLIENT_DETAIL = "upstream service unavailable"


class CoreUnavailableError(RuntimeError):
    """Core did not return a usable response.

    ``str(exc)`` is the generic message the router hands to the caller and is the
    only part that can reach a response. ``reason`` carries the specifics for the
    server-side log and is never serialised.
    """

    def __init__(self, reason: str) -> None:
        super().__init__(CLIENT_DETAIL)
        self.reason = reason


def _unavailable(reason: str) -> CoreUnavailableError:
    """Log the specifics server-side and return a generic client-facing error."""
    logger.warning("JAGO proxy failed: %s", reason)
    return CoreUnavailableError(reason)


#: Longest single filter value forwarded to Core (query string on GET).
_MAX_FILTER_CHARS = 256


def _scalar_filters(params: dict) -> dict:
    """Caller filters reduced to short scalars safe for a URL query string."""
    flat: dict[str, str | int | float | bool] = {}
    for key, value in params.items():
        if value is None or key == "usid":
            continue
        if not isinstance(key, str) or len(key) > 64:
            logger.warning("JAGO proxy dropping non-string filter key of length %d", len(str(key)))
            continue
        if isinstance(value, bool):
            flat[key] = value
        elif isinstance(value, (str, int, float)):
            if len(str(value)) > _MAX_FILTER_CHARS:
                logger.warning(
                    "JAGO proxy dropping oversized filter %r (%d chars)", key, len(str(value))
                )
                continue
            flat[key] = value
        else:
            logger.warning(
                "JAGO proxy dropping non-scalar filter %r (%s)", key, type(value).__name__
            )
    return flat


def _headers() -> dict[str, str]:
    # Core is JWT-protected. Without forwarding a credential every call 401s.
    headers = {"accept": "application/json"}
    if settings.core_service_token:
        headers["authorization"] = f"Bearer {settings.core_service_token}"
    return headers


async def invoke(tool: str, usid: str, params: dict) -> dict:
    if tool not in ROUTE_FOR_TOOL:
        raise ValueError(f"Unknown JAGO tool: {tool}")

    method, template = ROUTE_FOR_TOOL[tool]
    path = template.format(usid=usid)

    # Caller-supplied filters (academic_year, scheme, ...) used to be dropped on
    # the floor, so the API looked like it honoured them while Core never saw
    # them. They now reach Core: merged into the body on POST, query params on
    # GET. `None` values are dropped so an unset filter is absent rather than sent
    # as the string "None".
    #
    # `usid` is never taken from the caller: the tool is routed for a specific
    # scholar and the USID is already in the path, so a second, caller-supplied
    # one would either contradict the route or shadow it as a query parameter.
    #
    # Only scalars travel, and short ones: a nested dict used to be str()-ed
    # into garbage like "?a={'b': 'c'}" on GET, and a megabyte string into a
    # 414. Anything else is dropped (and logged) rather than forwarded.
    filters = _scalar_filters(params or {})
    if method == "POST":
        json_body: dict | None = {**filters, "usid": usid}
        query: dict | None = None
    else:
        json_body = None
        query = filters or None

    try:
        async with httpx.AsyncClient(
            base_url=settings.core_service_url, timeout=TIMEOUT_SECONDS
        ) as c:
            r = await c.request(
                method, path, json=json_body, params=query, headers=_headers()
            )
    except httpx.TimeoutException as e:
        raise _unavailable(
            f"Core timed out after {TIMEOUT_SECONDS}s calling {method} {path}"
        ) from e
    except httpx.HTTPError as e:
        # Only the exception type is logged: str(e) routinely embeds the
        # request headers, including the Core bearer credential.
        raise _unavailable(
            f"Core is unreachable calling {method} {path} ({type(e).__name__})"
        ) from e

    if r.status_code >= 400:
        # 401/403 means the caller's credential is wrong -- that must never look
        # like "no data". 404 means the route drifted from Core's contract.
        raise _unavailable(
            f"Core returned {r.status_code} for {method} {path}"
        )

    try:
        data = r.json()
    except ValueError as e:
        raise _unavailable(
            f"Core returned a non-JSON body for {method} {path}"
        ) from e

    # NEVER free-generate status: return structured output + template id only.
    return {"tool": tool, "usid": usid, "output": data, "template_id": f"jago.{tool}.v1"}
