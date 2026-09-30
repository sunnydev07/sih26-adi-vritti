"""Ask Adi help lane (/jago/help).

Pins the lane split that keeps JAGO+ safe: general questions get static,
cited answers; ANY question about the caller's own file is deflected with
lane="status" and a suggested tool, and the endpoint NEVER returns personal
status, amounts, or verdicts. Also pins determinism (no network) and auth.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import app
from app.services import help_service

AUTH = {"x-ai-service-token": "test-service-token"}
client = TestClient(app)


def ask(question: str, lang: str = "hi"):
    return client.post("/jago/help", json={"question": question, "lang": lang}, headers=AUTH)


# --- auth --------------------------------------------------------------------


def test_help_requires_the_service_token():
    r = client.post("/jago/help", json={"question": "what is this app"})
    assert r.status_code == 401


def test_help_rejects_an_empty_question():
    r = client.post("/jago/help", json={"question": ""}, headers=AUTH)
    assert r.status_code == 422


# --- FAQ lane ----------------------------------------------------------------


def test_what_is_this_app_matches_faq_in_english():
    r = ask("What is Adi-Vritti app about?", "en")
    assert r.status_code == 200
    body = r.json()
    assert body["lane"] == "help"
    assert body["source"] == "faq"
    assert "one student, one identity, five schemes" in body["answer"]
    assert body["citations"][0]["id"] == "what-is"
    assert len(body["suggestions"]) > 0


def test_demo_tour_matches_in_hindi():
    r = ask("demo kaise chalaye", "hi")
    assert r.status_code == 200
    body = r.json()
    assert body["lane"] == "help"
    assert body["citations"][0]["id"] == "demo-tour"
    assert "3 चरण" in body["answer"]


def test_devanagari_auto_detects_hindi_despite_en_param():
    r = ask("यह app क्या है", "en")
    assert r.json()["lang"] == "hi"


def test_payment_failed_matches_without_personal_data():
    r = ask("why did my payment fail", "en")
    body = r.json()
    # "my payment" is personal -> deflected, NOT answered as a personal status.
    assert body["lane"] == "status"
    assert body["suggested_tool"] == "why_is_payment_pending"


def test_payment_failed_generic_is_cited_when_impersonal():
    r = ask("what are common bank payment failure reasons", "en")
    body = r.json()
    assert body["lane"] == "help"
    assert body["citations"][0]["id"] == "payment-failed"


# --- the lane split: personal questions must NEVER get free-text status ------


def test_my_application_status_is_deflected():
    r = ask("where is my application", "en")
    body = r.json()
    assert body["lane"] == "status"
    assert body["suggested_tool"] == "get_my_applications"
    assert "never guess" in body["answer"]


def test_hindi_personal_payment_question_is_deflected():
    r = ask("mera payment kab aayega", "hi")
    body = r.json()
    assert body["lane"] == "status"
    assert body["suggested_tool"] == "why_is_payment_pending"
    assert "अंदाज़ा" in body["answer"]


def test_app_id_pattern_is_deflected():
    r = ask("APP-90412 status?", "en")
    assert r.json()["lane"] == "status"


def test_deflection_carries_no_personal_data():
    for q in ("where is my file", "mera paisa kab aayega", "my usid record"):
        body = ask(q).json()
        assert body["lane"] == "status"
        assert body["suggested_tool"] is not None
        assert "output" not in body
        assert "template_id" not in body


# --- clause grounding for general criteria -----------------------------------


def test_general_eligibility_uses_clauses_with_disclaimer():
    r = ask("what is the income ceiling for post-matric scholarship", "en")
    body = r.json()
    assert body["lane"] == "help"
    assert body["source"] == "clauses"
    assert any(c["kind"] == "clause" for c in body["citations"])
    assert "2,50,000" in body["answer"]
    assert "check Home" in body["answer"]


def test_unknown_question_falls_back_safely():
    body = ask("tell me about the weather on Mars", "en").json()
    assert body["lane"] == "help"
    assert body["source"] == "fallback"
    assert len(body["suggestions"]) == 3


def test_greeting_and_language_switch():
    assert ask("namaste").json()["source"] == "greeting"
    body = ask("talk in Hindi").json()
    assert body["source"] == "lang-switch"
    assert body["lang"] == "hi"


# --- determinism: no network, stable shapes ----------------------------------


def test_every_path_returns_latency_and_suggestions():
    for q in ("hi", "what is usid", "my file status", "xyzzy plugh", "income ceiling top class"):
        body = ask(q).json()
        assert isinstance(body["latency_ms"], float)
        assert isinstance(body["suggestions"], list)
        assert body["lang"] in ("hi", "en")


def test_service_level_answer_matches_route():
    # The router must be a thin wrapper over the service.
    direct = help_service.answer("what is usid", "en")
    body = ask("what is usid", "en").json()
    assert body["answer"] == direct["answer"]
    assert body["lane"] == direct["lane"]
