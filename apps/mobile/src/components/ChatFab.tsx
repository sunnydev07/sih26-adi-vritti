import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { PressableScale, shadow } from "@/components/ui";
import { lightTap } from "@/lib/feedback";
import { colors } from "@/lib/theme";

/** Floating "Ask Adi" button. Sits above the floating tab bar. */
export default function ChatFab() {
  const router = useRouter();
  return (
    <PressableScale
      accessibilityLabel="Ask Adi — help and questions"
      onPress={() => {
        lightTap();
        router.push("/chat");
      }}
      style={[
        {
          position: "absolute",
          right: 18,
          bottom: 102,
          width: 60,
          height: 60,
          borderRadius: 30,
          overflow: "hidden",
        },
        shadow.pop,
      ]}
    >
      <LinearGradient
        colors={[colors.primarySoft, colors.primary]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{ flex: 1, alignItems: "center", justifyContent: "center" }}
      >
        <Ionicons name="chatbubble-ellipses" size={26} color="#fff" />
      </LinearGradient>
    </PressableScale>
  );
}
