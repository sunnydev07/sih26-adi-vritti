/**
 * Ask Adi — offline-first assistant router (mobile).
 *
 * Answers come from the shared @adi-vritti/ui-tokens/assistant_faq.json
 * mirror (same ids as the backend canonical file). Routing mirrors
 * services/ai help_service.py but stays intentionally thinner: greeting,
 * status-deflection, FAQ match, fallback. The criteria/clause lane lives
 * server-side; when the AI service is reachable askAdi() prefers it.
 *
 * SAFETY: this lane never states personal status. Personal questions get a
 * deflection pointing at Home/Money; factual status stays template-filled
 * from tool output (see lib/jago.ts).
 */
import rawBundle from "@adi-vritti/ui-tokens/assistant_faq.json";
import type { SupportedLang } from "@/types";

interface FaqEntry {
  id: string;
  keywords_en: string[];
  keywords_hi: string[];
  answer_en: string;
  answer_hi: string;
  suggestions: string[];
}

interface FaqBundle {
  system: {
    greeting_en: string;
    greeting_hi: string;
    deflect_en: string;
    deflect_hi: string;
    fallback_en: string;
    fallback_hi: string;
    lang_switch_hi: string;
  };
  faqs: FaqEntry[];
}

const bundle = rawBundle as unknown as FaqBundle;

export type AdiLane = "help" | "status";

/** Machine-readable side-channel so the screen can act, not just render. */
export type AdiNotice =
  | "session-expired"
  | "consent-required"
  | "mirror-offline"
  | "lang-coming-soon";

export interface AdiAnswer {
  lane: AdiLane;
  text: string;
  suggestions: string[];
  /** True when answered by the AI service; false for the offline mirror. */
  online: boolean;
  notice?: AdiNotice;
}

// Personal markers: the question is about the CALLER's own file, not the app
// in general. Split in two because substring matching is wrong for the short
// ones: a bare "my" occurs inside "academy", "economy" and "army", so single
// words match on boundaries while multi-word phrases keep substring matching.
// "usid" is deliberately NOT a word marker: "what is usid" is a general
// question the USID faq answers — possessive framing ("my/mera usid") and
// APP-ids catch the personal ones.
const STATUS_WORD_MARKERS = [
  "my", "mera", "meri", "mere", "mujhe", "mujhko", "hamari", "hamaari",
  "kab", "kabhi",
];

const STATUS_PHRASE_MARKERS = [
  "app-", "application id", "applicationid", "where is my",
  "why is my", "paise kab", "payment kab", "scholarship kab",
];

const GREETINGS = [
  "namaste", "namaskar", "hello", "hey", "hi", "jai johar",
  "salaam", "good morning", "good evening", "pranam",
];

const LANG_SWITCH = ["talk in hindi", "hindi me", "hindi mein", "speak hindi"];

const DEVANAGARI = /[\u0900-\u097F]/;

export function detectLang(text: string, uiLang: SupportedLang): SupportedLang {
  if (DEVANAGARI.test(text)) return "hi";
  return uiLang;
}

function norm(text: string): string {
  return text.toLowerCase().replace(/\s+/g, " ").trim();
}

function isGreeting(t: string): boolean {
  return GREETINGS.some((g) => t === g || t.startsWith(g + " ") || t.startsWith(g + "!"));
}

function isStatus(t: string): boolean {
  if (/app-\d+/i.test(t)) return true;
  if (STATUS_PHRASE_MARKERS.some((m) => t.includes(m))) return true;
  const pattern = new RegExp(`\\b(?:${STATUS_WORD_MARKERS.join("|")})\\b`);
  return pattern.test(t);
}

function scoreFaq(f: FaqEntry, t: string, words: Set<string>): number {
  const matched = new Set(
    [...f.keywords_en, ...f.keywords_hi].filter((k) => t.includes(k) || words.has(k))
  );
  let score = matched.size;
  for (const k of matched) {
    if (k.length >= 6) {
      score += 2;
      break;
    }
  }
  return score;
}

function matchFaq(t: string): FaqEntry | null {
  const words = new Set(t.match(/[\w\u0900-\u097F]+/g) ?? []);
  let best: FaqEntry | null = null;
  let bestScore = 0;
  for (const f of bundle.faqs) {
    const s = scoreFaq(f, t, words);
    if (s > bestScore) {
      bestScore = s;
      best = f;
    }
  }
  return bestScore >= 2 ? best : null;
}

/** Pure-offline answer. Never throws, never needs network. */
export function answerLocal(raw: string, uiLang: SupportedLang): AdiAnswer {
  const lang = detectLang(raw, uiLang);
  const t = norm(raw);
  const hi = lang === "hi";
  const pick = (en: string, h: string): string => (hi ? h : en);

  if (LANG_SWITCH.includes(t)) {
    return {
      lane: "help",
      text: bundle.system.lang_switch_hi,
      suggestions: ["How do I use this app?", "Which scholarships can I get?"],
      online: false,
    };
  }

  if (isGreeting(t)) {
    return {
      lane: "help",
      text: pick(bundle.system.greeting_en, bundle.system.greeting_hi),
      suggestions: ["How do I use this app?", "Which scholarships can I get?", "Why did my payment fail?"],
      online: false,
    };
  }

  if (isStatus(t)) {
    return {
      lane: "status",
      text: pick(bundle.system.deflect_en, bundle.system.deflect_hi),
      suggestions: ["Where is my file?", "Why did my payment fail?", "What should I do next?"],
      online: false,
    };
  }

  const faq = matchFaq(t);
  if (faq) {
    return {
      lane: "help",
      text: hi ? faq.answer_hi : faq.answer_en,
      suggestions: faq.suggestions.slice(0, 3),
      online: false,
    };
  }

  return {
    lane: "help",
    text: pick(bundle.system.fallback_en, bundle.system.fallback_hi),
    suggestions: ["How do I use this app?", "Which scholarships can I get?", "Why did my payment fail?"],
    online: false,
  };
}

/**
 * Optional BFF base URL, configured at build time via
 * EXPO_PUBLIC_ASSISTANT_BFF_URL. The BFF (the officer-web /api/* routes, or
 * any deployment of them) holds the AI service token server-side; this app
 * never ships a token. Unset means the offline mirror below is the only
 * path — which is also the judge-demo configuration (WIFI OFF).
 */
const BFF_BASE = (process.env.EXPO_PUBLIC_ASSISTANT_BFF_URL ?? "")
  .trim()
  .replace(/\/+$/, "");

/** Online-first, offline-proof: BFF when configured and reachable, local mirror otherwise. */
export async function askAdi(raw: string, uiLang: SupportedLang): Promise<AdiAnswer> {
  const text = raw.trim();
  if (!text) {
    return answerLocal("namaste", uiLang);
  }
  // Santali/Gondi have no answer bundles anywhere in the stack (the BFF
  // answers 400 for them): answer in English and say so, instead of silently
  // serving English behind a Gondi pill.
  const comingSoon = uiLang === "sat" || uiLang === "gon";
  if (BFF_BASE.length > 0 && !comingSoon) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2500);
      let res: Response;
      try {
        res = await fetch(`${BFF_BASE}/api/jago/help`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ question: text.slice(0, 500), lang: uiLang }),
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timeoutId);
      }
      if (res.ok) {
        const data = await res.json();
        if (typeof data?.answer === "string") {
          return {
            lane: data.lane === "status" ? "status" : "help",
            text: data.answer,
            suggestions: Array.isArray(data.suggestions) ? data.suggestions.slice(0, 3) : [],
            online: true,
          };
        }
      } else {
        // A non-ok BFF answer is information, not "offline": 401 means the
        // session died, 403 CONSENT_REQUIRED means the file is blocked, and
        // anything else falls back to the mirror WITH the offline marker so
        // the screen can say so instead of silently serving general text.
        let errorCode = "";
        try {
          const err = await res.json();
          if (typeof err?.error_code === "string") errorCode = err.error_code;
        } catch {
          errorCode = "";
        }
        if (res.status === 401) {
          return {
            lane: "status",
            text: "Your session expired. Sign in again to continue.",
            suggestions: [],
            online: true,
            notice: "session-expired",
          };
        }
        if (res.status === 403) {
          return {
            lane: "status",
            text:
              errorCode === "CONSENT_REQUIRED"
                ? "Your file needs a sharing consent before anything can be looked up. Ask the district office to record it."
                : "This device is not allowed to open that file. Sign in with the registered number.",
            suggestions: [],
            online: true,
            notice: "consent-required",
          };
        }
        return { ...answerLocal(text, uiLang), notice: "mirror-offline" };
      }
    } catch {
      // Offline (judge demo runs WIFI OFF) — fall through to the mirror.
    }
  }
  const local = answerLocal(text, uiLang);
  return comingSoon ? { ...local, notice: "lang-coming-soon" } : local;
}
