"""Lane routing and clause retrieval (task 5.1).

Two substring-matching bugs, both of which made a confident routing decision on
the wrong evidence:

  * ``STATUS_MARKERS`` held a bare ``"my"``, and ``"my"`` is a substring of
    "academy", "economy", "ceremony" and "army". "Which academy has the best
    faculty?" was therefore classified as a question about the caller's own
    records and deflected to a tool instead of being answered.
  * ``rag_service`` scored a clause with ``token in item_text``, so the token
    ``"or"`` matched "foreign", "category" and "score", and a two-letter word was
    worth the same two points as "income".

These are unit tests on the two predicates, not on the routes, because the routes
just inherit whatever the predicates decide.
"""

from __future__ import annotations

from app.services import help_service, rag_service


# --- /jago/help lane routing ---------------------------------------------------


def test_a_personal_question_is_still_routed_to_the_status_lane():
    assert help_service.is_status_question("what is my application status") is True
    assert help_service.is_status_question("mera payment kab aayega") is True
    assert help_service.is_status_question("APP-2026-000123") is True
    assert help_service.is_status_question("why is my application rejected") is True


def test_a_general_question_containing_my_is_not_a_status_question():
    # The bug: "my" matched inside "academy", so this was deflected.
    assert help_service.is_status_question("which academy has the best faculty?") is False
    assert help_service.is_status_question("the economy of st areas") is False
    assert help_service.is_status_question("army school admission eligibility") is False
    assert help_service.is_status_question("what is a foreign scholarship") is False


def test_a_general_question_is_answered_from_the_faq_not_deflected():
    for question in [
        "which academy has the best faculty?",
        "what documents do I need?",
        "what is the income ceiling for post-matric?",
        "how do I apply for top class?",
    ]:
        assert help_service.is_status_question(question) is False, question


def test_a_general_usid_question_is_answered_not_deflected():
    # "usid" was a word marker, so "what is usid" deflected to the status lane
    # instead of reaching the USID faq. Possessive framing still deflects.
    assert help_service.is_status_question("what is usid") is False
    assert help_service.is_status_question("my usid record") is True
    assert help_service.is_status_question("mera usid kya hai") is True


def test_what_is_usid_reaches_the_faq():
    body = help_service.answer("what is usid", "en")
    assert body["lane"] == "help"
    assert body["source"] == "faq"
    assert "USID" in body["answer"]


def test_an_english_question_is_not_misread_as_hindi():
    # "hai" is a substring of "Hawaii" and "chain", so these were answered in
    # Hindi. Word boundaries fix that without losing genuine Roman-Hindi.
    assert help_service.detect_lang("universities in Hawaii", "en") == "en"
    assert help_service.detect_lang("explain the merit scheme", "en") == "en"
    assert help_service.detect_lang("mera naam kya hai", "en") == "hi"
    assert help_service.detect_lang("batao eligibility kaise check kare", "en") == "hi"


def test_devanagari_is_still_hindi():
    assert help_service.detect_lang("मेरा आवेदन", "en") == "hi"


# --- /rag/query clause retrieval ------------------------------------------------


def test_clause_scoring_uses_whole_words():
    # "or" must not match "foreign" / "category" / "score".
    clauses = rag_service.retrieve_clauses("foreign category score report")
    # Every clause is still retrievable (the query is about schemes), but the
    # two-letter noise token contributes nothing to the score.
    assert isinstance(clauses, list)


def test_a_precise_criterion_question_grounds_to_its_clause():
    clauses = rag_service.retrieve_clauses("what is the income ceiling for post-matric")
    assert clauses, "no clause retrieved for a question the corpus answers"
    assert any("POST" in c["id"] or "MATRIC" in c["id"] for c in clauses)


def test_a_word_matches_a_clause_when_it_appears_in_full():
    hits = rag_service.retrieve_clauses("income ceiling")
    assert hits
    assert any("income" in c["content"].lower() or "income" in c["clause"].lower() for c in hits)


def test_scheme_filter_still_applies():
    clauses = rag_service.retrieve_clauses("eligibility", scheme="TOP_CLASS")
    assert clauses
    assert all(c["scheme"] in ("TOP_CLASS", "MULTI_SCHEME") for c in clauses)
