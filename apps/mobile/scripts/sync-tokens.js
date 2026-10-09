// Cross-platform mirror of packages/ui-tokens into the vendored copy.
//
// The npm script used to be a Windows-only `xcopy` invocation, so on macOS,
// Linux, or CI it failed and the mirror silently went stale while
// vendor/README.md claimed "never edit here". This runs everywhere node does.
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..", "..", "..", "packages", "ui-tokens");
const dest = path.resolve(__dirname, "..", "vendor", "ui-tokens");

fs.cpSync(root, dest, { recursive: true });
console.log(`synced ${root} -> ${dest}`);
