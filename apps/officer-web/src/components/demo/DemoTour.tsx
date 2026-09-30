"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { cn } from "@/lib/utils";

interface DemoStep {
  route: string;
  anchor: string;
  title: string;
  text: string;
}

/** Mirrors docs/specs/demo-path.md in 3 judge-sized beats. */
const STEPS: DemoStep[] = [
  {
    route: "/dashboard/coverage-gap",
    anchor: "coverage-regions",
    title: "Beat 1 · The gap",
    text: "Crores enrolled, lakhs missing from portals. Tap a state to drill down to the school.",
  },
  {
    route: "/dashboard/exceptions",
    anchor: "queue-decide",
    title: "Beats 2–3 · Decide fast",
    text: "Most overdue first. Files marked Ready to approve clear in one tap.",
  },
  {
    route: "/dashboard/disbursements",
    anchor: "money-fix",
    title: "Beat 4 · Fix money",
    text: "Every stuck payment shows its fix. Then tap Ask Adi (?) and ask why a payment is pending.",
  },
];

const FLAG = "adi-demo";
const STEP_KEY = "adi-demo-step";

function TourInner() {
  const pathname = usePathname();
  const router = useRouter();
  const search = useSearchParams();
  // Lazy init reads ?demo=1 / localStorage once, client-side only (this tree
  // renders under Suspense, so the server prerenders the fallback instead).
  // No setState-in-effect: activation is derived, not synchronized.
  const [active, setActive] = React.useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    if (new URLSearchParams(window.location.search).get("demo") === "1") {
      window.localStorage.setItem(FLAG, "1");
      window.localStorage.setItem(STEP_KEY, "0");
      return true;
    }
    return window.localStorage.getItem(FLAG) === "1";
  });
  const [step, setStep] = React.useState<number>(() => {
    if (typeof window === "undefined") return 0;
    return Number(window.localStorage.getItem(STEP_KEY) ?? 0) || 0;
  });
  const anchorRef = React.useRef<Element | null>(null);

  const current = STEPS[Math.min(step, STEPS.length - 1)];

  React.useEffect(() => {
    if (anchorRef.current) {
      anchorRef.current.classList.remove("demo-pulse");
      anchorRef.current = null;
    }
    if (!active || !current || pathname !== current.route) return;
    const el = document.getElementById(current.anchor);
    if (el) {
      anchorRef.current = el;
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      el.classList.add("demo-pulse");
    }
    return () => {
      anchorRef.current?.classList.remove("demo-pulse");
      anchorRef.current = null;
    };
  }, [active, current, pathname]);

  function exit() {
    window.localStorage.removeItem(FLAG);
    window.localStorage.removeItem(STEP_KEY);
    setActive(false);
    if (search.get("demo") === "1") router.replace(pathname);
  }

  function next() {
    if (step >= STEPS.length - 1) {
      exit();
      return;
    }
    const n = step + 1;
    window.localStorage.setItem(STEP_KEY, String(n));
    setStep(n);
  }

  if (!active || !current) return null;
  const onRoute = pathname === current.route;

  return (
    <section
      aria-label={`Demo tour, step ${step + 1} of ${STEPS.length}`}
      className="fixed inset-x-3 bottom-[5.5rem] z-40 rounded-3xl border-2 border-[#6366F1] bg-[var(--card)] p-4 shadow-2xl sm:inset-x-auto sm:bottom-6 sm:left-6 sm:w-[380px]"
    >
      <p className="text-[11px] font-bold uppercase tracking-widest text-[#6366F1]">
        Judge demo · {step + 1} of {STEPS.length}
      </p>
      <h2 className="mt-0.5 font-display text-base font-bold">{current.title}</h2>
      <p className="mt-1 text-sm text-[var(--muted-foreground)]">{current.text}</p>
      <div className="mt-1 flex gap-1.5" aria-hidden>
        {STEPS.map((s, i) => (
          <span
            key={s.route}
            className={cn("h-1.5 flex-1 rounded-full", i <= step ? "bg-[#6366F1]" : "bg-slate-200 dark:bg-slate-700")}
          />
        ))}
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {!onRoute ? (
          <button
            onClick={() => router.push(current.route)}
            className="min-touch rounded-full bg-[var(--primary)] px-4 text-xs font-bold text-white"
          >
            Take me there →
          </button>
        ) : null}
        <button
          onClick={next}
          className="min-touch rounded-full border border-[var(--border)] px-4 text-xs font-semibold"
        >
          {step >= STEPS.length - 1 ? "Finish tour" : onRoute ? "Next beat →" : "Skip →"}
        </button>
        <button
          onClick={exit}
          className="min-touch rounded-full px-3 text-xs font-medium text-[var(--muted-foreground)] hover:underline"
        >
          Exit demo
        </button>
      </div>
    </section>
  );
}

/** ?demo=1 starts a 3-beat guided tour for judges. Suspended for prerender. */
export function DemoTour() {
  return (
    <React.Suspense fallback={null}>
      <TourInner />
    </React.Suspense>
  );
}
