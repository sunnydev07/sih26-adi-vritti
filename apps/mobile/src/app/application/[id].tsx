import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { ScrollView, View } from "react-native";
import {
  AnimatedBar,
  Card,
  Eyebrow,
  Pill,
  PressableScale,
  PrimaryButton,
  Rise,
  Stepper,
  Tx,
} from "@/components/ui";
import { slaWords } from "@/lib/plainLanguage";
import { colors, toneColors } from "@/lib/theme";
import { mockDashboard } from "@/lib/jago";

/** Single application timeline. Mock-driven; wire to GET /v1/applications/{id}/timeline in integration. */
export default function ApplicationDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const app = mockDashboard.applications.find((a) => a.id === id);

  if (!app) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.page, padding: 20, paddingTop: 64 }}>
        <Tx variant="title" weight="extrabold">
          File not found
        </Tx>
        <Tx variant="caption" color={colors.muted} style={{ marginTop: 4 }}>
          This application is not on your phone. Pull to sync on Home.
        </Tx>
        <View style={{ marginTop: 16 }}>
          <PrimaryButton title="Go back" icon="arrow-back" onPress={() => router.back()} />
        </View>
      </View>
    );
  }

  const sla = slaWords(app.elapsedDays, app.slaDays);
  const tone = toneColors(sla.tone);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.page }}>
      <View style={{ padding: 16, paddingTop: 60, paddingBottom: 40 }}>
        <PressableScale
          onPress={() => router.back()}
          accessibilityLabel="Go back"
          style={{ alignSelf: "flex-start", padding: 6 }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", gap: 2 }}>
            <Ionicons name="arrow-back" size={18} color={colors.primaryStrong} />
            <Tx variant="body" weight="bold" color={colors.primaryStrong}>
              Back
            </Tx>
          </View>
        </PressableScale>

        <Rise delay={30}>
          <View style={{ marginTop: 10, flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
            <View style={{ flex: 1, paddingRight: 8 }}>
              <Tx variant="hero" weight="extrabold">
                {app.scheme}
              </Tx>
              <Tx variant="caption" color={colors.muted} style={{ fontFamily: "monospace" }}>
                {app.id}
              </Tx>
            </View>
            <Pill tone={sla.tone} label={sla.headline} />
          </View>
        </Rise>

        <Rise delay={110}>
          <Card style={{ marginTop: 14, borderLeftWidth: 5, borderLeftColor: tone.color }}>
            <Eyebrow>Waiting time</Eyebrow>
            <View style={{ marginTop: 8 }}>
              <AnimatedBar progress={app.elapsedDays / Math.max(1, app.slaDays)} color={tone.color} />
            </View>
            <Tx variant="caption" color="#475569" style={{ marginTop: 8 }}>
              {sla.detail}
            </Tx>
          </Card>
        </Rise>

        <Rise delay={190}>
          <Card style={{ marginTop: 12 }}>
            <Eyebrow>Where it is</Eyebrow>
            <View style={{ marginTop: 12 }}>
              <Stepper
                steps={[
                  { label: "Application received", sub: "Your form reached the portal", state: "done" },
                  { label: app.stage, sub: `With: ${app.actor}`, state: "now" },
                  { label: "Officer decision", sub: "Approve or ask for one fix", state: "todo" },
                  { label: "Bank payment", sub: "Money lands in your account", state: "todo" },
                ]}
              />
            </View>
          </Card>
        </Rise>

        <Rise delay={270}>
          <View style={{ marginTop: 16 }}>
            <PrimaryButton
              title="Ask Adi about this file"
              icon="chatbubbles"
              onPress={() => router.push("/chat")}
            />
          </View>
        </Rise>
      </View>
    </ScrollView>
  );
}
