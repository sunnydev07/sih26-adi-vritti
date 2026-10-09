"use client";

import { MessageCircle, Send, X } from "lucide-react";
import { useRouter } from "next/navigation";
import * as React from "react";
import { answerLocal, askAdi, type AdiLang } from "@/components/assistant/assistant";
import { assistantSuggestions } from "@/lib/plain";
import { cn } from "@/lib/utils";

interface Msg {
  id: number;
  role: "user" | "adi";
  text: string;
}

/** Floating Ask Adi panel. Bottom sheet on phones, docked card on desktop. */
export function AskAdi() {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const [lang, setLang] = React.useState<AdiLang>("hi");
  const [draft, setDraft] = React.useState("");
  const [sending, setSending] = React.useState(false);
  const [chips, setChips] = React.useState<string[]>(assistantSuggestions);
  const [messages, setMessages] = React.useState<Msg[]>([]);
  const idRef = React.useRef(0);
  const bodyRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (open && messages.length === 0) {
      const g = answerLocal("namaste", lang);
      idRef.current += 1;
      setMessages([{ id: idRef.current, role: "adi", text: g.text }]);
      setChips(g.suggestions);
    }
    // Greet once per open session; lang switches apply to new answers.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open ]);

  React.useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  async function sendText(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    idRef.current += 1;
    const userId = idRef.current;
    setMessages((m) => [...m, { id: userId, role: "user", text: trimmed }]);
    setDraft("");
    setSending(true);
    const ans = await askAdi(trimmed, lang);
    setSending(false);
    if (ans.sessionExpired) {
      // The BFF said 401: leave for login instead of chatting on a dead
      // session, where every further question would fail the same way.
      setOpen(false);
      router.replace("/login");
      return;
    }
    idRef.current += 1;
    setMessages((m) => [...m, { id: idRef.current, role: "adi", text: ans.text }]);
    setChips(ans.suggestions.length > 0 ? ans.suggestions : assistantSuggestions);
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    void sendText(draft);
  }

  return (
    <>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={open ? "Close Ask Adi help" : "Open Ask Adi help"}
        aria-expanded={open}
        className="min-touch fixed bottom-5 right-5 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-[var(--primary)] text-white shadow-lg transition-transform hover:scale-105"
      >
        {open ? <X size={22} aria-hidden /> : <MessageCircle size={22} aria-hidden />}
      </button>

      {open ? (
        <section
          aria-label="Ask Adi help chat"
          className="fixed z-50 flex flex-col overflow-hidden border border-[var(--border)] bg-[var(--card)] shadow-2xl inset-x-3 bottom-24 max-h-[65vh] rounded-3xl sm:inset-x-auto sm:right-5 sm:w-[380px]"
        >
          <header className="flex items-center gap-2 border-b border-[var(--border)] px-4 py-3">
            <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--primary)] text-sm font-bold text-white">
              A
            </span>
            <div className="min-w-0 flex-1">
              <h2 className="text-sm font-bold">Ask Adi</h2>
              <p className="truncate text-xs text-[var(--muted-foreground)]">
                App help — files stay template-filled, never guessed
              </p>
            </div>
            <div className="flex gap-1" role="group" aria-label="Answer language">
              {(["hi", "en"] as const).map((l) => (
                <button
                  key={l}
                  onClick={() => setLang(l)}
                  aria-pressed={lang === l}
                  className={cn(
                    "min-touch rounded-full px-3 text-xs font-semibold",
                    lang === l ? "bg-[var(--primary)] text-white" : "border border-[var(--border)]"
                  )}
                >
                  {l === "hi" ? "हिं" : "EN"}
                </button>
              ))}
            </div>
          </header>

          <div ref={bodyRef} aria-live="polite" className="flex-1 space-y-2 overflow-y-auto px-3 py-3">
            {messages.map((m) => (
              <p
                key={m.id}
                className={cn(
                  "max-w-[85%] rounded-2xl px-3 py-2 text-sm",
                  m.role === "user"
                    ? "ml-auto bg-[var(--primary)] text-white"
                    : "border border-[var(--border)] bg-[var(--muted)]"
                )}
              >
                {m.text}
              </p>
            ))}
            {sending ? <p className="text-xs text-[var(--muted-foreground)]">Adi is typing…</p> : null}
          </div>

          <div className="flex gap-1.5 overflow-x-auto px-3 pb-1">
            {chips.map((c) => (
              <button
                key={c}
                onClick={() => void sendText(c)}
                className="min-touch shrink-0 rounded-full border border-[#C7D2FE] bg-[#EEF2FF] px-3 text-xs font-medium text-[#4338CA] dark:bg-indigo-500/10"
              >
                {c}
              </button>
            ))}
          </div>

          <form onSubmit={onSubmit} className="flex gap-2 border-t border-[var(--border)] p-3">
            <label htmlFor="ask-adi-input" className="sr-only">Ask Adi a question</label>
            <input
              id="ask-adi-input"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Ask Adi…"
              autoComplete="off"
              className="min-touch min-w-0 flex-1 rounded-full border border-[var(--border)] bg-transparent px-4 text-sm"
            />
            <button
              type="submit"
              aria-label="Send question"
              className="min-touch flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[var(--primary)] text-white"
            >
              <Send size={17} aria-hidden />
            </button>
          </form>
        </section>
      ) : null}
    </>
  );
}
