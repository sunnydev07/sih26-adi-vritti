import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { MotiView } from "moti";
import { useRef, useState } from "react";
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { PressableScale, Tx, TypingDots, shadow } from "@/components/ui";
import { answerLocal, askAdi, type AdiNotice } from "@/lib/assistant";
import { signOut } from "@/lib/session";
import { newId } from "@/lib/store";
import { assistantSuggestions, colors, radius } from "@/lib/theme";
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

/** Machine-readable side-channel captions (askAdi `notice`). */
function noticeCaption(notice: AdiNotice, hi: boolean): string | null {
  switch (notice) {
    case "mirror-offline":
      return hi
        ? "ऑफ़लाइन प्रति — केवल सामान्य उत्तर"
        : "Offline mirror — general answers only";
    case "lang-coming-soon":
      return hi
        ? "संथाली/गोंडी उत्तर जल्द आ रहे हैं — अभी अंग्रेज़ी में"
        : "Santali/Gondi answers coming soon — showing English for now";
    default:
      return null;
  }
}

export default function ChatScreen() {
  const router = useRouter();
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    const g = answerLocal("namaste", "hi");
    return [{ id: "m0", role: "jago", text: g.text, createdAt: new Date().toISOString() }];
  });
  const [draft, setDraft] = useState("");
  const [lang, setLang] = useState<SupportedLang>("hi");
  const [chips, setChips] = useState<string[]>(assistantSuggestions);
  const [sending, setSending] = useState(false);
  const listRef = useRef<FlatList<ChatMessage>>(null);

  async function sendText(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    const user: ChatMessage = {
      id: newId("u"),
      role: "user",
      text: trimmed,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, user]);
    setDraft("");
    setSending(true);
    // Online-first (AI /jago/help), offline-proof (local mirror for WIFI-OFF demo).
    const ans = await askAdi(trimmed, lang);
    setSending(false);
    if (ans.notice === "session-expired") {
      // The BFF said 401: the session is dead, so leave — staying would loop
      // every further question through the same expired credential.
      signOut();
      router.replace("/login");
      return;
    }
    const reply: ChatMessage = {
      id: newId("j"),
      role: "jago",
      text: ans.text,
      card: ans.lane === "status" ? statusCard() : undefined,
      notice: ans.notice,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, reply]);
    setChips(ans.suggestions.length > 0 ? ans.suggestions : assistantSuggestions);
  }

  function send() {
    void sendText(draft);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <LinearGradient
        colors={[colors.primary, "#1E1B4B"]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{
          paddingTop: 60,
          paddingBottom: 16,
          paddingHorizontal: 20,
          borderBottomLeftRadius: 28,
          borderBottomRightRadius: 28,
        }}
      >
        <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
          <View>
            <View
              style={{
                width: 48,
                height: 48,
                borderRadius: 24,
                backgroundColor: "rgba(255,255,255,0.16)",
                borderWidth: 1.5,
                borderColor: "rgba(255,255,255,0.35)",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Tx variant="title" weight="extrabold" color="#fff">
                A
              </Tx>
            </View>
            <View
              style={{
                position: "absolute",
                right: 1,
                bottom: 1,
                width: 13,
                height: 13,
                borderRadius: 7,
                backgroundColor: "#10B981",
                borderWidth: 2,
                borderColor: "#1E1B4B",
              }}
            />
          </View>
          <View style={{ flex: 1 }}>
            <Tx variant="title" weight="extrabold" color="#fff">
              Ask Adi
            </Tx>
            <Tx variant="tiny" weight="medium" color="#C7D2FE">
              Hindi · English · Santali · Gondi — files stay template-filled, never guessed
            </Tx>
          </View>
        </View>
        <View style={{ flexDirection: "row", gap: 6, marginTop: 12 }}>
          {LANGS.map((l) => {
            const active = lang === l.id;
            return (
              <PressableScale
                key={l.id}
                onPress={() => setLang(l.id)}
                accessibilityLabel={`Chat in ${l.label}`}
                style={{
                  paddingHorizontal: 13,
                  paddingVertical: 7,
                  minHeight: 34,
                  borderRadius: radius.pill,
                  backgroundColor: active ? "#fff" : "rgba(255,255,255,0.14)",
                }}
              >
                <Tx variant="tiny" weight="extrabold" color={active ? colors.primary : "#E0E7FF"}>
                  {l.label}
                </Tx>
              </PressableScale>
            );
          })}
        </View>
      </LinearGradient>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        {/* FlatList, not ScrollView: every message used to mount an animated
            view forever, so a long JAGO session grew memory without bound on
            low-end devices. */}
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={(m) => m.id}
          style={{ flex: 1 }}
          contentContainerStyle={{ padding: 16, paddingBottom: 8 }}
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
          removeClippedSubviews
          maxToRenderPerBatch={10}
          windowSize={11}
          ListFooterComponent={
            sending ? (
              <View
                style={{
                  alignSelf: "flex-start",
                  backgroundColor: "#fff",
                  borderRadius: 18,
                  borderTopLeftRadius: 6,
                  borderWidth: 1,
                  borderColor: colors.border,
                  marginBottom: 8,
                }}
              >
                <TypingDots />
              </View>
            ) : null
          }
          renderItem={({ item: m }) =>
            m.role === "user" ? (
              <MotiView
                from={{ opacity: 0, translateY: 10, scale: 0.98 }}
                animate={{ opacity: 1, translateY: 0, scale: 1 }}
                transition={{ type: "spring", damping: 20, stiffness: 320 }}
                style={{
                  alignSelf: "flex-end",
                  borderRadius: 18,
                  borderBottomRightRadius: 6,
                  paddingHorizontal: 14,
                  paddingVertical: 10,
                  marginBottom: 8,
                  maxWidth: "85%",
                  backgroundColor: colors.primaryStrong,
                }}
              >
                <Tx variant="body" color="#fff">
                  {m.text}
                </Tx>
              </MotiView>
            ) : (
              <MotiView
                from={{ opacity: 0, translateY: 10, scale: 0.98 }}
                animate={{ opacity: 1, translateY: 0, scale: 1 }}
                transition={{ type: "spring", damping: 20, stiffness: 320 }}
                style={{ alignSelf: "flex-start", maxWidth: "88%", marginBottom: 8 }}
              >
                <Tx variant="tiny" weight="extrabold" color={colors.primarySoft} style={{ marginBottom: 3 }}>
                  ADI
                </Tx>
                {m.notice && noticeCaption(m.notice, lang === "hi") ? (
                  <Tx variant="tiny" color={colors.muted} style={{ marginBottom: 3 }}>
                    {noticeCaption(m.notice, lang === "hi")}
                  </Tx>
                ) : null}
                <View
                  style={[
                    {
                      backgroundColor: "#fff",
                      borderRadius: 18,
                      borderTopLeftRadius: 6,
                      paddingHorizontal: 14,
                      paddingVertical: 10,
                      borderWidth: 1,
                      borderColor: colors.border,
                    },
                    shadow.card,
                  ]}
                >
                  <Tx variant="body">{m.text}</Tx>
                  {m.card ? (
                    <View style={{ marginTop: 10, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 }}>
                      <Tx variant="caption" weight="extrabold">
                        {m.card.title}
                      </Tx>
                      {m.card.rows.map((r) => (
                        <Tx key={r.label} variant="caption" color="#475569" style={{ marginTop: 2 }}>
                          {r.label}: <Tx variant="caption" weight="bold">{r.value}</Tx>
                        </Tx>
                      ))}
                      <PressableScale
                        onPress={() => router.push("/")}
                        style={{ marginTop: 8, flexDirection: "row", alignItems: "center", gap: 2 }}
                        accessibilityLabel="Open Home tab"
                      >
                        <Tx variant="caption" weight="extrabold" color={colors.primaryStrong}>
                          See it in the app
                        </Tx>
                        <Ionicons name="chevron-forward" size={13} color={colors.primaryStrong} />
                      </PressableScale>
                    </View>
                  ) : null}
                </View>
              </MotiView>
            )
          }
        />

        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ maxHeight: 48 }}>
          <View style={{ flexDirection: "row", gap: 6, paddingHorizontal: 14, paddingVertical: 6 }}>
            {chips.map((c) => (
              <Pressable
                key={c}
                onPress={() => void sendText(c)}
                style={{
                  paddingHorizontal: 13,
                  paddingVertical: 8,
                  borderRadius: radius.pill,
                  backgroundColor: "#EEF2FF",
                  borderWidth: 1,
                  borderColor: "#C7D2FE",
                }}
              >
                <Tx variant="caption" weight="bold" color={colors.primaryStrong}>
                  {c}
                </Tx>
              </Pressable>
            ))}
          </View>
        </ScrollView>

        <View
          style={{
            flexDirection: "row",
            gap: 8,
            paddingHorizontal: 14,
            paddingTop: 8,
            paddingBottom: 108,
            alignItems: "center",
          }}
        >
          <TextInput
            value={draft}
            onChangeText={setDraft}
            onSubmitEditing={send}
            placeholder="Ask Adi…"
            returnKeyType="send"
            style={{
              flex: 1,
              backgroundColor: "#fff",
              borderRadius: radius.pill,
              paddingHorizontal: 16,
              minHeight: 48,
              fontSize: 15,
              fontFamily: "PlusJakartaSans_400Regular",
              borderWidth: 1,
              borderColor: colors.border,
            }}
          />
          <PressableScale
            onPress={send}
            haptic
            accessibilityLabel="Send question"
            style={{
              width: 48,
              height: 48,
              borderRadius: 24,
              backgroundColor: draft.trim() ? colors.primaryStrong : "#CBD5E1",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Ionicons name="send" size={20} color="#fff" />
          </PressableScale>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}
