"""Scheme RAG: answers grounded in guideline chunks WITH clause citations."""

from __future__ import annotations

# pgvector-backed in full wiring: ingest 5 scheme PDFs -> chunk -> embed -> pgvector.
# Contract: NEVER invent eligibility rules; every answer cites its clause.
#
# Until the index is ingested this returns no answer rather than a plausible
# sentence. A fabricated eligibility answer is worse than no answer here,
# because callers have no way to tell the difference.


def answer(question: str, scheme: str | None = None) -> dict:
    return {
        "answer": None,
        "citations": [],
        "question": question,
        "scheme": scheme,
        "note": (
            "RAG index not yet ingested. Guidelines must be loaded before this "
            "endpoint can answer; it deliberately declines rather than guessing."
        ),
    }
