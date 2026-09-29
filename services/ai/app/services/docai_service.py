"""Doc AI: text extraction + issuer-format/tamper checks (Tier 3 input).

Design note on trust: this module does not verify anything about a document. OCR
text cannot establish that a certificate is genuine, so it reports *format* signals
and a self-assessed extraction confidence, and the caller (Core's
DocAiStrategy) decides how much weight that can carry. Issuing a high confidence
for an unrecognised issuer is exactly the fabrication this is meant to avoid.
"""

from __future__ import annotations

import re
from datetime import datetime, timezone

# Devanagari literals are written as escapes on purpose. Source files here have
# been corrupted by a bad encoding round-trip before, which silently turned the
# issuer match into a literal that could never match anything.
_HI_GOVT = "\u092d\u093e\u0930\u0924 \u0938\u0930\u0915\u093e\u0930"  # \u092d\u093e\u0930\u0924 \u0938\u0930\u0915\u093e\u0930
_HI_SARKAR = "\u0938\u0930\u0915\u093e\u0930"  # \u0938\u0930\u0915\u093e\u0930

ISSUER_MARKERS = (
    "government of india",
    "govt. of india",
    "govt of india",
    "government of nct",
    "ministry of",
    "department of",
    "tehsil",
    "district collector",
    "uidai",
    _HI_GOVT,
    _HI_SARKAR,
)

_RUPEE_SIGN = "\u20b9"  # \u20b9
_HI_RUPEE = "\u0930\u0941\u092a\u092f\u0947|\u0930\u0942\u092a\u092f\u0947|\u0930\u0941\u092a\u090f"  # \u0930\u0941\u092a\u092f\u0947

# Field-ish patterns. Deliberately conservative: a wrong number is worse than a
# missing one, so only unambiguous shapes are pulled out.
#
# Scoped (?i:...) flags are used so a case-insensitive keyword does not also make
# the captured value case-insensitive -- that is what let "certifies" match the
# certificate-number pattern and capture "ifies".
#
# EVERY quantified run below is bounded, and that is load-bearing rather than
# stylistic. These patterns are searched against the whole uploaded text (up to
# DOCAI_MAX_UPLOAD_BYTES, 5 MB by default), so a caller who posts a few megabytes
# of digits turns any unbounded `+`/`*` into a denial of service. The textbook
# shape is a greedy unbounded run followed by a mandatory literal: the engine
# matches the run, fails on the literal, backtracks one character and retries,
# and it repeats that from every start position inside the run. For
# `[\d,]+ ... rupees` on a 2,000,000-character digit run that is ~2*10^12
# backtracking steps -- hours of CPU for one request. Bounding the run makes it
# linear. Nothing here is anchored either, because anchoring would kill the
# leading-"Rs." form of the amount pattern.
_MAX_AMOUNT_DIGITS = 15  # no real rupee amount needs more digits than this
# Runs of separators between a field label and its value. Real documents put one
# or two characters there; a long run means the pattern is off its label anyway.
_MAX_LABEL_SEPARATORS = 12

_DATE = r"(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2})"

# An INR amount: an Indian-format digit run, optionally with paise.
_AMOUNT = rf"[\d,]{{1,{_MAX_AMOUNT_DIGITS}}}(?:\.\d{{1,2}})?"

_SEP = rf"[\s:#-]{{0,{_MAX_LABEL_SEPARATORS}}}"

_PATTERNS: dict[str, re.Pattern[str]] = {
    # Accepts a leading currency marker ("Rs. 250,000"), a trailing currency word
    # ("250000 \u0930\u0941\u092a\u092f\u0947"), or both. Hindi certificates routinely carry only the
    # trailing form, so a leading-only pattern silently extracted nothing.
    #
    # The (?<![\d,]) guard on the trailing form is a semantic no-op that costs
    # nothing and saves everything: without it the engine still retried every
    # start position *inside* a digit run, so the bound alone left ~15 attempts
    # per character. The guard rejects those positions in constant time, and the
    # leftmost position of a run is the only one that can ever match, because
    # search() stops at the first hit.
    "amount_inr": re.compile(
        rf"(?:rs\.?|inr|{_RUPEE_SIGN})\s*({_AMOUNT})"
        rf"|(?<![\d,])({_AMOUNT})\s*(?:{_HI_RUPEE}|rupees?)",
        re.IGNORECASE,
    ),
    # A bare date regex reports an income certificate's *issue* date as the
    # student's date of birth, which is a plausible-looking fabrication. Every
    # date field therefore requires its own label.
    # Hindi: \u091c\u0928\u094d\u092e \u0924\u093f\u0925\u093f (date of birth), \u091c\u093e\u0930\u0940 (issued).
    "date_of_birth": re.compile(
        rf"(?i:d\.?\s?o\.?\s?b\.?|date\s+of\s+birth|birth\s+date|\u091c\u0928\u094d\u092e)"
        rf"{_SEP}{_DATE}"
    ),
    "date_of_issue": re.compile(
        rf"(?i:date\s+of\s+issue|issued?\s+on|\u091c\u093e\u0930\u0940){_SEP}{_DATE}"
    ),
    # The label word sits between the keyword and the value on real documents
    # ("Certificate No: ABC/123"), so it is allowed through explicitly rather
    # than by loosening the whole separator class. Scoped (?i:) on the keyword
    # only, so the captured value stays case-sensitive.
    # Hindi equivalents: \u092a\u0902\u091c\u0940\u0915\u0930\u0923 (registration), \u092a\u094d\u0930\u092e\u093e\u0923 \u092a\u0924\u094d\u0930 (certificate).
    # Two adjacent separator runs and a value run of {4,64}: the value used to be
    # {4,}, which cannot backtrack (nothing follows it) but would happily copy
    # megabytes of upload into the response.
    "certificate_number": re.compile(
        r"(?i:cert(?:ificate)?|registration|reg|\u092a\u0902\u091c\u0940\u0915\u0930\u0923"
        r"|\u092a\u094d\u0930\u092e\u093e\u0923)"
        rf"\.?{_SEP}(?i:no\.?|number|serial)?{_SEP}([A-Z0-9/-]{{4,64}})"
    ),
    # Only read a trailing Aadhaar fragment when the word Aadhaar is actually
    # present. A bare \b[2-9]\d{3}\b matched any year or serial number, which
    # invented an identifier that was never on the document.
    # Both runs here were already bounded.
    "aadhaar_last4": re.compile(
        r"(?i:aadhaar|\u0906\u0927\u093e\u0930)[^\d]{0,24}(?:x{2,4}[\s-]*)?(\d{4})\b"
    ),
}

MIN_USABLE_CHARS = 20

# Field extraction runs over a bounded prefix rather than the whole upload. The
# upload cap is 5 MB by default, and every pattern here is linear, so cost would
# otherwise scale with whatever the caller uploaded. No real certificate needs
# more than this much text to yield its issuer, number, and amounts, and a
# truncated document yields fewer fields rather than a wrong answer.
MAX_ANALYSIS_CHARS = 200_000


def _normalise(text: str) -> str:
    """Lowercase, collapse whitespace, and strip the nukta forms OCR likes to add.

    Without the nukta fold, a legitimately issued "\u092a\u094d\u0930\u092e\u093e\u0923 \u092a\u0924\u094d\u0930" read as
    "\u092a\u094d\u0930\u092e\u093e\u0923 \u092a\u0924\u094d\u0930" fails every comparison, which is how a real document gets
    flagged as unrecognised.
    """
    t = text.lower().replace("\u200c", "").replace("\u200d", "")
    t = t.replace("\u0958", "").replace("\u0959", "").replace("\u095a", "")
    t = t.replace("\u093c", "")  # nukta, so bhasma/bhisma compare equal
    return re.sub(r"\s+", " ", t).strip()


def _extract(text: str) -> dict[str, object]:
    found: dict[str, object] = {}
    for name, pattern in _PATTERNS.items():
        m = pattern.search(text)
        if not m:
            continue
        # Patterns with alternations have several groups; take the first that
        # actually participated, and fall back to the whole match.
        value: object = m.group(0)
        for group in m.groups():
            if group is not None:
                value = group
                break
        found[name] = value
    return found


def parse_document(text: str, claim_type: str) -> dict:
    signals: list[str] = []
    if not text or not text.strip():
        return {
            "fields": {"claim_type": claim_type, "extraction_status": "empty"},
            "tamper_signals": ["no_text_extracted"],
            "confidence": 0.0,
            "parsed_at": datetime.now(timezone.utc),
        }

    normalised = _normalise(text)
    analysed = text[:MAX_ANALYSIS_CHARS]
    fields = _extract(analysed)
    fields["claim_type"] = claim_type
    fields["raw_text"] = text[:2000]
    if len(text) > MAX_ANALYSIS_CHARS:
        fields["analysis_truncated"] = True

    issuer_recognised = any(m in normalised for m in ISSUER_MARKERS)
    if not issuer_recognised:
        signals.append("issuer_format_unrecognized")
    if len(normalised) < MIN_USABLE_CHARS:
        signals.append("insufficient_text")
    if not fields.keys() - {"claim_type", "raw_text"}:
        signals.append("no_fields_extracted")

    # Confidence reflects extraction quality, not authenticity. With nothing
    # recognised it drops to 0.3 rather than the 0.45/0.85 pair the old code used.
    if not signals:
        confidence = 0.80
    elif "no_text_extracted" in signals:
        confidence = 0.0
    elif len(signals) == 1 and signals[0] == "issuer_format_unrecognized":
        # Text is readable and fields parsed; only the issuer is unrecognised.
        confidence = 0.60
    else:
        confidence = 0.30

    return {
        "fields": fields,
        "tamper_signals": signals,
        "confidence": confidence,
        "parsed_at": datetime.now(timezone.utc),
    }
