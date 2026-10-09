import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useState } from "react";
import type { ReactNode } from "react";import { ScrollView, Switch, View } from "react-native";
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
import { useLang } from "@/lib/lang";
import { signOut } from "@/lib/session";
import { colors } from "@/lib/theme";
import { useMounted } from "@/lib/useMounted";

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
  const { lang, t, toggle } = useLang();
  const [sync, setSync] = useState<SyncState>("idle");
  const [offlineFiles, setOfflineFiles] = useState(true);
  const mounted = useMounted();

  function syncNow() {
    if (sync === "busy") return;
    setSync("busy");
    setTimeout(() => {
      if (!mounted.current) return;
      setSync("done");
      successTap();
    }, 1300);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.page }}>
      <ScreenHeader
        eyebrow={t.profileEyebrow}
        title="Sunita Meena"
        subtitle="Scholar ID ··A91F04C2 · Mandla, MP"
        avatar="S"
      />
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, paddingBottom: 128 }}>
        <Rise delay={40}>
          <Card>
            <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
              <Eyebrow>{t.profileIdentity}</Eyebrow>
              <Pill tone="ok" label={t.profileVerified} icon="shield-checkmark" />
            </View>
            <Row icon="lock-closed" title={`Aadhaar ${maskAadhaar("XXXX4821")}`} sub={t.profileAadhaarSub} />
            <Row icon="finger-print" title="USID ··A91F04C2" sub={t.profileUsidSub} />
          </Card>
        </Rise>

        <Rise delay={120}>
          <Card style={{ marginTop: 12 }}>
            <Eyebrow>{t.profileAudit}</Eyebrow>
            <Row icon="eye" title="District Nodal Officer" sub="Viewed file APP-90412 · 12 Aug" />
            <Row icon="card" title="Payment system (PFMS)" sub="Checked bank details · 18 Sep" />
          </Card>
        </Rise>

        <Rise delay={200}>
          <Card style={{ marginTop: 12 }}>
            <Eyebrow>{t.profileSettings}</Eyebrow>
            <Row
              icon="language"
              title={t.profileLanguage}
              sub={lang === "en" ? t.profileLangSubEn : t.profileLangSubHi}
              onPress={() => {
                toggle();
                successTap();
              }}
              right={
                <Tx variant="body" weight="extrabold" color={colors.primaryStrong}>
                  {lang === "en" ? "EN" : "हिं"}
                </Tx>
              }
            />
            <Row
              icon="sync"
              title={t.profileSync}
              sub={sync === "done" ? t.profileSyncDone : sync === "busy" ? t.profileSyncBusy : t.profileSyncIdle}
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
              title={t.profileOffline}
              sub={t.profileOfflineSub}
              right={
                <Switch
                  value={offlineFiles}
                  onValueChange={setOfflineFiles}
                  trackColor={{ true: colors.primaryStrong, false: "#CBD5E1" }}
                />
              }
            />
            <Row icon="chatbubbles" title={t.profileHelp} sub="Hindi, English, Santali, Gondi" onPress={() => router.push("/chat")} />
          </Card>
        </Rise>

        <Rise delay={280}>
          <PressableScale
            onPress={() => {
              // Demo session is in-memory: clearing it is the whole sign-out.
              // Navigating alone would leave it usable.
              signOut();
              router.replace("/login");
            }}
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
              {t.profileSignOut}
            </Tx>
          </PressableScale>
        </Rise>
      </ScrollView>
    </View>
  );
}
