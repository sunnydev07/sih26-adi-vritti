import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { useState } from "react";
import { Text, TextInput, View } from "react-native";
import { PrimaryButton, Rise } from "@/components/ui";
import { colors } from "@/lib/theme";

export default function LoginScreen() {
  const router = useRouter();
  const [phone, setPhone] = useState("");

  return (
    <LinearGradient
      colors={[colors.primary, "#1E1B4B"]}
      start={{ x: 0, y: 0 }}
      end={{ x: 0.9, y: 1 }}
      style={{ flex: 1, padding: 24, justifyContent: "center" }}
    >
      <View
        style={{
          position: "absolute",
          width: 240,
          height: 240,
          borderRadius: 120,
          backgroundColor: "rgba(255,255,255,0.06)",
          top: -80,
          right: -80,
        }}
      />
      <View
        style={{
          position: "absolute",
          width: 160,
          height: 160,
          borderRadius: 80,
          backgroundColor: "rgba(245,158,11,0.14)",
          bottom: -50,
          left: -50,
        }}
      />

      <Rise delay={0}>
        <View
          style={{
            width: 68,
            height: 68,
            borderRadius: 34,
            backgroundColor: "rgba(255,255,255,0.14)",
            borderWidth: 1.5,
            borderColor: "rgba(255,255,255,0.35)",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Ionicons name="school" size={34} color="#fff" />
        </View>
      </Rise>
      <Rise delay={80}>
        <Text style={{ marginTop: 16, fontSize: 34, fontWeight: "800", color: "#fff" }}>Adi-Vritti</Text>
        <Text style={{ marginTop: 4, fontSize: 15, color: "#C7D2FE" }}>
          One student. One identity. Five schemes.
        </Text>
      </Rise>

      <Rise delay={170}>
        <View
          style={{
            marginTop: 26,
            backgroundColor: "#fff",
            borderRadius: 24,
            padding: 20,
          }}
        >
          <Text style={{ fontSize: 13, fontWeight: "800", color: colors.ink }}>Mobile number</Text>
          <View
            style={{
              marginTop: 8,
              flexDirection: "row",
              alignItems: "center",
              backgroundColor: "#F1F5F9",
              borderRadius: 16,
              borderWidth: 1.5,
              borderColor: colors.border,
              paddingHorizontal: 14,
            }}
          >
            <Text style={{ fontSize: 16, fontWeight: "700", color: colors.ink }}>+91</Text>
            <View style={{ width: 1, height: 24, backgroundColor: colors.border, marginHorizontal: 10 }} />
            <TextInput
              value={phone}
              onChangeText={(t) => setPhone(t.replace(/[^0-9]/g, "").slice(0, 10))}
              placeholder="98765 43210"
              keyboardType="phone-pad"
              maxLength={10}
              style={{ flex: 1, fontSize: 17, fontWeight: "600", paddingVertical: 14, color: colors.ink }}
            />
            {phone.length === 10 ? <Ionicons name="checkmark-circle" size={20} color="#047857" /> : null}
          </View>
          <View style={{ marginTop: 14 }}>
            <PrimaryButton amber title="Send OTP" icon="chatbox" onPress={() => router.replace("/(tabs)")} />
          </View>
          <Text style={{ marginTop: 12, fontSize: 11, color: colors.muted, textAlign: "center" }}>
            Demo build — any 10-digit number works. OTP arrives on SMS in production.
          </Text>
        </View>
      </Rise>
    </LinearGradient>
  );
}
