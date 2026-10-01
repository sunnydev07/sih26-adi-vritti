#!/usr/bin/env node
/**
 * Token-mirror check: packages/ui-tokens is the single source of truth, but
 * two consumers carry checked-in copies that must stay byte-identical:
 *
 *   1. apps/mobile/vendor/ui-tokens/* — Expo cannot resolve the workspace
 *      package, so `npm run sync:tokens` xcopies the directory. The copy is
 *      git-tracked, which means it can drift when someone edits the
 *      canonical file and forgets to re-run the sync.
 *   2. services/ai/app/data/help_faq.json — the AI help lane reads this path
 *      (help_service.py _FAQ_PATH), not packages/ui-tokens. Same content,
 *      different filename, same drift hazard.
 *
 * Officer-web needs no check: it depends on file:../../packages/ui-tokens
 * directly, so there is no copy to drift.
 *
 * Usage: node scripts/check-token-mirrors.mjs   (exit 0 = identical)
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

// fileURLToPath (not .pathname): a URL pathname keeps a leading slash before
// the drive letter, which path.join turns into C:\C:\... on Windows.
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

const PAIRS = [
  // [canonical, mirror]
  ["packages/ui-tokens/tokens.json", "apps/mobile/vendor/ui-tokens/tokens.json"],
  ["packages/ui-tokens/tokens.d.ts", "apps/mobile/vendor/ui-tokens/tokens.d.ts"],
  ["packages/ui-tokens/package.json", "apps/mobile/vendor/ui-tokens/package.json"],
  ["packages/ui-tokens/README.md", "apps/mobile/vendor/ui-tokens/README.md"],
  ["packages/ui-tokens/assistant_faq.json", "apps/mobile/vendor/ui-tokens/assistant_faq.json"],
  ["packages/ui-tokens/assistant_faq.json", "services/ai/app/data/help_faq.json"],
];

let failed = false;
for (const [canonical, mirror] of PAIRS) {
  let a, b;
  try {
    a = readFileSync(join(ROOT, canonical));
  } catch {
    console.error(`MISSING canonical file (not a drift, a broken tree): ${canonical}`);
    failed = true;
    continue;
  }
  try {
    b = readFileSync(join(ROOT, mirror));
  } catch {
    console.error(`MISSING mirror: ${mirror} — re-run the sync that produces it`);
    failed = true;
    continue;
  }
  if (!a.equals(b)) {
    console.error(`DRIFT: ${mirror} differs from ${canonical}`);
    console.error(`  fix: npm run sync:tokens --workspace=adi-vritti-mobile (vendor copy)`);
    console.error(`       or copy packages/ui-tokens/assistant_faq.json over the AI data file`);
    failed = true;
  } else {
    console.log(`ok: ${mirror}`);
  }
}
process.exit(failed ? 1 : 0);
