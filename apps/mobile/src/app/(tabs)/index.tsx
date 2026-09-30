import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { Link } from "expo-router";
import ChatFab from "@/components/ChatFab";
import { formatPaise, greetingFor } from "@/lib/format";
import { slaWords } from "@/lib/plainLanguage";
import { colors, radius, toneColors } from "@/lib/theme";
import { mockDashboard } from "@/lib/jago";

/**
 * Student home answers three questions in order: where is my file,
 * what must I do next, and how much money. Everything else folds away.
 */
export default function StudentDashboardScreen() {
  const [d] = useState(mockDashboard);
  const [showSchemes, setShowSchemes] = useState(false);
  const [showActions, setShowActions] = useState(false);
  const greet = greetingFor(new Date());

  const hero = d.applications[0] ?? null;
  const heroSla = hero ? slaWords(hero.elapsedDays, hero.slaDays) : null;
  const heroTone = heroSla ? toneColors(heroSla.tone) : toneColors("info");
  const [firstAction, ...restActions] = d.actions;

  return (
    <View style={{ flex: 1 }}>
      <ScrollView style={{ flex: 1, backgroundColor: colors.page }}>
        <View style={{ padding: 16, paddingTop: 48, paddingBottom: 96 }}>
          <Text style={{ fontSize: 22, fontWeight: "800", color: colors.primary }}>
            {greet}, {d.greetingName}
          </Text>
          {d.offline ? (
            <Text style={{ color: "#B45309" }}>Offline — showing cached data</Text>
          ) : (
            <Text style={{ color: colors.muted, fontSize: 12 }}>Last sync {d.lastSyncAt}</Text>
          )}

          {hero && heroSla ? (
            <Link href={`/application/${hero.id}`} asChild>
              <Pressable
                style={{
                  marginTop: 14,
                  padding: 16,
                  borderRadius: radius.card,
                  backgroundColor: heroTone.bg,
                  borderWidth: 1.5,
                  borderColor: heroTone.color,
                }}
              >
                <Text style={{ fontSize: 12, color: heroTone.ink, fontWeight: "700" }}>
                  {hero.scheme} · {hero.id}
                </Text>
                <Text style={{ marginTop: 4, fontSize: 17, fontWeight: "800", color: "#0F172A" }}>
                  {heroSla.headline}
                </Text>
                <Text style={{ fontSize: 12, color: "#475569" }}>
                  {hero.stage} · {hero.actor}
                </Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>{heroSla.detail}</Text>
                <Text style={{ marginTop: 8, fontSize: 13, fontWeight: "700", color: colors.primary }}>
                  See full timeline →
                </Text>
              </Pressable>
            </Link>
          ) : null}

          {firstAction ? (
            <View
              style={{
                marginTop: 12,
                padding: 14,
                borderRadius: radius.card,
                backgroundColor: "#fff",
                borderColor: colors.accent,
                borderWidth: 1.5,
              }}
            >
              <Text style={{ fontSize: 12, fontWeight: "700", color: "#B45309" }}>DO THIS NEXT</Text>
              <Text style={{ fontSize: 14, marginTop: 2 }}>{firstAction.title}</Text>
              <Pressable
                style={{ marginTop: 8, backgroundColor: colors.primary, borderRadius: 999, padding: 10, alignItems: "center" }}
              >
                <Text style={{ color: "#fff", fontWeight: "700" }}>{firstAction.cta}</Text>
              </Pressable>
              {restActions.length > 0 ? (
                <Pressable onPress={() => setShowActions((s) => !s)} style={{ marginTop: 8, alignItems: "center" }}>
                  <Text style={{ fontSize: 12, color: colors.primary, fontWeight: "600" }}>
                    {showActions ? "Hide" : `+${restActions.length} more action${restActions.length === 1 ? "" : "s"}`}
                  </Text>
                </Pressable>
              ) : null}
              {showActions
                ? restActions.map((a) => (
                    <View key={a.id} style={{ marginTop: 8, padding: 12, borderRadius: 12, backgroundColor: "#FFFBEB" }}>
                      <Text style={{ fontSize: 13 }}>{a.title}</Text>
                      <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary, marginTop: 4 }}>{a.cta} →</Text>
                    </View>
                  ))
                : null}
            </View>
          ) : null}

          <Text style={{ marginTop: 18, fontWeight: "700" }}>Money</Text>
          <View style={{ flexDirection: "row", gap: 10, marginTop: 8 }}>
            <View style={{ flex: 1, padding: 14, borderRadius: radius.card, backgroundColor: "#ECFDF5" }}>
              <Text style={{ fontSize: 12, color: "#047857" }}>Received</Text>
              <Text style={{ fontSize: 20, fontWeight: "800", color: "#047857" }}>{formatPaise(d.receivedPaise)}</Text>
            </View>
            <View style={{ flex: 1, padding: 14, borderRadius: radius.card, backgroundColor: "#FFFBEB" }}>
              <Text style={{ fontSize: 12, color: "#B45309" }}>Pending</Text>
              <Text style={{ fontSize: 20, fontWeight: "800", color: "#B45309" }}>{formatPaise(d.pendingPaise)}</Text>
            </View>
          </View>

          <Pressable
            onPress={() => setShowSchemes((s) => !s)}
            style={{ marginTop: 18, padding: 14, borderRadius: radius.card, backgroundColor: "#fff", borderWidth: 1, borderColor: colors.border }}
          >
            <Text style={{ fontWeight: "700" }}>
              {showSchemes ? "Hide" : "Show"} all 5 scholarships {showSchemes ? "▴" : "▾"}
            </Text>
            <Text style={{ fontSize: 12, color: colors.muted }}>
              {d.schemes.filter((s) => s.eligible).length} you can get · rest explain why not
            </Text>
          </Pressable>
          {showSchemes
            ? d.schemes.map((s) => (
                <View
                  key={s.name}
                  style={{
                    marginTop: 8,
                    padding: 14,
                    borderRadius: radius.card,
                    backgroundColor: s.eligible ? "#EEF2FF" : "#F1F5F9",
                    borderWidth: s.eligible ? 2 : 1,
                    borderColor: s.eligible ? colors.primarySoft : colors.border,
                    opacity: s.eligible ? 1 : 0.8,
                  }}
                >
                  <Text style={{ fontWeight: "800" }}>{s.name}</Text>
                  <Text style={{ fontSize: 12, color: "#475569" }}>{s.status}</Text>
                  {s.eligible ? <Text style={{ marginTop: 6, fontWeight: "800" }}>{formatPaise(s.amountPaise)}</Text> : null}
                  {s.reason ? (
                    <Text style={{ fontSize: 11, color: colors.muted }}>
                      {s.eligible ? s.reason : `Why not: ${s.reason}`}
                    </Text>
                  ) : null}
                </View>
              ))
            : null}
        </View>
      </ScrollView>
      <ChatFab />
    </View>
  );
}
