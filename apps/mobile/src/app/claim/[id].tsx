import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { claimWords } from "@/lib/plainLanguage";
import { colors, radius, toneColors } from "@/lib/theme";
import { mockClaims } from "@/lib/jago";

/** Single wallet claim. Mock-driven; wire to GET /v1/scholars/{usid}/claims in integration. */
export default function ClaimDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [requested, setRequested] = useState(false);
  const claim = mockClaims.find((c) => c.id === id);

  if (!claim) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.page, padding: 16, paddingTop: 48 }}>
        <Text style={{ fontSize: 17, fontWeight: "800" }}>Document not found</Text>
        <Pressable onPress={() => router.back()} style={{ marginTop: 12, padding: 12, borderRadius: 999, backgroundColor: colors.primary, alignItems: "center" }}>
          <Text style={{ color: "#fff", fontWeight: "700" }}>Go back</Text>
        </Pressable>
      </View>
    );
  }

  const words = claimWords(claim.status);
  const tone = toneColors(words.tone);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.page }}>
      <View style={{ padding: 16, paddingTop: 48 }}>
        <Pressable onPress={() => router.back()} accessibilityLabel="Go back">
          <Text style={{ color: colors.primary, fontWeight: "700" }}>‹ Back</Text>
        </Pressable>
        <Text style={{ marginTop: 8, fontSize: 22, fontWeight: "800", color: colors.primary }}>
          {claim.type}
        </Text>
        <Text style={{ fontSize: 12, color: colors.muted }}>{claim.preview}</Text>

        <View style={{ marginTop: 12, padding: 16, borderRadius: radius.card, backgroundColor: tone.bg, borderWidth: 1.5, borderColor: tone.color }}>
          <Text style={{ fontSize: 17, fontWeight: "800", color: "#0F172A" }}>{words.words}</Text>
          <Text style={{ fontSize: 12, color: "#475569", marginTop: 4 }}>{words.detail}</Text>
          <Text style={{ fontSize: 11, color: colors.muted, marginTop: 6 }}>
            From {claim.source} · verified {claim.verifiedAt.slice(0, 10)} · valid till {claim.validUntil.slice(0, 10)}
          </Text>
        </View>

        {claim.status !== "valid" ? (
          requested ? (
            <Text style={{ marginTop: 12, fontSize: 13, color: "#047857", fontWeight: "600" }}>
              Request sent — your fresh copy will appear here once DigiLocker responds.
            </Text>
          ) : (
            <Pressable
              onPress={() => setRequested(true)}
              style={{ marginTop: 12, padding: 12, borderRadius: 999, backgroundColor: colors.accent, alignItems: "center" }}
            >
              <Text style={{ fontWeight: "700" }}>Refetch from DigiLocker</Text>
            </Pressable>
          )
        ) : (
          <Text style={{ marginTop: 12, fontSize: 12, color: colors.muted }}>
            Verified once, reused across all 5 schemes until expiry.
          </Text>
        )}
      </View>
    </ScrollView>
  );
}
