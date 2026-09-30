import { Ionicons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import type { ComponentProps } from "react";
import { colors } from "@/lib/theme";

type IconName = ComponentProps<typeof Ionicons>["name"];

const TABS: { name: string; title: string; icon: IconName; activeIcon: IconName }[] = [
  { name: "index", title: "Home", icon: "home-outline", activeIcon: "home" },
  { name: "wallet", title: "Wallet", icon: "wallet-outline", activeIcon: "wallet" },
  { name: "chat", title: "Ask Adi", icon: "chatbubbles-outline", activeIcon: "chatbubbles" },
  { name: "profile", title: "Profile", icon: "person-outline", activeIcon: "person" },
];

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primaryStrong,
        tabBarInactiveTintColor: "#94A3B8",
        tabBarHideOnKeyboard: true,
        tabBarLabelStyle: { fontSize: 11, fontWeight: "800", marginTop: 2 },
        tabBarStyle: {
          position: "absolute",
          marginHorizontal: 16,
          marginBottom: 18,
          borderRadius: 24,
          height: 70,
          paddingTop: 10,
          paddingBottom: 10,
          backgroundColor: "#fff",
          borderWidth: 1,
          borderColor: colors.border,
          borderTopWidth: 1,
          elevation: 10,
          shadowColor: "#312E81",
          shadowOpacity: 0.16,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: 8 },
        },
      }}
    >
      {TABS.map((t) => (
        <Tabs.Screen
          key={t.name}
          name={t.name}
          options={{
            title: t.title,
            tabBarIcon: ({ color, size, focused }) => (
              <Ionicons name={focused ? t.activeIcon : t.icon} size={size} color={color} />
            ),
          }}
        />
      ))}
    </Tabs>
  );
}
