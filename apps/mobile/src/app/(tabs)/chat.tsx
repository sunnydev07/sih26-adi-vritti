import { useState } from "react";
import { Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { mockMessages, renderStatusTemplate } from "@/lib/jago";
import type { ChatMessage, SupportedLang } from "@/types";

const LANGS: { id: SupportedLang; label: string }[] = [
  { id: "hi", label: "Hindi" },
  { id: "en", label: "English" },
  { id: "sat", label: "Santali" },
  { id: "gon", label: "Gondi" },
];

export default function ChatScreen() {
  const [messages, setMessages] = useState<ChatMessage[]>(mockMessages);
  const [draft, setDraft] = useState("");
  const [lang, setLang] = useState<SupportedLang>("hi");

  function send() {
    const text = draft.trim();
    if (!text) return;
    const user: ChatMessage = { id: `u-${Date.now()}`, role: "user", text, createdAt: new Date().toISOString() };
    // Status answers are template-filled from tool output — never free-generated.
    const reply: ChatMessage = {
      id: `j-${Date.now()}`,
      role: "jago",
      text: renderStatusTemplate({
        schemeName: "Post-Matric",
        stage: "district nodal",
        actor: "District Nodal Officer, Mandla",
        days: 11,
        slaDays: 7,
      }),
      card: {
        title: "APP-90412 · Post-Matric",
        rows: [{ label: "SLA", value: "11 / 7 days — breached" }],
        actions: ["Update IFSC", "Connect DigiLocker"],
      },
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, user, reply]);
    setDraft("");
  }

  return (
    <View style={{ flex: 1, backgroundColor: "#F8FAFC", paddingTop: 48 }}>
      <View style={{ flexDirection: "row", gap: 6, paddingHorizontal: 16 }}>
        {LANGS.map((l) => (
          <Pressable
            key={l.id}
            onPress={() => setLang(l.id)}
            style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, backgroundColor: lang === l.id ? "#312E81" : "#E2E8F0" }}
          >
            <Text style={{ color: lang === l.id ? "#fff" : "#312E81", fontSize: 12, fontWeight: "700" }}>{l.label}</Text>
          </Pressable>
        ))}
      </View>
      <ScrollView style={{ flex: 1, padding: 16 }}>
        {messages.map((m) => (
          <View
            key={m.id}
            style={{
              alignSelf: m.role === "user" ? "flex-end" : "flex-start",
              backgroundColor: m.role === "user" ? "#312E81" : "#fff",
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
      </ScrollView>
      <View style={{ flexDirection: "row", gap: 8, padding: 12 }}>
        <TextInput
          value={draft}
          onChangeText={setDraft}
          placeholder="Ask JAGO+…"
          style={{ flex: 1, backgroundColor: "#fff", borderRadius: 999, paddingHorizontal: 14, borderWidth: 1, borderColor: "#E2E8F0" }}
        />
        <Pressable onPress={send} style={{ backgroundColor: "#312E81", borderRadius: 999, paddingHorizontal: 18, justifyContent: "center" }}>
          <Text style={{ color: "#fff", fontWeight: "700" }}>Send</Text>
        </Pressable>
      </View>
    </View>
  );
}
