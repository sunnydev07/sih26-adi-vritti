import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import {
  AnimatedBar,
  Card,
  Eyebrow,
  GhostButton,
  Pill,
  PressableScale,
  PrimaryButton,
  Rise,
  ScreenHeader,
  SectionTitle,
  inDate,
  shadow,
} from "@/components/ui";
import { formatPaise, greetingFor } from "@/lib/format";
import { slaWords } from "@/lib/plainLanguage";
import { colors, radius, toneColors } from "@/lib/theme";
import { mockDashboard } from "@/lib/jago";

/** Chevron glyph that flips with expand state. */
function Chevron({ open }: { open: boolean }) {
  return <Ionicons name={open ? "chevron-up" : "chevron-down"} size={18} color={colors.primaryStrong} />;
}

/**
 * Student home answers three questions in order: where is my file,
 * what must I do next, and how much money. Everything else folds away.
 */
export default function StudentDashboardScreen() {
  const router = useRouter();
  const [d] = useState(mockDashboard);
  const [showSchemes, setShowSchemes] = useState(false);
  const [showActions, setShowActions] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [syncedAt, setSyncedAt] = useState(d.lastSyncAt);
  const greet = greetingFor(new Date());

  const hero = d.applications[0] ?? null;
  const heroSla = hero ? slaWords(hero.elapsedDays, hero.slaDays) : null;
  const heroTone = heroSla ? toneColors(heroSla.tone) : toneColors("info");
  const [firstAction, ...restActions] = d.actions;
  const eligible = d.schemes.filter((s) => s.eligible);
  const featured = eligible[0] ?? null;

  function onRefresh() {
    setRefreshing(true);
    setTimeout(() => {
      setSyncedAt(new Date().toISOString());
      setRefreshing(false);
    }, 1100);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <ScreenHeader
        eyebrow="Adi-Vritti · Student"
        title={`${greet}, ${d.greetingName}`}
        subtitle={d.offline ? "Offline — showing cached data" : `Last sync ${inDate(syncedAt)}`}
        avatar={d.greetingName.slice(0, 1)}
      />
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: 16, paddingBottom: 128 }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[colors.primaryStrong]} />
        }
      >
        {hero && heroSla ? (
          <Rise delay={40}>
            <PressableScale
              accessibilityRole="link"
              accessibilityLabel={`${hero.scheme} file: ${heroSla.headline}. Open timeline.`}
              onPress={() => router.push(`/application/${hero.id}`)}
              haptic
            >
              <Card style={{ borderLeftWidth: 5, borderLeftColor: heroTone.color, paddingTop: 14 }}>
                <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                  <Pill tone="info" label={hero.scheme} icon="school" />
                  <Text style={{ fontSize: 11, fontFamily: "monospace", color: colors.muted }}>{hero.id}</Text>
                </View>
                <Text style={{ marginTop: 10, fontSize: 19, fontWeight: "800", color: colors.ink }}>
                  {heroSla.headline}
                </Text>
                <Text style={{ marginTop: 3, fontSize: 13, color: "#475569" }}>
                  {hero.stage} · {hero.actor}
                </Text>
                <View style={{ marginTop: 12 }}>
                  <AnimatedBar progress={hero.elapsedDays / Math.max(1, hero.slaDays)} color={heroTone.color} />
                  <Text style={{ marginTop: 6, fontSize: 12, color: colors.muted }}>
                    Day {hero.elapsedDays} of {hero.slaDays} · {heroSla.detail}
                  </Text>
                </View>
                <View style={{ marginTop: 10, flexDirection: "row", alignItems: "center", gap: 2 }}>
                  <Text style={{ fontSize: 13, fontWeight: "800", color: colors.primaryStrong }}>
                    See full timeline
                  </Text>
                  <Ionicons name="chevron-forward" size={15} color={colors.primaryStrong} />
                </View>
              </Card>
            </PressableScale>
          </Rise>
        ) : null}

        {firstAction ? (
          <Rise delay={120}>
            <View
              style={[
                {
                  marginTop: 12,
                  padding: 16,
                  borderRadius: radius.card,
                  backgroundColor: "#FFFBEB",
                  borderWidth: 1.5,
                  borderColor: "#F59E0B",
                },
                shadow.card,
              ]}
            >
              <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                <Ionicons name="alert-circle" size={15} color="#B45309" />
                <Eyebrow color="#B45309">Do this next</Eyebrow>
              </View>
              <Text style={{ fontSize: 15, fontWeight: "600", color: colors.ink, marginTop: 6 }}>
                {firstAction.title}
              </Text>
              <View style={{ marginTop: 12 }}>
                <PrimaryButton
                  amber
                  title={firstAction.cta}
                  icon="refresh"
                  onPress={() => router.push("/wallet")}
                />
              </View>
              {restActions.length > 0 ? (
                <PressableScale
                  onPress={() => setShowActions((s) => !s)}
                  style={{ marginTop: 10, alignItems: "center", paddingVertical: 6 }}
                  accessibilityLabel={showActions ? "Hide more actions" : `Show ${restActions.length} more actions`}
                >
                  <Text style={{ fontSize: 13, color: colors.primaryStrong, fontWeight: "700" }}>
                    {showActions ? "Hide" : `+${restActions.length} more action${restActions.length === 1 ? "" : "s"}`}
                  </Text>
                </PressableScale>
              ) : null}
              {showActions
                ? restActions.map((a, i) => (
                    <Rise key={a.id} delay={i * 70}>
                      <PressableScale
                        onPress={() => router.push("/wallet")}
                        style={{
                          marginTop: 8,
                          padding: 12,
                          borderRadius: 12,
                          backgroundColor: "#fff",
                          borderWidth: 1,
                          borderColor: "#FDE68A",
                          flexDirection: "row",
                          alignItems: "center",
                          justifyContent: "space-between",
                        }}
                      >
                        <Text style={{ fontSize: 13, fontWeight: "600", color: colors.ink, flex: 1 }}>{a.title}</Text>
                        <Ionicons name="chevron-forward" size={16} color={colors.primaryStrong} />
                      </PressableScale>
                    </Rise>
                  ))
                : null}
            </View>
          </Rise>
        ) : null}

        <Rise delay={200}>
          <SectionTitle title="Money" action="Ask Adi →" onAction={() => router.push("/chat")} />
          <View style={{ flexDirection: "row", gap: 10 }}>
            <View style={{ flex: 1 }}>
              <Card style={{ backgroundColor: "#ECFDF5", borderColor: "#A7F3D0" }}>
                <View
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 17,
                    backgroundColor: "#10B981",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Ionicons name="cash" size={18} color="#fff" />
                </View>
                <Text style={{ marginTop: 8, fontSize: 12, fontWeight: "700", color: "#047857" }}>Received</Text>
                <Text style={{ fontSize: 20, fontWeight: "800", color: "#047857" }}>
                  {formatPaise(d.receivedPaise)}
                </Text>
                <Text style={{ fontSize: 11, color: "#047857" }}>Safe in your bank</Text>
              </Card>
            </View>
            <View style={{ flex: 1 }}>
              <Card style={{ backgroundColor: "#FFFBEB", borderColor: "#FDE68A" }}>
                <View
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 17,
                    backgroundColor: "#F59E0B",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Ionicons name="time" size={18} color="#fff" />
                </View>
                <Text style={{ marginTop: 8, fontSize: 12, fontWeight: "700", color: "#B45309" }}>Pending</Text>
                <Text style={{ fontSize: 20, fontWeight: "800", color: "#B45309" }}>
                  {formatPaise(d.pendingPaise)}
                </Text>
                <Text style={{ fontSize: 11, color: "#B45309" }}>On the way</Text>
              </Card>
            </View>
          </View>
        </Rise>

        <Rise delay={280}>
          <SectionTitle title="My scholarships" />
          {featured ? (
            <PressableScale onPress={() => setShowSchemes((s) => !s)} haptic>
              <Card style={{ backgroundColor: "#EEF2FF", borderColor: "#C7D2FE", borderWidth: 1.5 }}>
                <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 10, flex: 1 }}>
                    <View
                      style={{
                        width: 40,
                        height: 40,
                        borderRadius: 20,
                        backgroundColor: colors.primaryStrong,
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <Ionicons name="school" size={20} color="#fff" />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={{ fontSize: 16, fontWeight: "800", color: colors.ink }}>{featured.name}</Text>
                      <Text style={{ fontSize: 12, color: colors.muted }}>{featured.status}</Text>
                    </View>
                  </View>
                  <Chevron open={showSchemes} />
                </View>
                <Text style={{ marginTop: 8, fontSize: 22, fontWeight: "800", color: colors.primaryStrong }}>
                  {formatPaise(featured.amountPaise)}
                </Text>
                <Text style={{ fontSize: 12, color: colors.muted }}>
                  {eligible.length} you can get · tap to see all 5
                </Text>
              </Card>
            </PressableScale>
          ) : null}
          {showSchemes ? (
            <View style={{ marginTop: 10 }}>
              {d.schemes.map((s, i) => (
                <Rise key={s.name} delay={i * 70}>
                  <Card
                    style={{
                      marginBottom: 8,
                      backgroundColor: s.eligible ? "#fff" : "#F1F5F9",
                      borderColor: s.eligible ? "#C7D2FE" : colors.border,
                      opacity: s.eligible ? 1 : 0.85,
                    }}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                      <View
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: 16,
                          backgroundColor: s.eligible ? "#ECFDF5" : "#E2E8F0",
                          alignItems: "center",
                          justifyContent: "center",
                        }}
                      >
                        <Ionicons
                          name={s.eligible ? "checkmark" : "close"}
                          size={17}
                          color={s.eligible ? "#047857" : "#64748B"}
                        />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={{ fontWeight: "800", color: colors.ink }}>{s.name}</Text>
                        <Text style={{ fontSize: 12, color: "#475569" }}>{s.status}</Text>
                      </View>
                      {s.eligible ? (
                        <Text style={{ fontWeight: "800", color: colors.primaryStrong }}>
                          {formatPaise(s.amountPaise)}
                        </Text>
                      ) : null}
                    </View>
                    {s.reason ? (
                      <Text style={{ marginTop: 6, fontSize: 11, color: colors.muted }}>
                        {s.eligible ? s.reason : `Why not: ${s.reason}`}
                      </Text>
                    ) : null}
                  </Card>
                </Rise>
              ))}
            </View>
          ) : (
            <View style={{ marginTop: 10 }}>
              <GhostButton
                title={`Show all 5 scholarships`}
                icon="list"
                onPress={() => setShowSchemes(true)}
              />
            </View>
          )}
        </Rise>
      </ScrollView>
    </View>
  );
}
