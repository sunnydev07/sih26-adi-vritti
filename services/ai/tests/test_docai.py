"""Doc AI: issuer recognition, extraction, and the upload cap."""

import time

from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.services import docai_service

TOKEN = "test-service-token"
client = TestClient(app)
AUTH = {"x-ai-service-token": TOKEN}

# A plausible income certificate. The Hindi form is the important case: the
# issuer check used to compare against a literal that had been corrupted by a
# bad encoding round-trip, so it matched nothing and every Hindi document was
# flagged as unrecognised.
HINDI_CERT = (
    "\u092d\u093e\u0930\u0924 \u0938\u0930\u0915\u093e\u0930\n"
    "\u0906\u092f\u0915\u0930 \u0935\u093f\u092d\u093e\u0917\n"
    "\u092a\u094d\u0930\u092e\u093e\u0923 \u092a\u0924\u094d\u0930\n"
    "\u092f\u0939 \u092a\u094d\u0930\u092e\u093e\u0923\u093f\u0924 \u0915\u0930\u0924\u093e \u0939\u0948 \u0915\u093f \u0938\u0941\u0928\u0940\u0924\u093e \u092e\u0940\u0928\u093e \u0915\u0940 \u0935\u093e\u0930\u094d\u0937\u093f\u0915 \u0906\u092f 250000 \u0930\u0941\u092a\u092f\u0947 \u0939\u0948\u0964\n"
    "\u092a\u0902\u091c\u0940\u0915\u0930\u0923 \u0938\u0902\u0916\u094d\u092f\u093e: ABC/2024/556677"
)
ENGLISH_CERT = (
    "Government of India\n"
    "Income Tax Department\n"
    "This certifies that Sunita Meena has an annual income of Rs. 250,000.\n"
    "Date of Birth: 2012-05-01\n"
    "Certificate No: ABC/2024/556677\n"
    "Date of issue: 2024-08-17"
)


def _parse(text: str, claim_type: str = "income") -> dict:
    return docai_service.parse_document(text, claim_type)


# --- issuer recognition --------------------------------------------------------


def test_english_government_issuer_is_recognised():
    out = _parse(ENGLISH_CERT)
    assert "issuer_format_unrecognized" not in out["tamper_signals"], out["tamper_signals"]


def test_hindi_government_issuer_is_recognised():
    # Regression: the Devanagari marker was mojibake, so this could never match.
    out = _parse(HINDI_CERT)
    assert "issuer_format_unrecognized" not in out["tamper_signals"], out["tamper_signals"]


def test_devanagari_source_file_is_not_corrupted():
    # The markers are written as \\u escapes precisely so an encoding round-trip
    # cannot silently break them again. Assert the escape resolves to real text.
    assert docai_service._HI_GOVT == "\u092d\u093e\u0930\u0924 \u0938\u0930\u0915\u093e\u0930"
    assert docai_service._HI_SARKAR == "\u0938\u0930\u0915\u093e\u0930"
    assert "\ufffd" not in "".join(docai_service.ISSUER_MARKERS)


def test_an_unrecognised_issuer_is_flagged_but_not_rejected():
    out = _parse("Sunita Meena was born on 2012-05-01 in Mandla district school.")
    assert "issuer_format_unrecognized" in out["tamper_signals"]
    # Still usable: a missing issuer marker is a signal, not a hard failure.
    assert 0 < out["confidence"] < 1


# --- extraction ----------------------------------------------------------------


def test_amount_is_extracted_in_both_script_documents():
    assert _parse(ENGLISH_CERT)["fields"].get("amount_inr") == "250,000"
    assert _parse(HINDI_CERT)["fields"].get("amount_inr") == "250000"


def test_certificate_number_and_dates_are_extracted():
    fields = _parse(ENGLISH_CERT)["fields"]
    assert fields.get("certificate_number") == "ABC/2024/556677"
    # Each date keeps its own identity. Reporting the issue date as a date of
    # birth is the kind of plausible-looking fabrication this avoids.
    assert fields.get("date_of_birth") == "2012-05-01"
    assert fields.get("date_of_issue") == "2024-08-17"


def test_an_issue_date_is_never_reported_as_a_date_of_birth():
    # A certificate with only an issue date must not yield a date_of_birth.
    out = _parse("Government of India\nCertificate No: X/1\nDate of issue: 2024-08-17")
    assert out["fields"].get("date_of_issue") == "2024-08-17"
    assert "date_of_birth" not in out["fields"]


def test_a_bare_number_is_not_invented_as_an_aadhaar_fragment():
    # "2024" in a registration number must not become an Aadhaar last-4.
    out = _parse(ENGLISH_CERT)
    assert "aadhaar_last4" not in out["fields"], out["fields"]


def test_aadhaar_last4_is_extracted_when_labelled():
    out = _parse("Government of India\nAadhaar XXXX XXXX 4821\nCertificate No: A/1")
    assert out["fields"].get("aadhaar_last4") == "4821"


def test_claim_type_is_echoed_and_raw_text_is_truncated():
    out = _parse("x" * 5000, claim_type="domicile")
    assert out["fields"]["claim_type"] == "domicile"
    assert len(out["fields"]["raw_text"]) == 2000


# --- degenerate input ----------------------------------------------------------


def test_empty_text_scores_zero_rather_than_guessing():
    out = _parse("")
    assert out["confidence"] == 0.0
    assert "no_text_extracted" in out["tamper_signals"]


def test_short_text_is_flagged_as_insufficient():
    out = _parse("Government of India")
    assert "insufficient_text" in out["tamper_signals"]


def test_a_fully_parsed_recognised_document_scores_highest():
    good = _parse(ENGLISH_CERT)["confidence"]
    poor = _parse("random noise with no issuer and no fields")["confidence"]
    assert good > poor


# --- upload cap ----------------------------------------------------------------


def test_oversized_upload_is_rejected_with_413():
    limit = settings.docai_max_upload_bytes
    payload = b"Government of India " + b"A" * (limit + 1024)
    r = client.post(
        "/docai/parse",
        files={"file": ("big.txt", payload, "text/plain")},
        headers=AUTH,
    )
    assert r.status_code == 413, r.text


def test_an_upload_at_the_limit_is_accepted():
    # Exactly at the boundary must still work, or the cap is off by one.
    prefix = b"Government of India certificate "
    body = prefix + b"A" * (settings.docai_max_upload_bytes - len(prefix))
    assert len(body) == settings.docai_max_upload_bytes
    r = client.post(
        "/docai/parse", files={"file": ("ok.txt", body, "text/plain")}, headers=AUTH
    )
    assert r.status_code == 200, r.text
    assert "hashed" not in r.text


def test_a_normal_upload_round_trips_through_the_router():
    r = client.post(
        "/docai/parse",
        files={"file": ("c.txt", ENGLISH_CERT.encode(), "text/plain")},
        headers=AUTH,
    )
    assert r.status_code == 200
    assert r.json()["fields"]["amount_inr"] == "250,000"


# --- regex backtracking bound --------------------------------------------------
#
# The amount pattern used to end in `([\d,]+(?:\.\d{1,2})?)\s*(?:<hindi>|rupees?)`:
# a greedy unbounded run followed by a mandatory literal. On a long digit run the
# engine matches the run, misses the literal, backtracks one character, retries --
# and repeats all of that from every start position inside the run. The upload cap
# allows 5 MB, so this was a request-level denial of service.
#
# Measured on this interpreter (CPython 3.10, sre) with the old pattern: 2,000
# digits 0.2s, 10,000 digits 12.9s, 20,000 digits 53.8s. That curve is quadratic,
# and extrapolated it puts a 2,000,000-character run at roughly 6 * 10^5 seconds.
# With the bound below the same 2,000,000-character run takes ~0.5s.


def test_a_multi_megabyte_digit_run_cannot_stall_the_extractor():
    digits = "9" * 2_000_000
    started = time.perf_counter()
    out = docai_service.parse_document(digits, "income")
    elapsed = time.perf_counter() - started
    # Linear, not quadratic: the pre-fix pattern needed days for this input.
    # 5s rather than 2s because the bound is about the absence of quadratic
    # blow-up, not absolute speed, and CI machines run this under load.
    assert elapsed < 5.0, f"extraction of a 2MB digit run took {elapsed:.2f}s"
    # And it still has to be a *correct* answer, not merely a fast one.
    assert "amount_inr" not in out["fields"]
    assert out["confidence"] <= 0.60
    # Cost is bounded by the analysis cap, not by the upload size.
    assert out["fields"]["analysis_truncated"] is True


def test_analysis_is_capped_but_short_documents_are_untouched():
    # No false truncation signal for anything of realistic length.
    out = docai_service.parse_document(ENGLISH_CERT, "income")
    assert "analysis_truncated" not in out["fields"]
    assert out["fields"]["amount_inr"] == "250,000"


def test_a_document_longer_than_the_cap_still_extracts_its_header():
    # The issuer and reference number live at the top, so truncating the tail
    # must not cost us the fields that matter.
    padded = ENGLISH_CERT + ("\nfiller line\n" * 60_000)
    out = docai_service.parse_document(padded, "income")
    assert out["fields"]["certificate_number"] == "ABC/2024/556677"
    assert out["fields"]["analysis_truncated"] is True


def test_a_huge_run_of_separators_cannot_stall_the_extractor():
    # Same shape on the date patterns: a long run of separators with no value
    # behind it, after a label that does match.
    text = "Date of Birth" + (":" * 500_000)
    started = time.perf_counter()
    out = docai_service.parse_document(text, "income")
    assert time.perf_counter() - started < 2.0
    assert "date_of_birth" not in out["fields"]


def test_a_realistic_amount_is_still_extracted_from_a_long_document():
    # The bound must not cost recall: the amount has to survive a megabyte of
    # surrounding noise, in both the leading and the trailing form.
    for text, expected in (
        ("Rs. 250,000 " + "x" * 1_000_000, "250,000"),
        ("9" * 100_000 + " 250,000 \u0930\u0941\u092a\u092f\u0947", "250,000"),
    ):
        assert docai_service.parse_document(text, "income")["fields"][
            "amount_inr"
        ] == expected


def test_an_absurdly_long_digit_run_is_not_reported_as_an_amount():
    # Truncating a 22-digit run to the first 15 would report a wrong number, and
    # this module's rule is that a wrong number is worse than a missing one. The
    # whole run simply fails to match.
    out = docai_service.parse_document("9" * 22 + " \u0930\u0941\u092a\u092f\u0947", "income")
    assert "amount_inr" not in out["fields"]
    # 15 digits is still accepted, so the bound is a bound and not a ban.
    at_bound = docai_service.parse_document("9" * 15 + " \u0930\u0941\u092a\u092f\u0947", "income")
    assert at_bound["fields"]["amount_inr"] == "9" * 15


def test_the_certificate_number_value_run_is_bounded():
    # Unbounded `{4,}` cannot backtrack (nothing follows it) but will copy
    # megabytes of the upload into the response body.
    out = docai_service.parse_document("Certificate No: " + "A" * 1_000_000, "income")
    assert len(out["fields"]["certificate_number"]) == 64
