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
import { useLang } from "@/lib/lang";
import { colors, toneColors } from "@/lib/theme";
import { mockDashboard } from "@/lib/jago";

/** Single application timeline. Mock-driven; wire to GET /v1/applications/{id}/timeline in integration. */
export default function ApplicationDetailScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { t } = useLang();
  // Deep links can carry repeated params (string[]); the lookup below would
  // otherwise miss and show "not found" for a file that exists.
  const rawId = params.id;
  const id = Array.isArray(rawId) ? rawId[0] : rawId;
  const app = mockDashboard.applications.find((a) => a.id === id);

  // A cold deep link has no navigation history: bare router.back() visibly
  // does nothing on a dead-end screen, so fall back to Home.
  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace("/");
  }

  if (!app) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.page, padding: 20, paddingTop: 64 }}>
        <Tx variant="title" weight="extrabold">
          {t.appNotFound}
        </Tx>
        <Tx variant="caption" color={colors.muted} style={{ marginTop: 4 }}>
          {t.appNotFoundSub}
        </Tx>
        <View style={{ marginTop: 16 }}>
          <PrimaryButton title={t.appGoBack} icon="arrow-back" onPress={goBack} />
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
          onPress={goBack}
          accessibilityLabel={t.appBack}
          style={{ alignSelf: "flex-start", padding: 6 }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", gap: 2 }}>
            <Ionicons name="arrow-back" size={18} color={colors.primaryStrong} />
            <Tx variant="body" weight="bold" color={colors.primaryStrong}>
              {t.appBack}
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
            <Eyebrow>{t.appWaiting}</Eyebrow>
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
            <Eyebrow>{t.appWhere}</Eyebrow>
            <View style={{ marginTop: 12 }}>
              <Stepper
                steps={[
                  { label: t.appStepReceived, sub: t.appStepReceivedSub, state: "done" },
                  { label: app.stage, sub: `${t.appStepWith}${app.actor}`, state: "now" },
                  { label: t.appStepDecision, sub: t.appStepDecisionSub, state: "todo" },
                  { label: t.appStepPayment, sub: t.appStepPaymentSub, state: "todo" },
                ]}
              />
            </View>
          </Card>
        </Rise>

        <Rise delay={270}>
          <View style={{ marginTop: 16 }}>
            <PrimaryButton
              title={t.appAskAdi}
              icon="chatbubbles"
              onPress={() => router.push("/chat")}
            />
          </View>
        </Rise>
      </View>
    </ScrollView>
  );
}
