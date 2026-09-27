"use client";

import * as React from "react";
import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export function Sheet({ open, onClose, title, subtitle, children }: SheetProps) {
  React.useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    if (open) window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open ? (
        <>
          <motion.div
            aria-hidden
            className="fixed inset-0 z-50 bg-slate-950/40 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.aside
            role="dialog"
            aria-modal="true"
            aria-label={title}
            className={cn(
              "fixed right-0 top-0 z-50 flex h-full w-full max-w-md flex-col",
              "border-l border-[var(--border)] bg-white/80 backdrop-blur-2xl dark:bg-slate-900/80"
            )}
            initial={{ x: 420, opacity: 0.5 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 420, opacity: 0 }}
            transition={{ type: "spring", damping: 30, stiffness: 300 }}
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--border)] p-5">
              <div>
                <h2 className="font-display text-lg font-bold">{title}</h2>
                {subtitle ? <p className="text-sm text-[var(--muted-foreground)]">{subtitle}</p> : null}
              </div>
              <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close panel">
                <X size={18} />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto p-5">{children}</div>
          </motion.aside>
        </>
      ) : null}
    </AnimatePresence>
  );
}

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  onConfirm?: () => void;
  confirmLabel?: string;
}

export function Dialog({ open, onClose, title, children, onConfirm, confirmLabel = "Confirm" }: DialogProps) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div aria-hidden className="absolute inset-0 bg-slate-950/50 backdrop-blur-sm" onClick={onClose} />
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        className="glass-card relative w-full max-w-md p-6"
      >
        <h2 className="font-display text-lg font-bold">{title}</h2>
        <div className="mt-3 text-sm">{children}</div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          {onConfirm ? <Button onClick={onConfirm}>{confirmLabel}</Button> : null}
        </div>
      </motion.div>
    </div>
  );
}
