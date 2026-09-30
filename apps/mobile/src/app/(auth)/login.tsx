import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { useState } from "react";
import { TextInput, View } from "react-native";
import { PrimaryButton, Rise, Tx } from "@/components/ui";
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
        <Tx variant="hero" weight="extrabold" color="#fff" style={{ marginTop: 16, fontSize: 34, lineHeight: 40 }}>
          Adi-Vritti
        </Tx>
        <Tx variant="body" weight="medium" color="#C7D2FE" style={{ marginTop: 4 }}>
          One student. One identity. Five schemes.
        </Tx>
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
          <Tx variant="caption" weight="extrabold">
            Mobile number
          </Tx>
          <View
            style={{
              marginTop: 8,
              flexDirection: "row",
              alignItems: "center",
              backgroundColor: "#F1F5F9",
              borderRadius: 16,
              borderWidth: 1.5,
              borderColor: phone.length === 10 ? "#10B981" : colors.border,
              paddingHorizontal: 14,
            }}
          >
            <Tx variant="body" weight="bold">
              +91
            </Tx>
            <View style={{ width: 1, height: 24, backgroundColor: colors.border, marginHorizontal: 10 }} />
            <TextInput
              value={phone}
              onChangeText={(t) => setPhone(t.replace(/[^0-9]/g, "").slice(0, 10))}
              placeholder="98765 43210"
              keyboardType="phone-pad"
              maxLength={10}
              style={{
                flex: 1,
                fontSize: 17,
                fontFamily: "PlusJakartaSans_600SemiBold",
                paddingVertical: 14,
                color: colors.ink,
              }}
            />
            {phone.length === 10 ? <Ionicons name="checkmark-circle" size={20} color="#047857" /> : null}
          </View>
          <View style={{ marginTop: 14 }}>
            <PrimaryButton amber title="Send OTP" icon="chatbox" onPress={() => router.replace("/(tabs)")} />
          </View>
          <Tx variant="tiny" color={colors.muted} style={{ marginTop: 12, textAlign: "center" }}>
            Demo build — any 10-digit number works. OTP arrives on SMS in production.
          </Tx>
        </View>
      </Rise>
    </LinearGradient>
  );
}
