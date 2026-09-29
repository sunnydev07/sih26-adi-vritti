"""Adi-Vritti AI service entrypoint.

Every route except /health requires the shared service token (see app.security).
/health is intentionally unauthenticated: container health checks and load
balancers need to reach it, and it reveals nothing beyond liveness.
"""

from fastapi import FastAPI

from app.routers import docai, gap, jago, matching

app = FastAPI(
    title="Adi-Vritti AI Services",
    version="1.0.0",
    # No interactive docs in a deployment: this service is internal and
    # /docs would advertise its surface to anyone who can reach the port.
    docs_url=None,
    redoc_url=None,
    # docs_url/redoc_url only hide the HTML explorers. FastAPI registers the
    # schema route from openapi_url independently, so /openapi.json stayed
    # reachable with no token and published the entire route map -- including
    # that /gap/hash takes a raw aadhaar_ref and /jago/tool takes an arbitrary
    # usid, which is a map of exactly where to aim a leaked token.
    # app.openapi() still works in-process; the HTTP route is what is closed.
    openapi_url=None,
)

app.include_router(matching.router)
app.include_router(gap.router)
app.include_router(jago.router)
app.include_router(docai.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ai"}
