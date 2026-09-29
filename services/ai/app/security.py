"""Service-to-service authentication for the AI service.

The AI service sits behind Core, but it also listens on a network port and two of
its routes handle sensitive material:

* ``POST /gap/hash`` takes a raw Aadhaar reference in the request body. The whole
  privacy design of the coverage-gap feature is that ministries exchange only
  hashed keys, so an unauthenticated endpoint that accepts raw references undoes
  it and gives anyone who can reach the port a hash oracle to build a rainbow
  table from.
* ``POST /jago/tool/{name}`` proxies to Core on behalf of an arbitrary USID.

So every route except ``/health`` requires a shared bearer token, compared in
constant time to avoid leaking the token byte-by-byte through timing.

Having a token is not enough, though: the default in ``app.config`` is
``dev-only-ai-service-token``, a string committed to this repository. A gate that
merely checks "is a token configured" therefore protects nothing in a default
deployment, which is exactly the configuration ``make dev`` produces. So the gate
here refuses to serve *any* protected route while the configured token is blank or
still that published value, unless ``ALLOW_INSECURE_DEV`` is explicitly turned on
(see the module docstring in ``app.config`` for how to set a real one).
``/health`` does not depend on this, so a misconfigured service still answers
liveness and a container healthcheck does not flap.
"""

from __future__ import annotations

import hmac
import logging

from fastapi import Depends, HTTPException, Request, status

from app.config import DEV_TOKEN, settings

logger = logging.getLogger(__name__)

# Compared with hmac.compare_digest, not ==, so the comparison time does not
# depend on how many leading bytes happen to match.
_AUTH_HEADER = "x-ai-service-token"

# A deployment error rather than a client error: the caller did nothing wrong and
# retrying will not help, so this is 503 and not 401.
_TOKEN_UNUSABLE_DETAIL = (
    "AI_SERVICE_TOKEN is blank or still the published development value, so the "
    "AI service refuses to serve protected routes. Set AI_SERVICE_TOKEN to a "
    "private secret, or set ALLOW_INSECURE_DEV=true to re-enable them for a "
    "local demo only."
)

# The dev escape hatch is a process-wide state, so the warning is emitted once
# rather than on every request it makes possible.
_insecure_dev_warned = False


def _insecure_dev_opt_in() -> bool:
    """True when the operator explicitly accepted the development token."""
    global _insecure_dev_warned
    if not settings.allow_insecure_dev:
        return False
    if not _insecure_dev_warned:
        _insecure_dev_warned = True
        logger.warning(
            "SECURITY: ALLOW_INSECURE_DEV=true - %s is blank or the published "
            "development value, so it authenticates anyone who can read this "
            "repository. /gap/hash will hash arbitrary Aadhaar references. Local "
            "demos only.",
            _AUTH_HEADER,
        )
    return True


def require_service_token(request: Request) -> None:
    """FastAPI dependency: reject callers without the shared service token."""
    configured = settings.ai_service_token.strip()
    # Blank and "still the published development value" are the same failure: the
    # only thing protecting the raw-PII routes is a string that is in git, so
    # there is nothing to compare against. Keyed off the *value*, not off whether
    # one is set, so a deployment that forgot to override the default fails
    # closed instead of running on a public credential.
    if (not configured or configured == DEV_TOKEN) and not _insecure_dev_opt_in():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=_TOKEN_UNUSABLE_DETAIL,
        )

    presented = request.headers.get(_AUTH_HEADER) or ""
    # Also accept Authorization: Bearer <token> so standard clients work.
    auth = request.headers.get("authorization") or ""
    if not presented and auth.lower().startswith("bearer "):
        presented = auth[7:].strip()

    if not hmac.compare_digest(presented.encode(), configured.encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing AI service token",
        )


ServiceAuth = Depends(require_service_token)
