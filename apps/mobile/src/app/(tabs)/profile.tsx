import { Text, View } from "react-native";
import { maskAadhaar } from "@/lib/format";

export default function ProfileScreen() {
  return (
    <View style={{ flex: 1, backgroundColor: "#F8FAFC", padding: 16, paddingTop: 48 }}>
      <Text style={{ fontSize: 22, fontWeight: "800", color: "#312E81" }}>Profile & Settings</Text>
      <View style={{ marginTop: 12, backgroundColor: "#fff", borderRadius: 16, padding: 14, borderWidth: 1, borderColor: "#E2E8F0" }}>
        <Text style={{ fontWeight: "700" }}>Sunita Meena</Text>
        <Text style={{ fontSize: 12, color: "#64748B" }}>Aadhaar {maskAadhaar("XXXX4821")} · USID ··A91F04C2</Text>
        <Text style={{ fontSize: 12, color: "#64748B", marginTop: 6 }}>Who looked at my data: District Nodal (12 Aug) · PFMS (18 Sep)</Text>
      </View>
    </View>
  );
}
