# @adi-vritti/ui-tokens

Shared design tokens + plain-language vocabulary for `apps/officer-web` and
`apps/mobile`. **Single source of truth** — change a word or color here, both
frontends follow.

## What's inside

| Key | Purpose |
|---|---|
| `brand` / `status` / `dark` | Colors (officer CSS vars + mobile theme read these) |
| `radius` / `touch` / `type` / `layout` | Shape, 44px min touch target, type scale, mobile spacing |
| `vocabulary` | Jargon → judge-friendly words (`SLA` → "Waiting time", `USID` → "Scholar ID") |
| `sla` / `claimStatus` / `stp` | Status bands — thresholds mirror backend semantics |
| `failureFixes` | E001–E006 cause + fix in plain words — **must stay in sync with `services/core/.../FailureDecoder.java`** |
| `assistant` | Chatbot name + suggestion chips shared by both apps |

## `assistant_faq.json` (offline Ask Adi mirror)

Exact copy of `services/ai/app/data/help_faq.json`: 11 FAQs + `system`
texts (greeting, deflection, fallback) in English + Hindi, keyed by stable
`id`. Both frontends answer from this mirror when the AI service is
unreachable (the judge demo runs WIFI OFF), and prefer `POST /jago/help`
when online.

## Rules

- Plain words here must never contradict backend meanings. If `FailureDecoder`
  changes, update `failureFixes` in the same PR.
- `assistant_faq.json` must stay byte-identical to the backend canonical
  file — update both in the same PR. Never add personal status, amounts, or
  verdicts here.
- No app imports another app's copy — always import this package.
- No dependencies. JSON + types only.
