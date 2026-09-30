import { Ionicons } from "@expo/vector-icons";
import { LinearGradient } from "expo-linear-gradient";
import type { ComponentProps, ReactNode } from "react";
import { useEffect, useState } from "react";
import {
  Pressable,
  Text,
  View,
  type GestureResponderEvent,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from "react-native";
import Animated, {
  Easing,
  FadeInDown,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { lightTap } from "@/lib/feedback";
import { colors, radius, toneColors, type Tone } from "@/lib/theme";

export type IconName = ComponentProps<typeof Ionicons>["name"];

/* ---------------------------------- motion --------------------------------- */

const SPRING = { damping: 16, stiffness: 380 } as const;

/** Staggered entrance wrapper — stack several with rising `delay` for a wave. */
export function Rise({
  children,
  delay = 0,
  style,
}: {
  children: ReactNode;
  delay?: number;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Animated.View
      entering={FadeInDown.duration(480).delay(delay).springify().damping(17)}
      style={style}
    >
      {children}
    </Animated.View>
  );
}

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Every tappable surface: springy 0.96 squash on press-in, bounce back out. */
export function PressableScale({
  children,
  onPress,
  style,
  haptic = false,
  accessibilityLabel,
  accessibilityRole = "button",
}: {
  children: ReactNode;
  onPress?: (e: GestureResponderEvent) => void;
  style?: StyleProp<ViewStyle>;
  haptic?: boolean;
  accessibilityLabel?: string;
  accessibilityRole?: "button" | "link";
}) {
  const s = useSharedValue(1);
  const anim = useAnimatedStyle(() => ({ transform: [{ scale: s.value }] }));
  return (
    <AnimatedPressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole={accessibilityRole}
      onPress={(e) => {
        if (haptic) lightTap();
        onPress?.(e);
      }}
      onPressIn={() => {
        s.value = withSpring(0.96, SPRING);
      }}
      onPressOut={() => {
        s.value = withSpring(1, SPRING);
      }}
      style={[anim, style]}
    >
      {children}
    </AnimatedPressable>
  );
}

/* ---------------------------------- chrome --------------------------------- */

export const shadow = {
  card: {
    shadowColor: "#312E81",
    shadowOpacity: 0.08,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  pop: {
    shadowColor: "#312E81",
    shadowOpacity: 0.18,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 7,
  },
} as const;

export function Card({
  children,
  style,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <View
      style={[
        {
          backgroundColor: colors.surface,
          borderRadius: radius.card,
          padding: 16,
          borderWidth: 1,
          borderColor: colors.border,
        },
        shadow.card,
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function Pill({ tone, label, icon }: { tone: Tone; label: string; icon?: IconName }) {
  const t = toneColors(tone);
  return (
    <View
      style={{
        flexDirection: "row",
        alignItems: "center",
        alignSelf: "flex-start",
        gap: 5,
        backgroundColor: t.bg,
        borderRadius: radius.pill,
        paddingHorizontal: 10,
        paddingVertical: 5,
      }}
    >
      {icon ? <Ionicons name={icon} size={12} color={t.ink} /> : null}
      <Text style={{ fontSize: 11, fontWeight: "800", color: t.ink }}>{label}</Text>
    </View>
  );
}

export function Eyebrow({ children, color }: { children: ReactNode; color?: string }) {
  return (
    <Text
      style={{
        fontSize: 11,
        fontWeight: "800",
        letterSpacing: 1.6,
        textTransform: "uppercase",
        color: color ?? colors.muted,
      }}
    >
      {children}
    </Text>
  );
}

export function SectionTitle({
  title,
  action,
  onAction,
}: {
  title: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <View
      style={{
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        marginTop: 22,
        marginBottom: 10,
      }}
    >
      <Text style={{ fontSize: 16, fontWeight: "800", color: colors.ink }}>{title}</Text>
      {action ? (
        <Pressable onPress={onAction} hitSlop={8}>
          <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primaryStrong }}>{action}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/** Gradient screen header with decorative light circles + avatar/icon medallion. */
export function ScreenHeader({
  eyebrow,
  title,
  subtitle,
  avatar,
  icon,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  avatar?: string;
  icon?: IconName;
}) {
  return (
    <LinearGradient
      colors={[colors.primary, "#1E1B4B"]}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{
        paddingTop: 60,
        paddingBottom: 26,
        paddingHorizontal: 20,
        borderBottomLeftRadius: 28,
        borderBottomRightRadius: 28,
        overflow: "hidden",
      }}
    >
      <View
        style={{
          position: "absolute",
          width: 190,
          height: 190,
          borderRadius: 95,
          backgroundColor: "rgba(255,255,255,0.07)",
          top: -70,
          right: -50,
        }}
      />
      <View
        style={{
          position: "absolute",
          width: 120,
          height: 120,
          borderRadius: 60,
          backgroundColor: "rgba(245,158,11,0.16)",
          bottom: -60,
          left: 40,
        }}
      />
      <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
        <View style={{ flex: 1, paddingRight: 12 }}>
          <Eyebrow color="#A5B4FC">{eyebrow}</Eyebrow>
          <Text style={{ marginTop: 4, fontSize: 24, fontWeight: "800", color: "#fff" }}>{title}</Text>
          <Text style={{ marginTop: 3, fontSize: 13, color: "#C7D2FE" }}>{subtitle}</Text>
        </View>
        <View
          style={{
            width: 54,
            height: 54,
            borderRadius: 27,
            backgroundColor: "rgba(255,255,255,0.16)",
            borderWidth: 1.5,
            borderColor: "rgba(255,255,255,0.35)",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          {icon ? (
            <Ionicons name={icon} size={26} color="#fff" />
          ) : (
            <Text style={{ fontSize: 22, fontWeight: "800", color: "#fff" }}>{avatar ?? "•"}</Text>
          )}
        </View>
      </View>
    </LinearGradient>
  );
}

/* --------------------------------- buttons --------------------------------- */

export function PrimaryButton({
  title,
  icon,
  onPress,
  amber = false,
}: {
  title: string;
  icon?: IconName;
  onPress?: () => void;
  amber?: boolean;
}) {
  return (
    <PressableScale onPress={onPress} haptic accessibilityLabel={title}>
      <LinearGradient
        colors={amber ? ["#FBBF24", "#F59E0B"] : [colors.primaryStrong, colors.primary]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 0 }}
        style={{
          borderRadius: radius.pill,
          paddingVertical: 13,
          paddingHorizontal: 18,
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "center",
          gap: 8,
          minHeight: 48,
        }}
      >
        {icon ? <Ionicons name={icon} size={18} color={amber ? "#1E1B4B" : "#fff"} /> : null}
        <Text style={{ color: amber ? "#1E1B4B" : "#fff", fontWeight: "800", fontSize: 15 }}>{title}</Text>
      </LinearGradient>
    </PressableScale>
  );
}

export function GhostButton({
  title,
  icon,
  onPress,
}: {
  title: string;
  icon?: IconName;
  onPress?: () => void;
}) {
  return (
    <PressableScale onPress={onPress} haptic accessibilityLabel={title}>
      <View
        style={{
          borderRadius: radius.pill,
          paddingVertical: 12,
          paddingHorizontal: 18,
          flexDirection: "row",
          alignItems: "center",
          justifyContent: "center",
          gap: 8,
          minHeight: 48,
          backgroundColor: colors.surface,
          borderWidth: 1.5,
          borderColor: colors.primarySoft,
        }}
      >
        {icon ? <Ionicons name={icon} size={18} color={colors.primaryStrong} /> : null}
        <Text style={{ color: colors.primaryStrong, fontWeight: "800", fontSize: 15 }}>{title}</Text>
      </View>
    </PressableScale>
  );
}

/* --------------------------------- widgets --------------------------------- */

/** Smooth fill bar: measures its track, then eases the fill to `progress`. */
export function AnimatedBar({
  progress,
  color,
  height = 8,
  delay = 250,
}: {
  progress: number;
  color: string;
  height?: number;
  delay?: number;
}) {
  const [track, setTrack] = useState(0);
  const p = useSharedValue(0);
  const target = Math.max(0, Math.min(1, progress));
  useEffect(() => {
    p.value = withDelay(delay, withTiming(target, { duration: 950, easing: Easing.out(Easing.cubic) }));
  }, [target, delay, p]);
  const fill = useAnimatedStyle(() => ({ width: track * p.value }));
  return (
    <View
      onLayout={(e) => setTrack(e.nativeEvent.layout.width)}
      style={{ height, borderRadius: 999, backgroundColor: "#E8ECF4", overflow: "hidden" }}
    >
      <Animated.View style={[{ height: "100%", borderRadius: 999, backgroundColor: color }, fill]} />
    </View>
  );
}

/** Vertical stepper: done ✓ → now (ring) → todo. */
export function Stepper({
  steps,
}: {
  steps: { label: string; sub?: string; state: "done" | "now" | "todo" }[];
}) {
  return (
    <View>
      {steps.map((s, i) => {
        const last = i === steps.length - 1;
        const dotBg = s.state === "done" ? "#10B981" : s.state === "now" ? colors.primaryStrong : "#E2E8F0";
        return (
          <View key={`${s.label}-${i}`} style={{ flexDirection: "row" }}>
            <View style={{ alignItems: "center", width: 26 }}>
              <View
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 13,
                  backgroundColor: s.state === "now" ? "#EEF2FF" : dotBg,
                  borderWidth: s.state === "now" ? 2 : 0,
                  borderColor: s.state === "now" ? colors.primaryStrong : "transparent",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                {s.state === "done" ? (
                  <Ionicons name="checkmark" size={15} color="#fff" />
                ) : s.state === "now" ? (
                  <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: colors.primaryStrong }} />
                ) : (
                  <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: "#94A3B8" }} />
                )}
              </View>
              {last ? null : (
                <View
                  style={{
                    width: 2,
                    flex: 1,
                    minHeight: 14,
                    backgroundColor: s.state === "todo" ? "#E2E8F0" : "#A5B4FC",
                    borderRadius: 1,
                  }}
                />
              )}
            </View>
            <View style={{ marginLeft: 10, paddingBottom: last ? 0 : 16, flex: 1 }}>
              <Text
                style={{
                  fontSize: 14,
                  fontWeight: s.state === "todo" ? "500" : "800",
                  color: s.state === "todo" ? colors.muted : colors.ink,
                }}
              >
                {s.label}
              </Text>
              {s.sub ? <Text style={{ fontSize: 12, color: colors.muted, marginTop: 2 }}>{s.sub}</Text> : null}
            </View>
          </View>
        );
      })}
    </View>
  );
}

/** Three bouncing dots while Adi composes a reply. */
function TypingDot({ delay }: { delay: number }) {
  const o = useSharedValue(0.3);
  useEffect(() => {
    o.value = withDelay(
      delay,
      withRepeat(withSequence(withTiming(1, { duration: 340 }), withTiming(0.3, { duration: 340 })), -1, false),
    );
  }, [delay, o]);
  const s = useAnimatedStyle(() => ({ opacity: o.value }));
  return <Animated.View style={[{ width: 7, height: 7, borderRadius: 4, backgroundColor: colors.primarySoft }, s]} />;
}

export function TypingDots() {
  return (
    <View style={{ flexDirection: "row", gap: 5, paddingHorizontal: 14, paddingVertical: 12 }}>
      <TypingDot delay={0} />
      <TypingDot delay={130} />
      <TypingDot delay={260} />
    </View>
  );
}

/** "en-IN" date like 2 Aug 2026; falls back to the raw YYYY-MM-DD slice. */
export function inDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
  return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function captionStyle(): TextStyle {
  return { fontSize: 12, color: colors.muted };
}
