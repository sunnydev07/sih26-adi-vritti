"""Coverage Gap engine: privacy-preserving HMAC join (no raw PII exchanged)."""

from __future__ import annotations

import hashlib
import hmac


def hashed_key(aadhaar_ref: str, salt: str, purpose: str = "coverage-gap") -> str:
    # Canonicalise the input: ministries that trim the reference and ones that
    # do not would otherwise compute different keys for the same person and
    # manufacture a false coverage gap.
    ref = (aadhaar_ref or "").strip()
    return hmac.new(
        f"{salt}:{purpose}".encode(), ref.encode(), hashlib.sha256
    ).hexdigest()


def left_anti_join(enrolled_keys: set[str], applicant_keys: set[str]) -> set[str]:
    """Keys enrolled (UDISE+/APAAR) with no NSP application: the gap."""
    return enrolled_keys - applicant_keys
