import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

export function formatPaise(paise: number): string {
  return `₹${(paise / 100).toLocaleString("en-IN")}`;
}

export function formatPaiseCompact(paise: number): string {
  const rupees = paise / 100;
  if (rupees >= 1e7) return `₹${(rupees / 1e7).toFixed(1)}Cr`;
  if (rupees >= 1e5) return `₹${(rupees / 1e5).toFixed(1)}L`;
  if (rupees >= 1e3) return `₹${(rupees / 1e3).toFixed(1)}K`;
  return `₹${rupees.toLocaleString("en-IN")}`;
}

export function maskAadhaar(ref: string): string {
  return `XXXX-XXXX-${ref.slice(-4)}`;
}

export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}

export function slaStatus(
  elapsed: number,
  limit: number
): { label: string; color: string; isBreached: boolean; variant: "ok" | "risk" | "breached" } {
  if (elapsed > limit) return { label: `SLA Breached (${elapsed}/${limit} days)`, color: "failed", isBreached: true, variant: "breached" };
  if (elapsed >= limit - 1) return { label: `At Risk (${elapsed}/${limit} days)`, color: "pending", isBreached: false, variant: "risk" };
  return { label: `On Track (${elapsed}/${limit} days)`, color: "verified", isBreached: false, variant: "ok" };
}
