"""JAGO proxy behaviour.

The original implementation folded every non-200 from Core into an empty but
successful payload:

    data = r.json() if r.status_code == 200 else {"disbursements": []}

So a 401 from Core (missing/expired token), a 404 (route drift), a 500 and a
genuine "no data" response were indistinguishable to the caller. A completely
broken integration looked exactly like a student with no applications. These
tests pin the difference.
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.services import jago_service

USID = "11111111-1111-4111-8111-111111111111"
AUTH = {"x-ai-service-token": "test-service-token"}
client = TestClient(app)


class _Response:
    def __init__(self, status_code: int, payload=None, *, body: str | None = None):
        self.status_code = status_code
        self._payload = payload
        self._body = body

    def json(self):
        if self._body is not None:
            raise ValueError("not json")
        return self._payload


class _FakeClient:
    """Records the outgoing request so routing, auth and body/query can be asserted."""

    last: dict = {}

    def __init__(self, response, *, raise_exc: Exception | None = None):
        self._response = response
        self._raise = raise_exc

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def request(self, method, path, json=None, params=None, headers=None):
        type(self).last = {
            "method": method, "path": path, "json": json, "params": params, "headers": headers
        }
        if self._raise is not None:
            raise self._raise
        return self._response


def _install(monkeypatch, response=None, raise_exc=None):
    monkeypatch.setattr(
        jago_service.httpx,
        "AsyncClient",
        lambda *a, **k: _FakeClient(response, raise_exc=raise_exc),
    )


# --- unknown tools -------------------------------------------------------------


def test_unknown_tool_is_a_400():
    r = client.post("/jago/tool/rm_rf", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 400
    assert "Unknown JAGO tool" in r.json()["detail"]


# --- the regression: upstream failures must not look like empty data -----------


def test_core_401_becomes_502_not_an_empty_list(monkeypatch):
    _install(monkeypatch, _Response(401, {"error": "unauthorized"}))
    r = client.post("/jago/tool/get_disbursement_history", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502, r.text
    body = r.json()
    # The critical assertion: no fabricated "you have no payments" payload. The
    # upstream status used to be echoed here; it no longer is -- see the
    # authorisation-oracle tests below.
    assert "output" not in body


def test_core_403_becomes_502(monkeypatch):
    # 403 is what Core returns when ScholarAccessGuard rejects the USID.
    _install(monkeypatch, _Response(403, {"error": "forbidden"}))
    r = client.post("/jago/tool/why_is_payment_pending", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502


def test_core_404_becomes_502(monkeypatch):
    _install(monkeypatch, _Response(404, {"error": "not found"}))
    r = client.post("/jago/tool/next_action", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502


def test_core_500_becomes_502(monkeypatch):
    _install(monkeypatch, _Response(500, {"error": "boom"}))
    r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502


def test_timeout_becomes_502(monkeypatch):
    import httpx

    _install(monkeypatch, raise_exc=httpx.TimeoutException("too slow"))
    r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502
    assert "timed out" not in r.json()["detail"]


def test_connection_error_becomes_502(monkeypatch):
    import httpx

    _install(monkeypatch, raise_exc=httpx.ConnectError("refused"))
    r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502


def test_non_json_body_becomes_502(monkeypatch):
    # Core returning an HTML error page must not raise out of the route.
    _install(monkeypatch, _Response(200, body="<html>gateway</html>"))
    r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502


# --- happy path and routing ----------------------------------------------------


def test_successful_call_returns_structured_output(monkeypatch):
    _install(monkeypatch, _Response(200, {"verdicts": [{"scheme": "NMSS", "eligible": True}]}))
    r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 200
    body = r.json()
    assert body["tool"] == "check_eligibility"
    assert body["usid"] == USID
    assert body["output"]["verdicts"][0]["scheme"] == "NMSS"
    # A template id, not generated prose: JAGO must never free-generate status.
    assert body["template_id"] == "jago.check_eligibility.v1"
    assert "answer" not in body


def test_an_empty_but_successful_core_response_stays_successful(monkeypatch):
    # The real "no data" case must remain distinguishable from the failures above.
    _install(monkeypatch, _Response(200, {"disbursements": []}))
    r = client.post("/jago/tool/get_disbursement_history", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 200
    assert r.json()["output"] == {"disbursements": []}


@pytest.mark.parametrize(
    ("tool", "method", "path"),
    [
        ("get_my_applications", "GET", f"/v1/disbursements/{USID}"),
        ("why_is_payment_pending", "GET", f"/v1/disbursements/{USID}"),
        ("get_disbursement_history", "GET", f"/v1/disbursements/{USID}"),
        ("check_eligibility", "POST", "/v1/eligibility/evaluate"),
        ("explain_deficiency", "GET", f"/v1/scholars/{USID}/dashboard"),
        ("next_action", "GET", f"/v1/scholars/{USID}/dashboard"),
        ("list_required_documents", "GET", f"/v1/scholars/{USID}/dashboard"),
    ],
)
def test_every_tool_maps_to_a_core_route(monkeypatch, tool, method, path):
    # Pins the tool -> Core route table against Core's actual controller paths.
    _install(monkeypatch, _Response(200, {}))
    r = client.post(f"/jago/tool/{tool}", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 200
    assert _FakeClient.last["method"] == method
    assert _FakeClient.last["path"] == path


def test_the_usid_is_always_sent_to_core(monkeypatch):
    _install(monkeypatch, _Response(200, {}))
    client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    # POST evaluate takes the USID in the body.
    assert _FakeClient.last["json"] == {"usid": USID}


# --- caller parameters must actually reach Core -------------------------------
#
# `json_body = {"usid": usid} if method == "POST" else None` threw away the
# parameters the router had already validated and passed in, so filters like
# academic_year or scheme never reached Core while the API looked like it honoured
# them. They are forwarded now: body on POST, query params on GET.


def test_post_parameters_are_merged_into_the_body(monkeypatch):
    _install(monkeypatch, _Response(200, {}))
    r = client.post(
        "/jago/tool/check_eligibility",
        json={"usid": USID, "parameters": {"academic_year": "2026-27", "scheme": "NMSS"}},
        headers=AUTH,
    )
    assert r.status_code == 200, r.text
    assert _FakeClient.last["json"] == {
        "usid": USID,
        "academic_year": "2026-27",
        "scheme": "NMSS",
    }
    # A body call has no query string to put them in.
    assert _FakeClient.last["params"] is None


def test_get_parameters_are_sent_as_query_params(monkeypatch):
    _install(monkeypatch, _Response(200, {}))
    r = client.post(
        "/jago/tool/next_action",
        json={"usid": USID, "parameters": {"academic_year": "2026-27"}},
        headers=AUTH,
    )
    assert r.status_code == 200, r.text
    assert _FakeClient.last["method"] == "GET"
    assert _FakeClient.last["params"] == {"academic_year": "2026-27"}
    # The USID is in the path for a GET, so the body stays empty.
    assert _FakeClient.last["json"] is None


def test_no_query_string_is_sent_when_there_are_no_parameters(monkeypatch):
    _install(monkeypatch, _Response(200, {}))
    client.post("/jago/tool/next_action", json={"usid": USID}, headers=AUTH)
    # Not {}: an empty params dict would still render as "?" in some clients.
    assert _FakeClient.last["params"] is None


def test_a_caller_cannot_override_the_usid_through_parameters(monkeypatch):
    # The tool is routed for a specific scholar. A `usid` in `parameters` is
    # dropped rather than merged, so it cannot contradict the path or shadow it
    # as a query parameter.
    other = "22222222-2222-4222-8222-222222222222"
    _install(monkeypatch, _Response(200, {}))
    client.post(
        "/jago/tool/check_eligibility",
        json={"usid": USID, "parameters": {"usid": other}},
        headers=AUTH,
    )
    assert _FakeClient.last["json"] == {"usid": USID}

    _install(monkeypatch, _Response(200, {}))
    client.post(
        "/jago/tool/next_action",
        json={"usid": USID, "parameters": {"usid": other}},
        headers=AUTH,
    )
    assert _FakeClient.last["path"] == f"/v1/scholars/{USID}/dashboard"
    assert other not in str(_FakeClient.last["params"])


def test_null_parameters_are_dropped_rather_than_forwarded(monkeypatch):
    # An explicit null must read as "no filter", not as the filter "None".
    _install(monkeypatch, _Response(200, {}))
    client.post(
        "/jago/tool/next_action",
        json={"usid": USID, "parameters": {"scheme": None, "academic_year": "2026-27"}},
        headers=AUTH,
    )
    assert _FakeClient.last["params"] == {"academic_year": "2026-27"}


# --- the 502 must not be an authorisation oracle -----------------------------
#
# `detail=str(e)` used to carry Core's status and route: "Core returned 404 for
# GET /v1/scholars/{usid}/dashboard". 403 vs 404 says whether a USID exists or
# whether the caller's credential merely lacks a role -- the exact distinction
# Core's ScholarAccessGuard collapses so its endpoints cannot be used to probe
# which USIDs are real.


@pytest.mark.parametrize("status_code", [401, 403, 404, 500, 502])
def test_every_upstream_status_collapses_to_one_client_message(monkeypatch, status_code):
    _install(monkeypatch, _Response(status_code, {"error": "x"}))
    r = client.post("/jago/tool/next_action", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502
    assert r.json()["detail"] == jago_service.CLIENT_DETAIL
    # A caller must not be able to tell these apart.
    assert str(status_code) not in r.json()["detail"]


def test_the_502_leaks_neither_the_core_route_nor_the_usid(monkeypatch):
    _install(monkeypatch, _Response(404, {"error": "not found"}))
    r = client.post("/jago/tool/next_action", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502
    body = r.text
    assert "/v1/scholars" not in body
    assert "dashboard" not in body
    assert USID not in body


def test_the_specifics_go_to_the_log_and_not_to_the_caller(monkeypatch, caplog):
    import logging

    _install(monkeypatch, _Response(403, {"error": "forbidden"}))
    with caplog.at_level(logging.WARNING, logger="app.services.jago_service"):
        r = client.post("/jago/tool/next_action", json={"usid": USID}, headers=AUTH)
    assert r.status_code == 502
    assert r.json()["detail"] == jago_service.CLIENT_DETAIL
    # The operator still gets everything they need to debug it.
    logged = caplog.text
    assert "403" in logged
    assert f"/v1/scholars/{USID}/dashboard" in logged


def test_transport_failures_are_also_generic_to_the_caller(monkeypatch):
    import httpx

    for exc in (httpx.TimeoutException("too slow"), httpx.ConnectError("refused")):
        _install(monkeypatch, raise_exc=exc)
        r = client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
        assert r.status_code == 502
        assert r.json()["detail"] == jago_service.CLIENT_DETAIL


def test_the_error_carries_the_reason_for_the_server_but_not_for_the_caller():
    err = jago_service.CoreUnavailableError("Core returned 404 for GET /v1/scholars/x")
    assert str(err) == jago_service.CLIENT_DETAIL
    assert "404" in err.reason


# --- credential forwarding -----------------------------------------------------


def test_core_credential_is_forwarded(monkeypatch):
    # Core is JWT-protected; without this header every call 401s.
    _install(monkeypatch, _Response(200, {}))
    client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    sent = _FakeClient.last["headers"]
    assert sent["authorization"] == f"Bearer {settings.core_service_token}"


def test_no_authorization_header_when_no_core_token_is_configured(monkeypatch):
    monkeypatch.setattr(settings, "core_service_token", "")
    _install(monkeypatch, _Response(200, {}))
    client.post("/jago/tool/check_eligibility", json={"usid": USID}, headers=AUTH)
    assert "authorization" not in _FakeClient.last["headers"]
