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
    if (phone.replace(/\D/g, "").length >= 10) setStep("otp");
  }

  function verify(e: React.FormEvent) {
    e.preventDefault();
    if (otp.trim().length >= 4) router.push("/dashboard");
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
                placeholder="6-digit OTP"
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                className="border-white/25 text-center font-mono text-lg tracking-[0.4em] text-white placeholder:text-indigo-100/50"
                required
              />
              <Button type="submit" className="shimmer w-full bg-emerald-500 text-white hover:bg-emerald-400">
                Verify & Enter Dashboard
              </Button>
              <button type="button" onClick={() => setStep("phone")} className="w-full text-center text-xs text-indigo-100/80 hover:underline">
                Change number
              </button>
            </form>
          )}
          <p className="mt-5 text-center text-[11px] text-indigo-100/60">
            Demo build — any 10-digit number and 4+ digit OTP will enter. No real PII is collected.
          </p>
        </GlassCard>
      </motion.div>
    </div>
  );
}
