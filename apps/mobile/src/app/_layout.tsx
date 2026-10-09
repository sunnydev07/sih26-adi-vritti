import {
  PlusJakartaSans_400Regular,
  PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold,
  PlusJakartaSans_700Bold,
  PlusJakartaSans_800ExtraBold,
  useFonts,
} from "@expo-google-fonts/plus-jakarta-sans";
import * as SplashScreen from "expo-splash-screen";
import { useEffect, type ReactNode } from "react";
import { Slot, useRouter, useSegments } from "expo-router";
import { LangProvider } from "@/lib/lang";
import { useSession } from "@/lib/session";

void SplashScreen.preventAutoHideAsync();

/** Loads Plus Jakarta Sans (same family as officer-web) before first paint. */
export default function RootLayout() {
  const [loaded, error] = useFonts({
    PlusJakartaSans_400Regular,
    PlusJakartaSans_500Medium,
    PlusJakartaSans_600SemiBold,
    PlusJakartaSans_700Bold,
    PlusJakartaSans_800ExtraBold,
  });

  useEffect(() => {
    if (loaded || error) {
      void SplashScreen.hideAsync();
    }
  }, [loaded, error]);

  if (!loaded && !error) return null;
  return (
    <LangProvider>
      <SessionGate>
        <Slot />
      </SessionGate>
    </LangProvider>
  );
}

/**
 * Cold starts and deep links land inside the tabs today with zero credentials:
 * the login screen was reachable but never enforced. Any gated route without
 * a session redirects to /login instead.
 */
function SessionGate({ children }: { children: ReactNode }) {
  const session = useSession();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (!session && segments[0] !== "(auth)") {
      router.replace("/login");
    }
  }, [session, segments, router]);

  return <>{children}</>;
}
