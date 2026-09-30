import { Pressable, ScrollView, Text, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { slaWords } from "@/lib/plainLanguage";
import { colors, radius, toneColors } from "@/lib/theme";
import { mockDashboard } from "@/lib/jago";

/** Single application timeline. Mock-driven; wire to GET /v1/applications/{id}/timeline in integration. */
export default function ApplicationDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const app = mockDashboard.applications.find((a) => a.id === id);

  if (!app) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.page, padding: 16, paddingTop: 48 }}>
        <Text style={{ fontSize: 17, fontWeight: "800" }}>File not found</Text>
        <Pressable onPress={() => router.back()} style={{ marginTop: 12, padding: 12, borderRadius: 999, backgroundColor: colors.primary, alignItems: "center" }}>
          <Text style={{ color: "#fff", fontWeight: "700" }}>Go back</Text>
        </Pressable>
      </View>
    );
  }

  const sla = slaWords(app.elapsedDays, app.slaDays);
  const tone = toneColors(sla.tone);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.page }}>
      <View style={{ padding: 16, paddingTop: 48 }}>
        <Pressable onPress={() => router.back()} accessibilityLabel="Go back">
          <Text style={{ color: colors.primary, fontWeight: "700" }}>‹ Back</Text>
        </Pressable>
        <Text style={{ marginTop: 8, fontSize: 22, fontWeight: "800", color: colors.primary }}>
          {app.scheme}
        </Text>
        <Text style={{ fontSize: 12, color: colors.muted }}>{app.id}</Text>

        <View style={{ marginTop: 12, padding: 16, borderRadius: radius.card, backgroundColor: tone.bg, borderWidth: 1.5, borderColor: tone.color }}>
          <Text style={{ fontSize: 17, fontWeight: "800", color: "#0F172A" }}>{sla.headline}</Text>
          <Text style={{ fontSize: 12, color: "#475569", marginTop: 4 }}>{sla.detail}</Text>
        </View>

        <View style={{ marginTop: 12, padding: 14, borderRadius: radius.card, backgroundColor: "#fff", borderWidth: 1, borderColor: colors.border }}>
          <Text style={{ fontWeight: "700" }}>Where it is</Text>
          <Text style={{ fontSize: 13, marginTop: 4 }}>{app.stage}</Text>
          <Text style={{ fontSize: 12, color: colors.muted, marginTop: 2 }}>With: {app.actor}</Text>
        </View>

        <Pressable
          onPress={() => router.push("/chat")}
          style={{ marginTop: 12, padding: 12, borderRadius: 999, backgroundColor: colors.primary, alignItems: "center" }}
        >
          <Text style={{ color: "#fff", fontWeight: "700" }}>Ask Adi about this file</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}
