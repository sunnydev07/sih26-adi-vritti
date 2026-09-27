import type { Metadata } from "next";
import "@fontsource-variable/plus-jakarta-sans";
import "@fontsource-variable/dm-sans";
import "@fontsource/jetbrains-mono/400.css";
import "@fontsource/jetbrains-mono/600.css";
import "./globals.css";
import { Providers } from "@/components/providers";
import { Toaster } from "@/components/ui/controls";

export const metadata: Metadata = {
  title: "Adi-Vritti · Officer Console",
  description: "Unified scholarship console for tribal students — MoTA SIH 2026 PS 26238",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning className="h-full">
      <body className="flex min-h-full flex-col antialiased">
        <Providers>
          {children}
          <Toaster />
        </Providers>
      </body>
    </html>
  );
}
