"""Ask Adi help lane: explain the app, the schemes, and the process.

SAFETY CONTRACT (mirrors the JAGO+ tool router rule): this lane answers ONLY
general how/what questions from static content. It NEVER sees a USID, NEVER
touches Core, and NEVER states personal status, amounts, or verdicts. A
question about the caller's own file is deflected to the status lane with a
suggested tool name — the client then invokes the real tool and template-fills
the answer. Free text here can therefore never leak or invent a status.

Deterministic by design: keyword scoring + templates, no LLM call, so the
endpoint works on the judge-demo network (WIFI OFF) and every response is
unit-testable.
"""

from __future__ import annotations

import json
import logging
import re
import time
from pathlib import Path
from typing import Any

from app.services import rag_service

logger = logging.getLogger(__name__)

_FAQ_PATH = Path(__file__).resolve().parent.parent / "data" / "help_faq.json"


def _load_bundle() -> dict[str, Any]:
    with open(_FAQ_PATH, encoding="utf-8") as f:
        return json.load(f)


_BUNDLE: dict[str, Any] = _load_bundle()
FAQS: list[dict[str, Any]] = _BUNDLE["faqs"]
SYSTEM: dict[str, str] = _BUNDLE["system"]

# Personal markers: the question is about the CALLER's own file, not the app
# in general. These route to the status lane (deflection + suggested tool).
# Kept narrow on purpose: bare "am I eligible" has no marker and is answered
# from general scheme criteria with a check-Home disclaimer.
#
# Split in two because substring matching is wrong for the short ones. The
# entries below used to include a bare "my", and "my" occurs inside "academy",
# "economy", "ceremony" and "army" — so "which academy has the best faculty?"
# was classified as a question about the caller's own records and deflected to
# a tool instead of being answered. Single-word markers are therefore matched
# on word boundaries (_has_marker); multi-word phrases keep substring
# matching, which is what they were written for.
STATUS_WORD_MARKERS = {
    "my", "mera", "meri", "mere", "mujhe", "mujhko", "hamari", "hamaari",
    "usid", "kab", "kabhi",
}

STATUS_PHRASE_MARKERS = {
    "app-", "application id", "applicationid", "where is my",
    "why is my", "paise kab", "payment kab", "scholarship kab",
}

# Which status tool the client should invoke after a deflection.
STATUS_TOOL_HINTS: list[tuple[str, tuple[str, ...]]] = [
    ("why_is_payment_pending", ("payment", "paisa", "paise", "bank", "money", "dbt", "pfms", "fail")),
    ("get_my_applications", ("application", "file", "stage", "waiting", "status", "stithi")),
    ("check_eligibility", ("eligib", "yogya", "milegi", "qualify")),
    ("list_required_documents", ("document", "dastavej", "certificate", "digilocker")),
]

# Precise-criterion questions deserve cited clause answers, not FAQ summaries.
# Checked before the FAQ match: "income ceiling for post-matric" must ground
# to [POST-1.3], not to the schemes overview.
CRITERION_MARKERS = {
    "income", "ceiling", "amount", "paisa", "allowance", "grant",
    "fellowship", "age", "class", "admiss", "rank", "net", "jrf",
    "backlog", "fees", "reimburs", "stack", "simultaneous", "together",
}

GREETING_MARKERS = {
    "namaste", "namaskar", "hello", "hey", "hi", "hey!", "jai johar",
    "salaam", "good morning", "good evening", "pranam",
}

HINDI_MARKERS = {
    # "me" deliberately excluded: English "tell me ..." is not Hindi.
    "hindi", "mein", "kya", "hai", "kaise", "kahan", "kaun",
    "mera", "meri", "mujhe", "batao", "bataiye", "samjhao", "dikhao",
}

#: Matched on word boundaries for the same reason STATUS_WORD_MARKERS are:
#: "hai" is a substring of "Hawaii" and "chain", and "kaun" of nothing useful,
#: so plain `in` testing answered an English question about a university in
#: Hawaii in Hindi.
_HINDI_MARKER_RE = re.compile(
    r"\b(?:" + "|".join(re.escape(m) for m in sorted(HINDI_MARKERS, key=len, reverse=True)) + r")\b"
)

LANG_SWITCH_MARKERS = {"talk in hindi", "hindi me", "hindi mein", "speak hindi"}

DEVANAGARI_RE = re.compile(r"[\u0900-\u097F]")

# All user-facing help strings live in help_faq.json ("system" block) so the
# backend and the offline frontend mirror can never drift. Read them here.
GREETING_EN = SYSTEM["greeting_en"]
GREETING_HI = SYSTEM["greeting_hi"]
DEFLECT_EN = SYSTEM["deflect_en"] + " I can explain any of these in general — try one of these:"
DEFLECT_HI = SYSTEM["deflect_hi"] + " मैं इन्हें सामान्य रूप से समझा सकता हूँ — इनमें से पूछें:"
FALLBACK_EN = SYSTEM["fallback_en"]
FALLBACK_HI = SYSTEM["fallback_hi"]
LANG_SWITCH_HI = SYSTEM["lang_switch_hi"]


def detect_lang(question: str, requested: str) -> str:
    """Requested lang wins, unless the text itself is Devanagari."""
    if DEVANAGARI_RE.search(question):
        return "hi"
    req = (requested or "").strip().lower()
    if req.startswith("hi"):
        return "hi"
    if req.startswith("en"):
        # Roman-Hindi questions still read better in Hindi.
        if _HINDI_MARKER_RE.search(question.lower()):
            return "hi"
        return "en"
    return "hi"


def _norm(text: str) -> str:
    return re.sub(r"\s+", " ", text.lower()).strip()


def is_greeting(text: str) -> bool:
    t = _norm(text)
    return any(t == g or t.startswith(g + " ") or t.startswith(g + "!") for g in GREETING_MARKERS)


def wants_hindi(text: str) -> bool:
    return _norm(text) in LANG_SWITCH_MARKERS


def is_status_question(text: str) -> bool:
    """True when the question is about the caller's own file/records."""
    t = _norm(text)
    if re.search(r"app-\d+", t):
        return True
    if any(m in t for m in STATUS_PHRASE_MARKERS):
        return True
    return _has_word_marker(t, STATUS_WORD_MARKERS)


def _has_word_marker(text: str, markers: set[str]) -> bool:
    """True when any marker appears in ``text`` as a whole word."""
    pattern = r"\b(?:" + "|".join(re.escape(m) for m in markers) + r")\b"
    return re.search(pattern, text) is not None


def suggested_tool(text: str) -> str:
    t = _norm(text)
    for tool, markers in STATUS_TOOL_HINTS:
        if any(m in t for m in markers):
            return tool
    return "next_action"


def _faq_score(faq: dict[str, Any], words: set[str], text: str) -> int:
    matched = [k for k in faq["keywords_en"] + faq["keywords_hi"] if k in text or k in words]
    score = len(set(matched))
    if any(len(k) >= 6 for k in set(matched)):
        score += 2
    return score


def match_faq(text: str) -> dict[str, Any] | None:
    """Best FAQ entry, or None when nothing matches confidently."""
    words = set(re.findall(r"[\w\u0900-\u097F]+", text.lower()))
    scored = [(_faq_score(f, words, text.lower()), f) for f in FAQS]
    scored.sort(key=lambda x: x[0], reverse=True)
    best_score, best = scored[0]
    return best if best_score >= 2 else None


def _clause_fallback(question: str, lang: str) -> dict[str, Any] | None:
    """General scheme-criteria questions answered from official clauses.

    Returns None when the question has no grounding in the clause corpus.
    Personal framing is stripped by answering criteria only, plus a
    check-Home disclaimer — no personal data is ever stated.
    """
    clauses = rag_service.retrieve_clauses(question, None, top_k=2)
    if not clauses:
        return None
    q_words = set(re.findall(r"[\w\u0900-\u097F]+", question.lower()))
    top = clauses[0]
    hay = f"{top['scheme_name']} {top['clause']} {top['content']}".lower()
    if len(q_words & set(re.findall(r"\w+", hay))) < 2:
        return None
    lines = [
        f"{c['scheme_name']} ({c['clause']} [{c['id']}]): {c['content']}"
        for c in clauses[:2]
    ]
    if lang == "en":
        disclaimer = "Your personal result depends on your documents — check Home for your schemes."
    else:
        disclaimer = "आपका व्यक्तिगत परिणाम दस्तावेज़ों पर निर्भर है — अपनी योजनाएँ Home में देखें।"
    return {
        "answer": " ".join(lines) + " " + disclaimer,
        "citations": [
            {"kind": "clause", "id": c["id"], "title": f"{c['scheme_name']} — {c['clause']}"}
            for c in clauses[:2]
        ],
        "source": "clauses",
    }


def answer(question: str, lang: str = "hi") -> dict[str, Any]:
    """Route a help question and build a deterministic response.

    Never raises for content reasons: every path returns an answer dict.
    """
    start = time.perf_counter()
    use_lang = detect_lang(question, lang)
    text = _norm(question)

    def done(payload: dict[str, Any]) -> dict[str, Any]:
        payload["lang"] = use_lang
        payload["latency_ms"] = round((time.perf_counter() - start) * 1000, 2)
        return payload

    if wants_hindi(text):
        return done({
            "lane": "help",
            "answer": LANG_SWITCH_HI,
            "citations": [],
            "source": "lang-switch",
            "suggested_tool": None,
            "suggestions": ["How do I use this app?", "Which scholarships can I get?", "Where is my file?"],
        })

    if is_greeting(text):
        return done({
            "lane": "help",
            "answer": GREETING_HI if use_lang == "hi" else GREETING_EN,
            "citations": [],
            "source": "greeting",
            "suggested_tool": None,
            "suggestions": ["How do I use this app?", "Which scholarships can I get?", "Why did my payment fail?"],
        })

    if is_status_question(text):
        tool = suggested_tool(text)
        return done({
            "lane": "status",
            "answer": DEFLECT_HI if use_lang == "hi" else DEFLECT_EN,
            "citations": [],
            "source": "deflection",
            "suggested_tool": tool,
            "suggestions": ["Where is my file?", "Why did my payment fail?", "What should I do next?"],
        })

    def faq_answer() -> dict[str, Any] | None:
        faq = match_faq(text)
        if faq is None:
            return None
        key = "answer_hi" if use_lang == "hi" else "answer_en"
        return done({
            "lane": "help",
            "answer": faq[key],
            "citations": [{"kind": "faq", "id": faq["id"], "title": faq["id"]}],
            "source": "faq",
            "suggested_tool": None,
            "suggestions": faq.get("suggestions", [])[:3],
        })

    def clause_answer() -> dict[str, Any] | None:
        grounded = _clause_fallback(question, use_lang)
        if grounded is None:
            return None
        grounded["lane"] = "help"
        grounded["suggested_tool"] = None
        grounded["suggestions"] = ["Which scholarships can I get?", "Where is my file?"]
        return done(grounded)

    # Criteria-first for precise questions, FAQ-first for app/process ones.
    if any(m in text for m in CRITERION_MARKERS):
        for attempt in (clause_answer, faq_answer):
            hit = attempt()
            if hit is not None:
                return hit
    else:
        for attempt in (faq_answer, clause_answer):
            hit = attempt()
            if hit is not None:
                return hit

    return done({
        "lane": "help",
        "answer": FALLBACK_HI if use_lang == "hi" else FALLBACK_EN,
        "citations": [],
        "source": "fallback",
        "suggested_tool": None,
        "suggestions": ["How do I use this app?", "Which scholarships can I get?", "Why did my payment fail?"],
    })
