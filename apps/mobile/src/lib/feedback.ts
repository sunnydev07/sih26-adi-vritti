import * as Haptics from "expo-haptics";

/** Tiny haptic helpers — safe no-ops on platforms without a haptics engine. */
export function lightTap(): void {
  try {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  } catch {
    /* ignore */
  }
}

export function successTap(): void {
  try {
    void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  } catch {
    /* ignore */
  }
}
