import { useRef, useState } from "react";
import { Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { answerLocal, askAdi } from "@/lib/assistant";
import { assistantSuggestions, colors } from "@/lib/theme";
import type { ChatMessage, SupportedLang } from "@/types";

const LANGS: { id: SupportedLang; label: string }[] = [
  { id: "hi", label: "Hindi" },
  { id: "en", label: "English" },
  { id: "sat", label: "Santali" },
  { id: "gon", label: "Gondi" },
];

/** Status-lane answers point at real screens — never free-generated status. */
function statusCard(): ChatMessage["card"] {
  return {
    title: "See your file in the app",
    rows: [
      { label: "Stage + waiting days", value: "Home tab" },
      { label: "Payment reason + fix", value: "Home tab → Money" },
    ],
    actions: [],
  };
}

export default function ChatScreen() {
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    const g = answerLocal("namaste", "hi");
    return [{ id: "m0", role: "jago", text: g.text, createdAt: new Date().toISOString() }];
  });
  const [draft, setDraft] = useState("");
  const [lang, setLang] = useState<SupportedLang>("hi");
  const [chips, setChips] = useState<string[]>(assistantSuggestions);
  const [sending, setSending] = useState(false);
  const scrollRef = useRef<ScrollView>(null);

  async function sendText(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    const user: ChatMessage = {
      id: `u-${Date.now()}`,
      role: "user",
      text: trimmed,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, user]);
    setDraft("");
    setSending(true);
    // Online-first (AI /jago/help), offline-proof (local mirror for WIFI-OFF demo).
    const ans = await askAdi(trimmed, lang);
    const reply: ChatMessage = {
      id: `j-${Date.now()}`,
      role: "jago",
      text: ans.text,
      card: ans.lane === "status" ? statusCard() : undefined,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, reply]);
    setChips(ans.suggestions.length > 0 ? ans.suggestions : assistantSuggestions);
    setSending(false);
  }

  function send() {
    void sendText(draft);
  }

  return (
    <View style={{ flex: 1, backgroundColor: "#F8FAFC", paddingTop: 48 }}>
      <Text style={{ paddingHorizontal: 16, fontSize: 20, fontWeight: "800", color: colors.primary }}>
        Ask Adi
      </Text>
      <Text style={{ paddingHorizontal: 16, fontSize: 12, color: "#64748B" }}>
        Help with the app — files and payments stay template-filled, never guessed
      </Text>
      <View style={{ flexDirection: "row", gap: 6, paddingHorizontal: 16, marginTop: 8 }}>
        {LANGS.map((l) => (
          <Pressable
            key={l.id}
            accessibilityRole="button"
            accessibilityLabel={`Chat in ${l.label}`}
            onPress={() => setLang(l.id)}
            style={{
              paddingHorizontal: 12,
              paddingVertical: 6,
              minHeight: 32,
              borderRadius: 999,
              backgroundColor: lang === l.id ? colors.primary : "#E2E8F0",
            }}
          >
            <Text style={{ color: lang === l.id ? "#fff" : colors.primary, fontSize: 12, fontWeight: "700" }}>
              {l.label}
            </Text>
          </Pressable>
        ))}
      </View>
      <ScrollView
        ref={scrollRef}
        style={{ flex: 1, padding: 16 }}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
      >
        {messages.map((m) => (
          <View
            key={m.id}
            style={{
              alignSelf: m.role === "user" ? "flex-end" : "flex-start",
              backgroundColor: m.role === "user" ? colors.primary : "#fff",
              borderRadius: 14,
              padding: 10,
              marginBottom: 8,
              maxWidth: "85%",
              borderWidth: m.role === "jago" ? 1 : 0,
              borderColor: "#E2E8F0",
            }}
          >
            <Text style={{ color: m.role === "user" ? "#fff" : "#0F172A" }}>{m.text}</Text>
            {m.card ? (
              <View style={{ marginTop: 8, borderTopWidth: 1, borderTopColor: "#E2E8F0", paddingTop: 8 }}>
                <Text style={{ fontWeight: "800", fontSize: 12 }}>{m.card.title}</Text>
                {m.card.rows.map((r) => (
                  <Text key={r.label} style={{ fontSize: 12 }}>{r.label}: {r.value}</Text>
                ))}
              </View>
            ) : null}
          </View>
        ))}
        {sending ? <Text style={{ fontSize: 12, color: "#64748B" }}>Adi is typing…</Text> : null}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ maxHeight: 44, paddingHorizontal: 12 }}>
        <View style={{ flexDirection: "row", gap: 6, paddingVertical: 4 }}>
          {chips.map((c) => (
            <Pressable
              key={c}
              onPress={() => void sendText(c)}
              style={{ paddingHorizontal: 12, paddingVertical: 7, borderRadius: 999, backgroundColor: "#EEF2FF", borderWidth: 1, borderColor: "#C7D2FE" }}
            >
              <Text style={{ fontSize: 12, color: colors.primary, fontWeight: "600" }}>{c}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>
      <View style={{ flexDirection: "row", gap: 8, padding: 12 }}>
        <TextInput
          value={draft}
          onChangeText={setDraft}
          onSubmitEditing={send}
          placeholder="Ask Adi…"
          returnKeyType="send"
          style={{ flex: 1, backgroundColor: "#fff", borderRadius: 999, paddingHorizontal: 14, minHeight: 44, borderWidth: 1, borderColor: "#E2E8F0" }}
        />
        <Pressable
          onPress={send}
          accessibilityRole="button"
          accessibilityLabel="Send question"
          style={{ backgroundColor: colors.primary, borderRadius: 999, paddingHorizontal: 18, minHeight: 44, justifyContent: "center" }}
        >
          <Text style={{ color: "#fff", fontWeight: "700" }}>Send</Text>
        </Pressable>
      </View>
    </View>
  );
}
