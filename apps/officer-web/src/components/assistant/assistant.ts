/**
 * Ask Adi — offline-first assistant router (officer-web).
 *
 * Same contract as the mobile twin (apps/mobile/src/lib/assistant.ts):
 * answers from the shared assistant_faq.json mirror, routing mirrored from
 * services/ai help_service.py. The criteria/clause lane lives server-side;
 * askAdi() prefers the AI service when reachable, else the local mirror.
 *
 * SAFETY: help lane never states personal status. Personal questions get a
 * deflection; factual answers stay template-filled from tool output.
 */
import rawBundle from "@adi-vritti/ui-tokens/assistant_faq.json";

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

export type AdiLang = "hi" | "en";
export type AdiLane = "help" | "status";

export interface AdiAnswer {
  lane: AdiLane;
  text: string;
  suggestions: string[];
  online: boolean;
}

const STATUS_MARKERS = [
  "my", "mera", "meri", "mere", "mujhe", "mujhko", "hamari",
  "usid", "app-", "application id", "kab", "where is my",
  "why is my", "paise kab", "payment kab", "scholarship kab",
];

const GREETINGS = [
  "namaste", "namaskar", "hello", "hey", "hi", "jai johar",
  "salaam", "good morning", "good evening", "pranam",
];

const LANG_SWITCH = ["talk in hindi", "hindi me", "hindi mein", "speak hindi"];

const DEVANAGARI = /[\u0900-\u097F]/;

export function detectLang(text: string, uiLang: AdiLang): AdiLang {
  if (DEVANAGARI.test(text)) return "hi";
  return uiLang;
}

function norm(text: string): string {
  return text.toLowerCase().replace(/\s+/g, " ").trim();
}

function isGreeting(t: string): boolean {
  return GREETINGS.some((g) => t === g || t.startsWith(`${g} `) || t.startsWith(`${g}!`));
}

function isStatus(t: string): boolean {
  if (/app-\d+/i.test(t)) return true;
  return STATUS_MARKERS.some((m) => t.includes(m));
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
export function answerLocal(raw: string, uiLang: AdiLang): AdiAnswer {
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
 * Same-origin BFF proxy (src/app/api/jago/help/route.ts) holds the service
 * token server-side, so the browser bundle never sees it. Unreachable BFF
 * (non-OK status) falls through to the offline mirror below.
 */
const HELP_URL = "/api/jago/help";

/** Online-first, offline-proof: BFF when reachable, local mirror otherwise. */
export async function askAdi(raw: string, uiLang: AdiLang): Promise<AdiAnswer> {
  const text = raw.trim();
  if (!text) return answerLocal("namaste", uiLang);
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);
    const res = await fetch(HELP_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: text.slice(0, 500), lang: uiLang }),
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
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
    }
  } catch {
    // Offline (judge demo runs WIFI OFF) — fall through to the mirror.
  }
  return answerLocal(text, uiLang);
}
