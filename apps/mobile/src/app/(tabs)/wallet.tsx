import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { mockClaims } from "@/lib/jago";

const STATUS_COLOR: Record<string, string> = { valid: "#10B981", expiring: "#F59E0B", expired: "#F43F5E" };

export default function WalletScreen() {
  const [claims] = useState(mockClaims);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: "#F8FAFC" }}>
      <View style={{ padding: 16, paddingTop: 48 }}>
        <Text style={{ fontSize: 22, fontWeight: "800", color: "#312E81" }}>Document Wallet</Text>
        <Text style={{ fontSize: 12, color: "#64748B" }}>Verified once, reused across all 5 schemes</Text>

        <Pressable style={{ marginTop: 12, backgroundColor: "#312E81", borderRadius: 999, padding: 12, alignItems: "center" }}>
          <Text style={{ color: "#fff", fontWeight: "700" }}>Connect DigiLocker (live demo)</Text>
        </Pressable>
        <Pressable style={{ marginTop: 8, borderColor: "#312E81", borderWidth: 1, borderRadius: 999, padding: 12, alignItems: "center" }}>
          <Text style={{ color: "#312E81", fontWeight: "700" }}>Scan Document (on-device OCR)</Text>
        </Pressable>

        {claims.map((c) => (
          <View key={c.id} style={{ marginTop: 10, padding: 14, borderRadius: 16, backgroundColor: "#fff", borderWidth: 1.5, borderColor: STATUS_COLOR[c.status] ?? "#E2E8F0" }}>
            <Text style={{ fontWeight: "800" }}>{c.type}</Text>
            <Text style={{ fontSize: 12 }}>{c.preview}</Text>
            <Text style={{ fontSize: 11, color: "#64748B" }}>
              {c.status === "valid" ? "✅ Valid" : c.status === "expiring" ? "⏳ Expiring soon" : "❌ Expired"} · {c.source} · verified {c.verifiedAt.slice(0, 10)}
            </Text>
            {c.status !== "valid" ? (
              <Pressable style={{ marginTop: 8, backgroundColor: "#F59E0B", borderRadius: 999, padding: 9, alignItems: "center" }}>
                <Text style={{ fontWeight: "700" }}>Refetch from DigiLocker</Text>
              </Pressable>
            ) : (
              <Text style={{ marginTop: 6, fontSize: 11, color: "#64748B" }}>Verified via DigiLocker — cached offline with signature</Text>
            )}
          </View>
        ))}
      </View>
    </ScrollView>
  );
}
