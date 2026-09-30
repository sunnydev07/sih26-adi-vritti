import { Pressable, Text } from "react-native";
import { useRouter } from "expo-router";
import { colors, radius, spacing } from "@/lib/theme";

/** Floating "Ask Adi" button for Home/Wallet. Jumps to the JAGO+ tab. */
export default function ChatFab() {
  const router = useRouter();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Ask Adi — help and questions"
      onPress={() => router.push("/chat")}
      style={{
        position: "absolute",
        right: 16,
        bottom: 24,
        width: 56,
        height: 56,
        minWidth: spacing.minTouch,
        minHeight: spacing.minTouch,
        borderRadius: radius.pill,
        backgroundColor: colors.primary,
        alignItems: "center",
        justifyContent: "center",
        elevation: 4,
        shadowColor: "#000",
        shadowOpacity: 0.2,
        shadowRadius: 6,
        shadowOffset: { width: 0, height: 3 },
      }}
    >
      <Text style={{ color: "#fff", fontSize: 22, fontWeight: "800" }}>?</Text>
    </Pressable>
  );
}
