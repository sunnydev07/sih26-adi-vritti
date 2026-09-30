import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import { Link } from "expo-router";
import ChatFab from "@/components/ChatFab";
import { mockClaims } from "@/lib/jago";

const STATUS_COLOR: Record<string, string> = { valid: "#10B981", expiring: "#F59E0B", expired: "#F43F5E" };

export default function WalletScreen() {
  const [claims] = useState(mockClaims);

  return (
    <View style={{ flex: 1 }}>
    <ScrollView style={{ flex: 1, backgroundColor: "#F8FAFC" }}>
      <View style={{ padding: 16, paddingTop: 48, paddingBottom: 96 }}>
        <Text style={{ fontSize: 22, fontWeight: "800", color: "#312E81" }}>Document Wallet</Text>
        <Text style={{ fontSize: 12, color: "#64748B" }}>Verified once, reused across all 5 schemes</Text>

        <Pressable style={{ marginTop: 12, backgroundColor: "#312E81", borderRadius: 999, padding: 12, alignItems: "center" }}>
          <Text style={{ color: "#fff", fontWeight: "700" }}>Connect DigiLocker (live demo)</Text>
        </Pressable>
        <Pressable style={{ marginTop: 8, borderColor: "#312E81", borderWidth: 1, borderRadius: 999, padding: 12, alignItems: "center" }}>
          <Text style={{ color: "#312E81", fontWeight: "700" }}>Scan Document (on-device OCR)</Text>
        </Pressable>

        {claims.map((c) => (
          <Link key={c.id} href={`/claim/${c.id}`} asChild>
            <Pressable style={{ marginTop: 10, padding: 14, borderRadius: 16, backgroundColor: "#fff", borderWidth: 1.5, borderColor: STATUS_COLOR[c.status] ?? "#E2E8F0" }}>
              <Text style={{ fontWeight: "800" }}>{c.type}</Text>
              <Text style={{ fontSize: 12 }}>{c.preview}</Text>
              <Text style={{ fontSize: 11, color: "#64748B" }}>
                {c.status === "valid" ? "✅ Valid" : c.status === "expiring" ? "⏳ Expiring soon" : "❌ Expired"} · {c.source} · verified {c.verifiedAt.slice(0, 10)}
              </Text>
              <Text style={{ marginTop: 6, fontSize: 12, fontWeight: "700", color: "#312E81" }}>
                {c.status !== "valid" ? "Refetch from DigiLocker →" : "Details →"}
              </Text>
            </Pressable>
          </Link>
        ))}
      </View>
    </ScrollView>
      <ChatFab />
    </View>
  );
}
