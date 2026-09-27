import { Link } from "expo-router";
import { Text, TextInput, View } from "react-native";

export default function LoginScreen() {
  return (
    <View style={{ flex: 1, backgroundColor: "#312E81", padding: 24, justifyContent: "center" }}>
      <Text style={{ fontSize: 30, fontWeight: "800", color: "#fff" }}>Adi-Vritti</Text>
      <Text style={{ color: "#C7D2FE" }}>One student. One identity. Five schemes.</Text>
      <TextInput placeholder="+91 mobile number" keyboardType="phone-pad" style={{ marginTop: 18, backgroundColor: "#fff", borderRadius: 12, padding: 12 }} />
      <Link href="/(tabs)" style={{ marginTop: 12, backgroundColor: "#F59E0B", borderRadius: 999, padding: 12, textAlign: "center", fontWeight: "700" }}>
        Send OTP
      </Link>
    </View>
  );
}
