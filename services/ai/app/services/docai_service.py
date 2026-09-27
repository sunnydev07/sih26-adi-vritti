"""Doc AI: OCR + field extraction + issuer-format/tamper checks (Tier 3 input)."""

from __future__ import annotations

from datetime import datetime, timezone


def parse_document(text: str, claim_type: str) -> dict:
    fields: dict = {"raw_text": text[:2000], "claim_type": claim_type}
    signals: list[str] = []
    lowered = text.lower()
    if "govt" not in lowered and "government" not in lowered and "प्रमाण" not in text:
        signals.append("issuer_format_unrecognized")
    if len(text.strip()) < 20:
        signals.append("insufficient_text")
    confidence = 0.85 if not signals else 0.45
    return {
        "fields": fields,
        "tamper_signals": signals,
        "confidence": confidence,
        "parsed_at": datetime.now(timezone.utc),
    }
