"use client";

import { MotionConfig } from "framer-motion";
import { ThemeProvider } from "next-themes";
import * as React from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      {/* WCAG 2.3.3: when the OS asks for reduced motion, every framer-motion
          stagger/tilt/rise renders in its final state instead of animating. */}
      <MotionConfig reducedMotion="user">{children}</MotionConfig>
    </ThemeProvider>
  );
}
