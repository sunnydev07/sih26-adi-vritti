"""Scheme RAG: answers grounded in guideline chunks WITH clause citations."""

from __future__ import annotations

# pgvector-backed in full wiring: ingest 5 scheme PDFs -> chunk -> embed -> pgvector.
# Contract: NEVER invent eligibility rules; every answer cites its clause.


def answer(question: str, scheme: str | None = None) -> dict:
    return {
        "answer": "RAG index not yet ingested — wire guideline PDFs via scripts/ingest.py.",
        "citations": [],
        "question": question,
        "scheme": scheme,
    }
