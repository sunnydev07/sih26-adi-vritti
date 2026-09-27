/** Money, dates, Aadhaar masking — identical semantics to officer-web lib/utils. */

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
  const days = Math.floor(diff / 86400000);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  return `${days} days ago`;
}

export function greetingFor(date: Date): string {
  const h = date.getHours();
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  return "Good evening";
}

export function slaTone(elapsed: number, limit: number): "green" | "amber" | "red" {
  if (elapsed > limit) return "red";
  if (elapsed >= limit - 1) return "amber";
  return "green";
}
