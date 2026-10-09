/** Money, dates, Aadhaar masking — identical semantics to officer-web lib/utils. */

export function formatPaise(paise: number): string {
  // Integer arithmetic on the paise value: float division prints ₹100.50 as
  // "₹100.5" and loses exactness past 2^53, so split rupees/paise as integers
  // and always show both paise digits.
  const sign = paise < 0 ? "-" : "";
  const abs = Math.abs(Math.trunc(paise));
  const rupees = Math.floor(abs / 100);
  const ps = abs % 100;
  return `${sign}₹${rupees.toLocaleString("en-IN")}.${ps.toString().padStart(2, "0")}`;
}

export function formatPaiseCompact(paise: number): string {
  const rupees = paise / 100;
  if (rupees >= 1e7) return `₹${(rupees / 1e7).toFixed(1)}Cr`;
  if (rupees >= 1e5) return `₹${(rupees / 1e5).toFixed(1)}L`;
  if (rupees >= 1e3) return `₹${(rupees / 1e3).toFixed(1)}K`;
  return formatPaise(paise);
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

export function greetingFor(date: Date, lang: "en" | "hi" = "en"): string {
  const h = date.getHours();
  if (lang === "hi") {
    if (h < 12) return "सुप्रभात";
    if (h < 17) return "नमस्कार";
    return "शुभ संध्या";
  }
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  return "Good evening";
}

export function slaTone(elapsed: number, limit: number): "green" | "amber" | "red" {
  if (elapsed > limit) return "red";
  if (elapsed >= limit - 1) return "amber";
  return "green";
}
