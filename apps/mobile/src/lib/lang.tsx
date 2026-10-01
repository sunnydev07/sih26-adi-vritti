/**
 * App-language state for screen chrome (English/Hindi).
 *
 * In-memory only: no async-storage dependency is installed, so the choice
 * resets to English on reload. That matches the current demo (every
 * screenshot is English) while letting a Hindi-first student switch once
 * per session. Persist when a storage dep lands.
 *
 * Chat keeps its own 4-language picker (it also serves Santali/Gondi and
 * per-message Devanagari detection); this context covers chrome strings
 * from lib/strings.ts only.
 */
import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { STRINGS, type Strings, type UiLang } from "@/lib/strings";

interface LangState {
  lang: UiLang;
  t: Strings;
  toggle: () => void;
}

const LangContext = createContext<LangState>({
  lang: "en",
  t: STRINGS.en,
  toggle: () => {},
});

export function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<UiLang>("en");
  const toggle = useCallback(() => {
    setLang((l) => (l === "en" ? "hi" : "en"));
  }, []);
  const value = useMemo<LangState>(
    () => ({ lang, t: STRINGS[lang], toggle }),
    [lang, toggle]
  );
  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang(): LangState {
  return useContext(LangContext);
}
