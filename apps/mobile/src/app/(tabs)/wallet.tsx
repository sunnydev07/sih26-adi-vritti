import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useState } from "react";import { ScrollView, View } from "react-native";
import {
  Card,
  GhostButton,
  Pill,
  PressableScale,
  PrimaryButton,
  Rise,
  ScreenHeader,
  Tx,
  type IconName,
} from "@/components/ui";
import { successTap } from "@/lib/feedback";
import { useLang } from "@/lib/lang";
import { claimWords } from "@/lib/plainLanguage";
import { colors } from "@/lib/theme";
import { useMounted } from "@/lib/useMounted";
import { mockClaims } from "@/lib/jago";
import type { ClaimState } from "@/types";

const DOC_ICON: Record<string, IconName> = {
  "ST Certificate": "ribbon",
  "Income Certificate": "cash",
  "Bank Account": "card",
};

const STATUS_ICON: Record<ClaimState, IconName> = {
  valid: "checkmark-circle",
  expiring: "time",
  expired: "alert-circle",
};

type ConnectState = "idle" | "busy" | "done";

export default function WalletScreen() {
  const router = useRouter();
  const { t } = useLang();
  const [claims] = useState(mockClaims);
  const [connect, setConnect] = useState<ConnectState>("idle");
  const mounted = useMounted();

  function connectLocker() {
    if (connect !== "idle") return;
    setConnect("busy");
    setTimeout(() => {
      if (!mounted.current) return;
      setConnect("done");
      successTap();
    }, 1400);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <ScreenHeader
        eyebrow={t.walletEyebrow}
        title={t.walletTitle}
        subtitle={t.walletReady(claims.filter((c) => c.status === "valid").length, claims.length)}
        icon="wallet"
      />
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, paddingBottom: 128 }}>
        <Rise delay={40}>
          {connect === "done" ? (
            <Card style={{ backgroundColor: "#ECFDF5", borderColor: "#A7F3D0", flexDirection: "row", alignItems: "center", gap: 10 }}>
              <Ionicons name="checkmark-circle" size={24} color="#047857" />
              <View style={{ flex: 1 }}>
                <Tx variant="body" weight="extrabold" color="#047857">
                  {t.walletStagedTitle}
                </Tx>
                <Tx variant="caption" color="#047857">
                  {t.walletStagedSub}
                </Tx>
              </View>
            </Card>
          ) : (
            <PrimaryButton
              title={connect === "busy" ? t.walletConnecting : t.walletConnect}
              icon={connect === "busy" ? "sync" : "cloud-upload"}
              onPress={connectLocker}
            />
          )}
          <View style={{ marginTop: 10 }}>
            <GhostButton title={t.walletScan} icon="scan" onPress={() => router.push("/chat")} />
          </View>
        </Rise>

        {claims.map((c, i) => {
          const words = claimWords(c.status);
          return (
            <Rise key={c.id} delay={120 + i * 80}>
              <PressableScale
                accessibilityRole="link"
                accessibilityLabel={`${c.type}: ${words.words}. Open details.`}
                onPress={() => router.push(`/claim/${c.id}`)}
                haptic
                style={{ marginTop: 10 }}
              >
                <Card>
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
                    <View
                      style={{
                        width: 44,
                        height: 44,
                        borderRadius: 22,
                        backgroundColor: "#EEF2FF",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <Ionicons name={DOC_ICON[c.type] ?? "document-text"} size={22} color={colors.primaryStrong} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Tx variant="body" weight="extrabold">
                        {c.type}
                      </Tx>
                      <Tx variant="caption" color={colors.muted}>
                        {c.preview}
                      </Tx>
                    </View>
                    <Ionicons name="chevron-forward" size={18} color={colors.muted} />
                  </View>
                  <View style={{ marginTop: 10, flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                    <Pill tone={words.tone} label={words.words} icon={STATUS_ICON[c.status]} />
                    <Tx variant="tiny" color={colors.muted}>
                      {c.source} · {c.verifiedAt.slice(0, 10)}
                    </Tx>
                  </View>
                </Card>
              </PressableScale>
            </Rise>
          );
        })}

        <Rise delay={120 + claims.length * 80}>
          <Card style={{ marginTop: 12, backgroundColor: "#EEF2FF", borderColor: "#C7D2FE" }}>
            <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-start" }}>
              <Ionicons name="shield-checkmark" size={18} color={colors.primaryStrong} />
              <Tx variant="caption" color={colors.primaryStrong} style={{ flex: 1 }}>
                {t.walletExplainer}
              </Tx>
            </View>
          </Card>
        </Rise>
      </ScrollView>
    </View>
  );
}
