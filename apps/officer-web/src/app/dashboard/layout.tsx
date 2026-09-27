"use client";

import { AnimatePresence, motion } from "framer-motion";
import { usePathname } from "next/navigation";
import * as React from "react";
import { Header } from "@/components/dashboard/header";
import { Sidebar } from "@/components/dashboard/sidebar";

const TITLES: Record<string, { title: string; subtitle: string }> = {
  "/dashboard": { title: "Overview", subtitle: "STP scores, queue counts and disbursement health at a glance" },
  "/dashboard/exceptions": { title: "Exception Queue", subtitle: "Files needing manual review, sorted by breach risk" },
  "/dashboard/coverage-gap": { title: "Coverage Gap Map", subtitle: "ST enrolment vs applications — State → District → Block → School" },
  "/dashboard/identity": { title: "Identity Resolution", subtitle: "Adjudicate uncertain USID links — lowest confidence first" },
  "/dashboard/disbursements": { title: "Disbursement Tracker", subtitle: "Sanctions, payments and decoded PFMS failures" },
  "/dashboard/settings": { title: "Settings", subtitle: "Console preferences and integration readiness" },
};

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const meta = TITLES[pathname] ?? TITLES["/dashboard"];
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header title={meta.title} subtitle={meta.subtitle} />
        <AnimatePresence mode="wait">
          <motion.main
            key={pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2, ease: "easeInOut" }}
            className="mx-auto w-full max-w-7xl flex-1 space-y-6 p-4 lg:p-8"
          >
            {children}
          </motion.main>
        </AnimatePresence>
      </div>
    </div>
  );
}
