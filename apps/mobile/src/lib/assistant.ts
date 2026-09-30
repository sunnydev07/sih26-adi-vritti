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

export interface AdiAnswer {
  lane: AdiLane;
  text: string;
  suggestions: string[];
  /** True when answered by the AI service; false for the offline mirror. */
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
  if (BFF_BASE.length > 0) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2500);
      const res = await fetch(`${BFF_BASE}/api/jago/help`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
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
  }
  return answerLocal(text, uiLang);
}
