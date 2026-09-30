import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { RefreshControl, ScrollView, View } from "react-native";
import {
  AnimatedBar,
  Card,
  Eyebrow,
  GhostButton,
  Money,
  Pill,
  PressableScale,
  PrimaryButton,
  Rise,
  ScreenHeader,
  SectionTitle,
  Skeleton,
  Tx,
  inDate,
  ink2,
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

function HomeSkeleton({ name, greet }: { name: string; greet: string }) {
  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <ScreenHeader eyebrow="Adi-Vritti · Student" title={`${greet}, ${name}`} subtitle="Syncing…" avatar={name.slice(0, 1)} />
      <View style={{ padding: 16, gap: 12 }}>
        <Skeleton h={168} r={radius.card} />
        <Skeleton h={150} r={radius.card} />
        <View style={{ flexDirection: "row", gap: 10 }}>
          <Skeleton h={120} r={radius.card} style={{ flex: 1 }} />
          <Skeleton h={120} r={radius.card} style={{ flex: 1 }} />
        </View>
        <Skeleton h={110} r={radius.card} />
      </View>
    </View>
  );
}

/**
 * Student home answers three questions in order: where is my file,
 * what must I do next, and how much money. Everything else folds away.
 */
export default function StudentDashboardScreen() {
  const router = useRouter();
  const [d] = useState(mockDashboard);
  const [ready, setReady] = useState(false);
  const [showSchemes, setShowSchemes] = useState(false);
  const [showActions, setShowActions] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [syncedAt, setSyncedAt] = useState(d.lastSyncAt);
  const greet = greetingFor(new Date());

  useEffect(() => {
    const t = setTimeout(() => setReady(true), 800);
    return () => clearTimeout(t);
  }, []);

  if (!ready) return <HomeSkeleton name={d.greetingName} greet={greet} />;

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
                  <Tx variant="tiny" color={colors.muted} style={{ fontFamily: "monospace" }}>
                    {hero.id}
                  </Tx>
                </View>
                <Tx variant="title" weight="extrabold" style={{ marginTop: 10 }}>
                  {heroSla.headline}
                </Tx>
                <Tx variant="caption" color={ink2} style={{ marginTop: 3 }}>
                  {hero.stage} · {hero.actor}
                </Tx>
                <View style={{ marginTop: 12 }}>
                  <AnimatedBar progress={hero.elapsedDays / Math.max(1, hero.slaDays)} color={heroTone.color} />
                  <Tx variant="caption" color={colors.muted} style={{ marginTop: 6 }}>
                    Day {hero.elapsedDays} of {hero.slaDays} · {heroSla.detail}
                  </Tx>
                </View>
                <View style={{ marginTop: 10, flexDirection: "row", alignItems: "center", gap: 2 }}>
                  <Tx variant="caption" weight="extrabold" color={colors.primaryStrong}>
                    See full timeline
                  </Tx>
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
              <Tx variant="body" weight="semibold" style={{ marginTop: 6 }}>
                {firstAction.title}
              </Tx>
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
                  <Tx variant="caption" weight="bold" color={colors.primaryStrong}>
                    {showActions ? "Hide" : `+${restActions.length} more action${restActions.length === 1 ? "" : "s"}`}
                  </Tx>
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
                        <Tx variant="caption" weight="semibold" style={{ flex: 1 }}>
                          {a.title}
                        </Tx>
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
                <Tx variant="caption" weight="bold" color="#047857" style={{ marginTop: 8 }}>
                  Received
                </Tx>
                <Money paise={d.receivedPaise} color="#047857" />
                <Tx variant="tiny" color="#047857">
                  Safe in your bank
                </Tx>
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
                <Tx variant="caption" weight="bold" color="#B45309" style={{ marginTop: 8 }}>
                  Pending
                </Tx>
                <Money paise={d.pendingPaise} color="#B45309" />
                <Tx variant="tiny" color="#B45309">
                  On the way
                </Tx>
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
                      <Tx variant="body" weight="extrabold">
                        {featured.name}
                      </Tx>
                      <Tx variant="caption" color={colors.muted}>
                        {featured.status}
                      </Tx>
                    </View>
                  </View>
                  <Chevron open={showSchemes} />
                </View>
                <Tx variant="hero" weight="extrabold" color={colors.primaryStrong} style={{ marginTop: 8 }}>
                  {formatPaise(featured.amountPaise)}
                </Tx>
                <Tx variant="caption" color={colors.muted}>
                  {eligible.length} you can get · tap to see all 5
                </Tx>
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
                        <Tx variant="body" weight="extrabold">
                          {s.name}
                        </Tx>
                        <Tx variant="caption" color={ink2}>
                          {s.status}
                        </Tx>
                      </View>
                      {s.eligible ? (
                        <Tx variant="body" weight="extrabold" color={colors.primaryStrong}>
                          {formatPaise(s.amountPaise)}
                        </Tx>
                      ) : null}
                    </View>
                    {s.reason ? (
                      <Tx variant="tiny" color={colors.muted} style={{ marginTop: 6 }}>
                        {s.eligible ? s.reason : `Why not: ${s.reason}`}
                      </Tx>
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
