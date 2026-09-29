"""The service-token gate.

These are the tests that matter most in this service: two of its routes take or
return sensitive material (/gap/hash takes a raw Aadhaar reference, /jago proxies
per-USID data), and it listens on a network port. An unauthenticated route is the
bug, so the gate is tested from the outside through the real app.

Having a token is not sufficient either, and these tests pin that too: a token
that is blank or the value committed to this repository protects nothing, so the
gate has to refuse rather than serve.
"""

from fastapi.testclient import TestClient

from app.config import DEV_TOKEN, settings
from app.main import app

TOKEN = "test-service-token"
client = TestClient(app)


def _auth(token: str = TOKEN) -> dict[str, str]:
    return {"x-ai-service-token": token}


def test_health_is_open_so_container_checks_work():
    # Deliberate: liveness must be reachable without a credential.
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "service": "ai"}


def test_gap_hash_rejects_anonymous_callers():
    # The whole privacy design rests on raw references never crossing the wire.
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"})
    assert r.status_code == 401
    assert "hashed_key" not in r.json()


def test_gap_hash_rejects_a_wrong_token():
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth("wrong"))
    assert r.status_code == 401
    assert "hashed_key" not in r.json()


def test_gap_hash_rejects_a_token_that_is_a_prefix_of_the_real_one():
    # Guards against a naive startswith/== comparison leaking the token.
    r = client.post(
        "/gap/hash",
        json={"aadhaar_ref": "AVR-123"},
        headers=_auth(TOKEN[:-1]),
    )
    assert r.status_code == 401


def test_gap_hash_accepts_the_service_token():
    r = client.post(
        "/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth()
    )
    assert r.status_code == 200
    assert len(r.json()["hashed_key"]) == 64  # hex sha256


def test_standard_authorization_bearer_header_is_accepted():
    r = client.post(
        "/gap/hash",
        json={"aadhaar_ref": "AVR-123"},
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert r.status_code == 200


def test_match_score_requires_a_token():
    record = {"full_name": "Sunita Meena", "dob": "2012-05-01"}
    assert client.post("/match/score", json={"a": record, "b": record}).status_code == 401
    assert (
        client.post("/match/score", json={"a": record, "b": record}, headers=_auth()).status_code
        == 200
    )


def test_docai_parse_requires_a_token():
    r = client.post(
        "/docai/parse",
        files={"file": ("c.txt", b"Government of India income certificate", "text/plain")},
    )
    assert r.status_code == 401


def test_rag_query_requires_a_token():
    assert client.post("/rag/query", json={"question": "who is eligible?"}).status_code == 401


def test_jago_tool_requires_a_token():
    usid = "11111111-1111-4111-8111-111111111111"
    r = client.post(f"/jago/tool/check_eligibility", json={"usid": usid})
    assert r.status_code == 401


def test_every_route_except_health_requires_the_token():
    """Behavioural sweep of the whole API surface.

    Written as observed behaviour rather than route introspection because
    FastAPI does not expose parameter-level Depends() consistently across
    versions, and a test that passes because it inspected nothing is worse than
    no test. Any new route added without the gate fails here.

    The route list comes from app.openapi() called in-process. openapi_url is
    disabled (the HTTP schema route is closed, see test_the_schema_is_not_served),
    but the method still works -- it is only the route that was removed.
    """
    spec = app.openapi()
    assert len(spec["paths"]) >= 7, f"unexpectedly small API: {sorted(spec['paths'])}"

    ungated: list[str] = []
    for path, operations in spec["paths"].items():
        for method in operations:
            if method.upper() in ("HEAD", "OPTIONS") or path == "/health":
                continue
            r = client.request(method.upper(), path, json={})
            if r.status_code != 401:
                ungated.append(f"{method.upper()} {path} -> {r.status_code}")

    assert not ungated, "routes reachable without the service token: " + ", ".join(ungated)


# --- the schema must not be served --------------------------------------------
#
# docs_url=None and redoc_url=None only hide the HTML explorers; FastAPI registers
# /openapi.json from openapi_url independently. Leaving it on published the whole
# route map to an unauthenticated caller, including that /gap/hash takes a raw
# aadhaar_ref and /jago/tool takes an arbitrary usid.


def test_the_schema_is_not_served():
    # Unauthenticated, it is a free map of the sensitive surface.
    r = client.get("/openapi.json")
    assert r.status_code == 404, r.text
    assert "aadhaar_ref" not in r.text


def test_the_interactive_docs_are_not_served():
    assert client.get("/docs").status_code == 404
    assert client.get("/redoc").status_code == 404


def test_the_schema_is_not_served_even_to_an_authenticated_caller():
    # Nothing in this service needs the schema, so it is closed to everyone.
    assert client.get("/openapi.json", headers=_auth()).status_code == 404


# --- a token is not enough: it has to not be the published one ---------------
#
# dev-only-ai-service-token is committed in this repository and in
# infra/docker-compose.yml. Anyone who can reach the dev box can read it, hash
# arbitrary Aadhaar references against it, and rebuild the table the privacy
# design exists to prevent.


def test_a_configured_token_is_accepted_without_the_dev_opt_in(monkeypatch):
    # The guard is keyed on the published VALUE, not on "is one set": the whole
    # test suite runs on this token with ALLOW_INSECURE_DEV off, and must keep
    # working. If this regresses, the gate has become "any non-empty string".
    monkeypatch.setattr(settings, "allow_insecure_dev", False)
    monkeypatch.setattr(settings, "ai_service_token", TOKEN)
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth())
    assert r.status_code == 200, r.text


def test_the_published_development_token_is_refused(monkeypatch):
    # The published value must not authenticate, even to a caller who presents
    # it. 503, not 401: nothing the caller can do will fix a server misconfigured
    # with a public credential.
    monkeypatch.setattr(settings, "ai_service_token", DEV_TOKEN)
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth(DEV_TOKEN))
    assert r.status_code == 503, r.text
    assert "hashed_key" not in r.json()
    assert "ALLOW_INSECURE_DEV" in r.json()["detail"]


def test_an_unset_token_is_refused(monkeypatch):
    monkeypatch.setattr(settings, "ai_service_token", "")
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth(""))
    assert r.status_code == 503, r.text
    assert "hashed_key" not in r.json()


def test_a_whitespace_only_token_is_treated_as_unset(monkeypatch):
    # Surrounding whitespace is a copy-paste artefact, not a secret.
    monkeypatch.setattr(settings, "ai_service_token", "   ")
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth("   "))
    assert r.status_code == 503, r.text


def test_the_refusal_applies_to_every_protected_route(monkeypatch):
    # A gate that only covers /gap/hash leaves /jago/tool open to the same caller.
    monkeypatch.setattr(settings, "ai_service_token", DEV_TOKEN)
    probes = [
        ("POST", "/gap/hash", {"aadhaar_ref": "AVR-123"}),
        ("POST", "/gap/query", {}),
        ("POST", "/match/score", {"a": {"full_name": "A B"}, "b": {"full_name": "A B"}}),
        ("POST", "/rag/query", {"question": "who is eligible?"}),
        (
            "POST",
            "/jago/tool/next_action",
            {"usid": "11111111-1111-4111-8111-111111111111"},
        ),
        ("POST", "/docai/parse", {}),
    ]
    for method, path, body in probes:
        r = client.request(method, path, json=body, headers=_auth(DEV_TOKEN))
        assert r.status_code == 503, f"{path} -> {r.status_code}: {r.text}"


def test_health_still_answers_while_the_token_is_unusable(monkeypatch):
    # Otherwise a misconfigured deployment fails its healthcheck, gets killed and
    # restarted in a loop, and the reason never gets read.
    monkeypatch.setattr(settings, "ai_service_token", DEV_TOKEN)
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "service": "ai"}


def test_the_dev_opt_in_re_enables_the_published_token(monkeypatch):
    # The escape hatch that keeps `make dev` usable, mirroring Core's
    # app.security.allow-insecure-dev.
    monkeypatch.setattr(settings, "ai_service_token", DEV_TOKEN)
    monkeypatch.setattr(settings, "allow_insecure_dev", True)
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth(DEV_TOKEN))
    assert r.status_code == 200, r.text
    assert len(r.json()["hashed_key"]) == 64


def test_the_dev_opt_in_still_checks_the_presented_token(monkeypatch):
    # The opt-in removes the 503, it does not turn authentication off.
    monkeypatch.setattr(settings, "ai_service_token", DEV_TOKEN)
    monkeypatch.setattr(settings, "allow_insecure_dev", True)
    r = client.post("/gap/hash", json={"aadhaar_ref": "AVR-123"}, headers=_auth("wrong"))
    assert r.status_code == 401

