import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { formatPaise, greetingFor, slaTone } from "@/lib/format";
import { mockDashboard } from "@/lib/jago";

const TONE: Record<string, string> = { green: "#10B981", amber: "#F59E0B", red: "#F43F5E" };

export default function StudentDashboardScreen() {
  const [d] = useState(mockDashboard);
  const greet = greetingFor(new Date());

  return (
    <ScrollView style={{ flex: 1, backgroundColor: "#F8FAFC" }}>
      <View style={{ padding: 16, paddingTop: 48 }}>
        <Text style={{ fontSize: 22, fontWeight: "800", color: "#312E81" }}>
          {greet}, {d.greetingName}
        </Text>
        {d.offline ? (
          <Text style={{ color: "#B45309" }}>Offline — showing cached data</Text>
        ) : (
          <Text style={{ color: "#64748B", fontSize: 12 }}>Last sync {d.lastSyncAt}</Text>
        )}

        <Text style={{ marginTop: 18, fontWeight: "700" }}>Your Scholarships</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: 8 }}>
          {d.schemes.map((s) => (
            <View
              key={s.name}
              style={{
                width: 200,
                marginRight: 10,
                padding: 14,
                borderRadius: 16,
                backgroundColor: s.eligible ? "#EEF2FF" : "#F1F5F9",
                borderWidth: s.eligible ? 2 : 1,
                borderColor: s.eligible ? "#6366F1" : "#E2E8F0",
                opacity: s.eligible ? 1 : 0.75,
              }}
            >
              <Text style={{ fontWeight: "800" }}>{s.name}</Text>
              <Text style={{ fontSize: 12, color: "#475569" }}>{s.status}</Text>
              {s.eligible ? <Text style={{ marginTop: 6, fontWeight: "800" }}>{formatPaise(s.amountPaise)}</Text> : null}
              {s.reason ? <Text style={{ fontSize: 11, color: "#64748B" }}>{s.reason}</Text> : null}
            </View>
          ))}
        </ScrollView>

        <Text style={{ marginTop: 18, fontWeight: "700" }}>Where your files are</Text>
        {d.applications.map((a) => (
          <View key={a.id} style={{ marginTop: 8, padding: 14, borderRadius: 16, backgroundColor: "#fff", borderWidth: 1, borderColor: "#E2E8F0" }}>
            <Text style={{ fontWeight: "700" }}>{a.scheme}</Text>
            <Text style={{ fontSize: 12 }}>{a.stage} → {a.actor}</Text>
            <Text style={{ fontSize: 12, color: TONE[slaTone(a.elapsedDays, a.slaDays)], fontWeight: "700" }}>
              {a.elapsedDays} days (SLA: {a.slaDays})
            </Text>
          </View>
        ))}

        <Text style={{ marginTop: 18, fontWeight: "700" }}>Money</Text>
        <View style={{ flexDirection: "row", gap: 10, marginTop: 8 }}>
          <View style={{ flex: 1, padding: 14, borderRadius: 16, backgroundColor: "#ECFDF5" }}>
            <Text style={{ fontSize: 12, color: "#047857" }}>Received</Text>
            <Text style={{ fontSize: 20, fontWeight: "800", color: "#047857" }}>{formatPaise(d.receivedPaise)}</Text>
          </View>
          <View style={{ flex: 1, padding: 14, borderRadius: 16, backgroundColor: "#FFFBEB" }}>
            <Text style={{ fontSize: 12, color: "#B45309" }}>Pending</Text>
            <Text style={{ fontSize: 20, fontWeight: "800", color: "#B45309" }}>{formatPaise(d.pendingPaise)}</Text>
          </View>
        </View>

        <Text style={{ marginTop: 18, fontWeight: "700" }}>Actions needed</Text>
        {d.actions.map((a) => (
          <View key={a.id} style={{ marginTop: 8, padding: 14, borderRadius: 16, backgroundColor: "#fff", borderColor: "#F59E0B", borderWidth: 1.5 }}>
            <Text style={{ fontSize: 13 }}>{a.title}</Text>
            <Pressable style={{ marginTop: 8, backgroundColor: "#312E81", borderRadius: 999, padding: 10, alignItems: "center" }}>
              <Text style={{ color: "#fff", fontWeight: "700" }}>{a.cta}</Text>
            </Pressable>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}
