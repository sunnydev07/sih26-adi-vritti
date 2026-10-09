import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useState } from "react";
import { ScrollView, View } from "react-native";
import {
  Card,
  Eyebrow,
  Pill,
  PressableScale,
  PrimaryButton,
  Rise,
  Tx,
  inDate,
  type IconName,
} from "@/components/ui";
import { successTap } from "@/lib/feedback";
import { useLang } from "@/lib/lang";
import { claimWords } from "@/lib/plainLanguage";
import { colors } from "@/lib/theme";
import { mockClaims } from "@/lib/jago";

const DOC_ICON: Record<string, IconName> = {
  "ST Certificate": "ribbon",
  "Income Certificate": "cash",
  "Bank Account": "card",
};

/** Single wallet claim. Mock-driven; wire to GET /v1/scholars/{usid}/claims in integration. */
export default function ClaimDetailScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { t } = useLang();
  const [requested, setRequested] = useState(false);
  // Deep links can carry repeated params (string[]); the lookup below would
  // otherwise miss and show "not found" for a document that exists.
  const rawId = params.id;
  const id = Array.isArray(rawId) ? rawId[0] : rawId;
  const claim = mockClaims.find((c) => c.id === id);

  // A cold deep link has no navigation history: bare router.back() visibly
  // does nothing on a dead-end screen, so fall back to Home.
  function goBack() {
    if (router.canGoBack()) router.back();
    else router.replace("/");
  }

  if (!claim) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.page, padding: 20, paddingTop: 64 }}>
        <Tx variant="title" weight="extrabold">
          {t.claimNotFound}
        </Tx>
        <View style={{ marginTop: 16 }}>
          <PrimaryButton title={t.claimGoBack} icon="arrow-back" onPress={goBack} />
        </View>
      </View>
    );
  }

  const words = claimWords(claim.status);
  const icon = DOC_ICON[claim.type] ?? "document-text";

  function refetch() {
    setRequested(true);
    successTap();
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.page }}>
      <View style={{ padding: 16, paddingTop: 60, paddingBottom: 40 }}>
        <PressableScale
          onPress={goBack}
          accessibilityLabel={t.claimBack}
          style={{ alignSelf: "flex-start", padding: 6 }}
        >
          <View style={{ flexDirection: "row", alignItems: "center", gap: 2 }}>
            <Ionicons name="arrow-back" size={18} color={colors.primaryStrong} />
            <Tx variant="body" weight="bold" color={colors.primaryStrong}>
              {t.claimBack}
            </Tx>
          </View>
        </PressableScale>

        <Rise delay={30}>
          <View style={{ marginTop: 10, flexDirection: "row", alignItems: "center", gap: 12 }}>
            <View
              style={{
                width: 56,
                height: 56,
                borderRadius: 28,
                backgroundColor: colors.primaryStrong,
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Ionicons name={icon} size={28} color="#fff" />
            </View>
            <View style={{ flex: 1 }}>
              <Tx variant="title" weight="extrabold">
                {claim.type}
              </Tx>
              <Tx variant="caption" color={colors.muted}>
                {claim.preview}
              </Tx>
            </View>
          </View>
        </Rise>

        <Rise delay={110}>
          <Card style={{ marginTop: 14 }}>
            <Pill tone={words.tone} label={words.words} />
            <Tx variant="body" color="#475569" style={{ marginTop: 10 }}>
              {words.detail}
            </Tx>
            <View style={{ marginTop: 12, gap: 8 }}>
              <MetaRow label={t.claimFrom} value={claim.source} />
              <MetaRow label={t.claimVerifiedAt} value={inDate(claim.verifiedAt)} />
              <MetaRow label={t.claimValidTill} value={inDate(claim.validUntil)} />
            </View>
          </Card>
        </Rise>

        <Rise delay={190}>
          {claim.status !== "valid" ? (
            requested ? (
              <Card style={{ marginTop: 12, backgroundColor: "#ECFDF5", borderColor: "#A7F3D0" }}>
                <View style={{ flexDirection: "row", gap: 10, alignItems: "flex-start" }}>
                  <Ionicons name="checkmark-circle" size={22} color="#047857" />
                  <Tx variant="caption" weight="semibold" color="#047857" style={{ flex: 1 }}>
                    {t.claimDemoRefetch}
                  </Tx>
                </View>
              </Card>
            ) : (
              <View style={{ marginTop: 14 }}>
                <PrimaryButton title={t.claimRefetch} icon="refresh" onPress={refetch} />
              </View>
            )
          ) : (
            <Card style={{ marginTop: 12, backgroundColor: "#EEF2FF", borderColor: "#C7D2FE" }}>
              <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-start" }}>
                <Ionicons name="shield-checkmark" size={18} color={colors.primaryStrong} />
                <Tx variant="caption" color={colors.primaryStrong} style={{ flex: 1 }}>
                  {t.walletExplainer}
                </Tx>
              </View>
            </Card>
          )}
        </Rise>

        <Rise delay={260}>
          <View style={{ marginTop: 16 }}>
            <Eyebrow>{t.claimConfused}</Eyebrow>
            <PressableScale
              onPress={() => router.push("/chat")}
              style={{ marginTop: 8, flexDirection: "row", alignItems: "center", gap: 4 }}
              accessibilityLabel={t.claimAskAdi}
            >
              <Tx variant="body" weight="extrabold" color={colors.primaryStrong}>
                {t.claimAskAdi}
              </Tx>
              <Ionicons name="chevron-forward" size={16} color={colors.primaryStrong} />
            </PressableScale>
          </View>
        </Rise>
      </View>
    </ScrollView>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <View
      style={{
        flexDirection: "row",
        justifyContent: "space-between",
        paddingVertical: 8,
        borderTopWidth: 1,
        borderTopColor: colors.border,
      }}
    >
      <Tx variant="caption" color={colors.muted}>
        {label}
      </Tx>
      <Tx variant="caption" weight="bold">
        {value}
      </Tx>
    </View>
  );
}
