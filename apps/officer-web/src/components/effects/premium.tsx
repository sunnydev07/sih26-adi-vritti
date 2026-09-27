"use client";

import * as React from "react";
import { motion, useReducedMotion } from "framer-motion";
import { cn } from "@/lib/utils";

/** Magic-UI style animated counter. Counts up on mount. */
export function NumberTicker({ value, format }: { value: number; format: (n: number) => string }) {
  const reduce = useReducedMotion();
  const [display, setDisplay] = React.useState(0);
  React.useEffect(() => {
    if (reduce) {
      setDisplay(value);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const dur = 1200;
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / dur);
      setDisplay(value * (1 - Math.pow(1 - p, 3)));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, reduce]);
  return <span className="font-mono tabular-nums">{format(display)}</span>;
}

/** Animated circular progress ring (Framer Motion). */
export function ProgressRing({
  pct,
  size = 84,
  label,
}: {
  pct: number;
  size?: number;
  label: string;
}) {
  const r = (size - 10) / 2;
  const c = 2 * Math.PI * r;
  const color = pct >= 80 ? "#10b981" : pct >= 50 ? "#f59e0b" : "#f43f5e";
  return (
    <div className="flex flex-col items-center gap-1" role="img" aria-label={`${label}: ${Math.round(pct)} percent`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={8} className="stroke-slate-200 dark:stroke-slate-700" />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={color}
          strokeWidth={8}
          strokeLinecap="round"
          strokeDasharray={c}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c - (c * Math.min(100, Math.max(0, pct))) / 100 }}
          transition={{ duration: 1.1, ease: "easeOut" }}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
        <text x="50%" y="50%" dominantBaseline="middle" textAnchor="middle" className="fill-[var(--foreground)] text-sm font-bold">
          {Math.round(pct)}%
        </text>
      </svg>
      <span className="text-xs text-[var(--muted-foreground)]">{label}</span>
    </div>
  );
}

/** Glassmorphic card with spotlight hover (Aceternity-style, dependency-free). */
export function GlassCard({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const ref = React.useRef<HTMLDivElement>(null);
  const [pos, setPos] = React.useState({ x: -400, y: -400 });
  return (
    <div
      ref={ref}
      onMouseMove={(e) => {
        const rect = ref.current?.getBoundingClientRect();
        if (rect) setPos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
      }}
      onMouseLeave={() => setPos({ x: -400, y: -400 })}
      className={cn("glass-card glass-card-hover relative overflow-hidden p-5", className)}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 transition-opacity"
        style={{ background: `radial-gradient(320px circle at ${pos.x}px ${pos.y}px, rgba(99,102,241,0.14), transparent 65%)` }}
      />
      <div className="relative">{children}</div>
    </div>
  );
}

/** Shimmer CTA button (Magic-UI Shimmer Button style). */
export function ShimmerButton({
  children,
  onClick,
  tone = "green",
}: {
  children: React.ReactNode;
  onClick?: () => void;
  tone?: "green" | "indigo" | "amber" | "rose";
}) {
  const tones: Record<string, string> = {
    green: "bg-emerald-600 hover:bg-emerald-500",
    indigo: "bg-[#312E81] hover:bg-[#4338CA]",
    amber: "bg-amber-600 hover:bg-amber-500",
    rose: "bg-rose-600 hover:bg-rose-500",
  };
  return (
    <button
      onClick={onClick}
      className={cn("shimmer rounded-full px-5 py-2.5 text-sm font-semibold text-white shadow transition-transform hover:scale-[1.02]", tones[tone])}
    >
      {children}
    </button>
  );
}

/** Subtle particle background for the login page (Aceternity-style, canvas-free). */
export function ParticleBackground() {
  const dots = React.useMemo(
    () =>
      Array.from({ length: 42 }, (_, i) => ({
        id: i,
        left: (i * 37) % 100,
        top: (i * 53) % 100,
        size: 2 + ((i * 7) % 4),
        delay: (i % 9) * 0.7,
        indigo: i % 3 !== 0,
      })),
    []
  );
  return (
    <div aria-hidden className="absolute inset-0 overflow-hidden bg-gradient-to-br from-[#312E81] via-[#1E1B4B] to-[#0F172A]">
      {dots.map((d) => (
        <motion.span
          key={d.id}
          className="absolute rounded-full"
          style={{
            left: `${d.left}%`,
            top: `${d.top}%`,
            width: d.size,
            height: d.size,
            background: d.indigo ? "rgba(165,180,252,0.7)" : "rgba(251,191,36,0.7)",
          }}
          animate={{ y: [0, -18, 0], opacity: [0.25, 0.9, 0.25] }}
          transition={{ duration: 6 + d.delay, repeat: Infinity, delay: d.delay, ease: "easeInOut" }}
        />
      ))}
    </div>
  );
}
