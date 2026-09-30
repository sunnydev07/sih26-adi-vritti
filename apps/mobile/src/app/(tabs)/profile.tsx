import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useState } from "react";
import type { ReactNode } from "react";
import { ScrollView, Switch, View } from "react-native";
import {
  Card,
  Eyebrow,
  Pill,
  PressableScale,
  Rise,
  ScreenHeader,
  Tx,
  type IconName,
} from "@/components/ui";
import { successTap } from "@/lib/feedback";
import { maskAadhaar } from "@/lib/format";
import { colors } from "@/lib/theme";

type SyncState = "idle" | "busy" | "done";

function Row({
  icon,
  title,
  sub,
  onPress,
  right,
}: {
  icon: IconName;
  title: string;
  sub?: string;
  onPress?: () => void;
  right?: ReactNode;
}) {
  return (
    <PressableScale
      onPress={onPress}
      accessibilityLabel={title}
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        paddingVertical: 12,
        borderTopWidth: 1,
        borderTopColor: colors.border,
      }}
    >
      <View
        style={{
          width: 36,
          height: 36,
          borderRadius: 18,
          backgroundColor: "#EEF2FF",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Ionicons name={icon} size={18} color={colors.primaryStrong} />
      </View>
      <View style={{ flex: 1 }}>
        <Tx variant="body" weight="bold">
          {title}
        </Tx>
        {sub ? (
          <Tx variant="caption" color={colors.muted} style={{ marginTop: 1 }}>
            {sub}
          </Tx>
        ) : null}
      </View>
      {right ?? <Ionicons name="chevron-forward" size={17} color={colors.muted} />}
    </PressableScale>
  );
}

export default function ProfileScreen() {
  const router = useRouter();
  const [sync, setSync] = useState<SyncState>("idle");
  const [offlineFiles, setOfflineFiles] = useState(true);

  function syncNow() {
    if (sync === "busy") return;
    setSync("busy");
    setTimeout(() => {
      setSync("done");
      successTap();
    }, 1300);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <ScreenHeader
        eyebrow="Student profile"
        title="Sunita Meena"
        subtitle="Scholar ID ··A91F04C2 · Mandla, MP"
        avatar="S"
      />
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, paddingBottom: 128 }}>
        <Rise delay={40}>
          <Card>
            <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
              <Eyebrow>My identity</Eyebrow>
              <Pill tone="ok" label="Verified" icon="shield-checkmark" />
            </View>
            <Row icon="lock-closed" title={`Aadhaar ${maskAadhaar("XXXX4821")}`} sub="Never shown in full — not even to officers" />
            <Row icon="finger-print" title="USID ··A91F04C2" sub="One ID across all 5 schemes" />
          </Card>
        </Rise>

        <Rise delay={120}>
          <Card style={{ marginTop: 12 }}>
            <Eyebrow>Who looked at my data</Eyebrow>
            <Row icon="eye" title="District Nodal Officer" sub="Viewed file APP-90412 · 12 Aug" />
            <Row icon="card" title="Payment system (PFMS)" sub="Checked bank details · 18 Sep" />
          </Card>
        </Rise>

        <Rise delay={200}>
          <Card style={{ marginTop: 12 }}>
            <Eyebrow>Settings</Eyebrow>
            <Row
              icon="sync"
              title="Sync now"
              sub={sync === "done" ? "Demo — nothing was synced (sync is not wired yet)" : sync === "busy" ? "Syncing…" : "Pull the latest file status"}
              onPress={syncNow}
              right={
                sync === "busy" ? (
                  <Ionicons name="sync" size={17} color={colors.primaryStrong} />
                ) : sync === "done" ? (
                  <Ionicons name="checkmark-circle" size={19} color="#047857" />
                ) : undefined
              }
            />
            <Row
              icon="download"
              title="Keep files offline"
              sub="Read status without internet"
              right={
                <Switch
                  value={offlineFiles}
                  onValueChange={setOfflineFiles}
                  trackColor={{ true: colors.primaryStrong, false: "#CBD5E1" }}
                />
              }
            />
            <Row icon="chatbubbles" title="Help & Ask Adi" sub="Hindi, English, Santali, Gondi" onPress={() => router.push("/chat")} />
          </Card>
        </Rise>

        <Rise delay={280}>
          <PressableScale
            onPress={() => router.replace("/login")}
            haptic
            accessibilityLabel="Sign out"
            style={{
              marginTop: 16,
              borderRadius: 999,
              paddingVertical: 13,
              minHeight: 48,
              flexDirection: "row",
              alignItems: "center",
              justifyContent: "center",
              gap: 8,
              backgroundColor: "#FFF1F2",
              borderWidth: 1.5,
              borderColor: "#FECDD3",
            }}
          >
            <Ionicons name="log-out" size={18} color="#BE123C" />
            <Tx variant="body" weight="extrabold" color="#BE123C">
              Sign out
            </Tx>
          </PressableScale>
        </Rise>
      </ScrollView>
    </View>
  );
}
