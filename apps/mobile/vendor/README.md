# vendor/ui-tokens — MIRROR, not source of truth

This directory is a verbatim copy of `packages/ui-tokens` (the shared design
tokens for officer-web + mobile). It exists for one reason: **EAS Build uploads
only `apps/mobile`**, so a `file:../../packages/ui-tokens` dependency dangles
on the cloud builder and the build dies at "Read app config".

Rules:
- Never edit files here. Edit `packages/ui-tokens` instead.
- After any token change, refresh the mirror from `apps/mobile` with:
  `npm run sync:tokens`
- Then reinstall (`npm install`) so `package-lock.json` stays consistent.
