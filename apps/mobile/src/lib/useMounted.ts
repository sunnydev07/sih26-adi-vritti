import { useEffect, useRef, type MutableRefObject } from "react";

/**
 * False once the component unmounts. Fire-and-forget timers (refresh
 * spinners, staged-sync delays) must check it before setState: without the
 * guard, navigating away mid-delay warns and can stick a spinner on.
 */
export function useMounted(): MutableRefObject<boolean> {
  const mounted = useRef(true);
  useEffect(() => () => {
    mounted.current = false;
  }, []);
  return mounted;
}
