"use client";

import { motion } from "framer-motion";
import { useRouter } from "next/navigation";
import * as React from "react";
import { GlassCard, ParticleBackground } from "@/components/effects/premium";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/primitives";

const TAGLINE = "One student. One identity. Five schemes.";

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = React.useState<"phone" | "otp">("phone");
  const [phone, setPhone] = React.useState("");
  const [otp, setOtp] = React.useState("");
  const [typed, setTyped] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [demo, setDemo] = React.useState(false);
  // Whether the server offers demo entry. Nothing sends an SMS in any
  // environment, so without this the OTP screen is a dead end — the button
  // below is the only honest way in during development.
  const [demoAvailable, setDemoAvailable] = React.useState(false);

  React.useEffect(() => {
    fetch("/api/session", { cache: "no-store" })
      .then((r) => r.json() as Promise<{ demo?: boolean }>)
      .then((b) => setDemoAvailable(b.demo === true))
      .catch(() => setDemoAvailable(false));
  }, []);

  React.useEffect(() => {
    let i = 0;
    const t = setInterval(() => {
      i += 1;
      setTyped(TAGLINE.slice(0, i));
      if (i >= TAGLINE.length) clearInterval(t);
    }, 45);
    return () => clearInterval(t);
  }, []);

  function sendOtp(e: React.FormEvent) {
    e.preventDefault();
    if (phone.replace(/\D/g, "").length >= 10) {
      setError(null);
      setStep("otp");
    }
  }

  /**
   * Exchange the credentials for a session at the server.
   *
   * This used to `router.push("/dashboard")` off a length check alone, which granted
   * nothing: the console had no route guard, so the "sign-in" only animated. A failure
   * here is shown to the user rather than swallowed, because the honest states are
   * "Core has no login endpoint" (501) and "those credentials are wrong" (401).
   */
  async function verify(e: React.FormEvent) {
    e.preventDefault();
    await doVerify();
  }

  async function doVerify() {
    if (otp.trim().length < 4 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone, otp: otp.trim() }),
      });
      if (!res.ok) {
        const body = (await res.json().catch(() => null)) as { message?: string } | null;
        setError(body?.message ?? `Sign-in failed (${res.status}).`);
        return;
      }
      const body = (await res.json().catch(() => null)) as { demo?: boolean } | null;
      setDemo(body?.demo === true);
      router.push("/dashboard");
    } catch {
      setError("Sign-in failed. Check the OTP and try again.");
    } finally {
      setBusy(false);
    }
  }

  // Six digits is a complete OTP: submit without making the officer hunt for
  // the button. Shorter codes still go through the button path above.
  React.useEffect(() => {
    if (step === "otp" && otp.replace(/\D/g, "").length === 6 && !busy) {
      void doVerify();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [otp, step]);

  /**
   * One-click demo entry. Posts placeholder credentials — the server mints a
   * demo-marked cookie only when demo mode is on (development), otherwise it
   * 501s like any other attempt. The OTP field is skipped entirely because no
   * OTP is ever sent; asking the user to invent one is the confusion this
   * replaces.
   */
  async function enterDemo() {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone: "0000000000", otp: "0000" }),
      });
      if (!res.ok) {
        const body = (await res.json().catch(() => null)) as { message?: string } | null;
        setError(body?.message ?? `Demo entry failed (${res.status}).`);
        return;
      }
      setDemo(true);
      router.push("/dashboard");
    } catch {
      setError("Demo entry failed. Is the dev server running?");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden p-4">
      <ParticleBackground />
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="relative w-full max-w-md">
        <GlassCard className="border-white/20 bg-white/10 p-8 text-white backdrop-blur-2xl">
          <h1 className="font-display text-3xl font-bold">
            <span className="bg-gradient-to-r from-indigo-200 via-white to-amber-300 bg-clip-text text-transparent">
              Adi-Vritti
            </span>
          </h1>
          <p className="mt-1 min-h-6 font-mono text-xs text-indigo-100/90" aria-live="polite">{typed}</p>
          <p className="mt-1 text-xs uppercase tracking-widest text-indigo-100/70">Officer Console · MoTA</p>

          {step === "phone" ? (
            <form onSubmit={sendOtp} className="mt-6 space-y-3">
              <label htmlFor="phone" className="text-sm font-medium">Official mobile number</label>
              <Input
                id="phone"
                inputMode="tel"
                placeholder="+91 98XXX XXXXX"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="border-white/25 text-white placeholder:text-indigo-100/50"
                required
              />
              <Button type="submit" className="w-full bg-amber-500 text-amber-950 hover:bg-amber-400">
                Send OTP
              </Button>
            </form>
          ) : (
            <form onSubmit={verify} className="mt-6 space-y-3">
              <label htmlFor="otp" className="text-sm font-medium">
                Enter OTP sent to {phone || "your number"}
              </label>
              <Input
                id="otp"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="6-digit OTP"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                maxLength={6}
                className="border-white/25 text-center font-mono text-lg tracking-[0.4em] text-white placeholder:text-indigo-100/50"
                required
              />
              <Button type="submit" disabled={busy} className="w-full bg-emerald-500 text-white hover:bg-emerald-400">
                {busy ? "Verifying…" : "Verify & Enter Dashboard"}
              </Button>
              <button type="button" onClick={() => setStep("phone")} className="w-full text-center text-xs text-indigo-100/80 hover:underline">
                Change number
              </button>
            </form>
          )}
          {error && (
            <p role="alert" className="mt-3 rounded-md border border-amber-400/40 bg-amber-400/10 px-3 py-2 text-xs text-amber-100">
              {error}
            </p>
          )}
          {demoAvailable ? (
            <div className="mt-4">
              <Button
                type="button"
                onClick={enterDemo}
                disabled={busy}
                className="w-full bg-white/15 text-white hover:bg-white/25"
              >
                {busy ? "Entering…" : "Enter demo console →"}
              </Button>
              <p className="mt-2 text-center text-[11px] text-indigo-100/60">
                No SMS is sent in any environment — this skips the OTP step with demo data.
              </p>
            </div>
          ) : (
            <p className="mt-5 text-center text-[11px] text-indigo-100/60">
              Sign-in is checked server-side. This build has no Core login endpoint and demo
              entry is off — run in development for demo access. No real PII is collected.
            </p>
          )}
          {demo && (
            <p className="mt-2 text-center text-[11px] text-amber-200/90">
              Demo session — console figures are synthetic.
            </p>
          )}
        </GlassCard>
      </motion.div>
    </div>
  );
}
