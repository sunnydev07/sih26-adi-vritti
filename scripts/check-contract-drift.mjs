#!/usr/bin/env node
/**
 * Contract-drift check: hand-written docs/openapi/core.yaml vs the spec
 * regenerated from the running app (springdoc /v3/api-docs).
 *
 * Usage:
 *   npx -y js-yaml@4 docs/openapi/core.yaml > /tmp/core.json
 *   curl -sf http://localhost:8080/v3/api-docs > /tmp/live.json
 *   node scripts/check-contract-drift.mjs /tmp/core.json /tmp/live.json
 *
 * Structural comparison only — descriptions, examples, and envelope versions
 * (contract is 3.1, springdoc emits 3.0) are ignored:
 *   1. path+method sets must match exactly. A live-only operation is an
 *      undocumented endpoint; a contract-only one is unimplemented. Both fail.
 *   2. Per operation, the (in, name) parameter sets must match exactly.
 *   3. Same-named component schemas must expose the same property names and
 *      the same required names, compared snake-normalized (the contract is
 *      snake_case; springdoc emits whatever Jackson produces — wire casing is
 *      pinned separately by WireFormatContractTest).
 *
 * Schema-name differences (live-only vs contract-only components) are reported
 * but do not fail: springdoc names nested DTOs differently and emits helpers
 * the contract inlines. Property sets of SHARED names fail hard.
 */
import { readFileSync } from "node:fs";

const [contractPath, livePath] = process.argv.slice(2);
if (!contractPath || !livePath) {
  console.error("usage: check-contract-drift.mjs <contract.json> <live.json>");
  process.exit(2);
}
const contract = JSON.parse(readFileSync(contractPath, "utf8"));
const live = JSON.parse(readFileSync(livePath, "utf8"));

const drift = [];
const notes = [];

function resolve(doc, ref) {
  if (!ref || !ref.startsWith("#/")) return null;
  return ref.slice(2).split("/").reduce((node, part) => node?.[part], doc) ?? null;
}

function expandParams(doc, op) {
  return (op.parameters ?? []).map((p) => {
    const resolved = p.$ref ? resolve(doc, p.$ref) : p;
    return resolved ? `${resolved.in}:${resolved.name}` : "unresolvable-param";
  });
}

function snake(name) {
  return String(name).replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase();
}

// 1. Operations.
const contractOps = new Map();
for (const [path, methods] of Object.entries(contract.paths ?? {})) {
  for (const [method, op] of Object.entries(methods ?? {})) {
    if (!op || typeof op !== "object" || op.$ref) continue;
    contractOps.set(`${method.toUpperCase()} ${path}`, op);
  }
}
const liveOps = new Map();
for (const [path, methods] of Object.entries(live.paths ?? {})) {
  for (const [method, op] of Object.entries(methods ?? {})) {
    if (!op || typeof op !== "object" || op.$ref) continue;
    liveOps.set(`${method.toUpperCase()} ${path}`, op);
  }
}
for (const key of [...contractOps.keys()].sort()) {
  if (!liveOps.has(key)) drift.push(`contract-only operation (unimplemented?): ${key}`);
}
for (const key of [...liveOps.keys()].sort()) {
  if (!contractOps.has(key)) drift.push(`live-only operation (undocumented?): ${key}`);
}

// 2. Parameters of shared operations.
for (const [key, contractOp] of [...contractOps.entries()].sort()) {
  const liveOp = liveOps.get(key);
  if (!liveOp) continue;
  const want = expandParams(contract, contractOp).sort().join(",");
  const got = expandParams(live, liveOp).sort().join(",");
  if (want !== got) drift.push(`${key} params differ:\n    contract: [${want}]\n    live:     [${got}]`);
}

// 3. Properties of shared schemas.
const contractSchemas = contract.components?.schemas ?? {};
const liveSchemas = live.components?.schemas ?? {};
for (const name of Object.keys(contractSchemas).sort()) {
  if (!liveSchemas[name]) {
    notes.push(`contract-only schema (no same-named live schema): ${name}`);
    continue;
  }
  const norm = (obj) => new Set(Object.keys(obj ?? {}).map(snake));
  const cProps = norm(contractSchemas[name].properties);
  const lProps = norm(liveSchemas[name].properties);
  for (const p of [...cProps].sort()) {
    if (!lProps.has(p)) drift.push(`schema ${name}: contract property '${p}' missing live`);
  }
  for (const p of [...lProps].sort()) {
    if (!cProps.has(p)) drift.push(`schema ${name}: live property '${p}' missing from contract`);
  }
  const cReq = new Set((contractSchemas[name].required ?? []).map(snake));
  const lReq = new Set((liveSchemas[name].required ?? []).map(snake));
  for (const p of [...cReq].sort()) {
    if (!lReq.has(p)) drift.push(`schema ${name}: contract requires '${p}' but live does not`);
  }
  for (const p of [...lReq].sort()) {
    if (!cReq.has(p)) drift.push(`schema ${name}: live requires '${p}' but contract does not`);
  }
}
for (const name of Object.keys(liveSchemas).sort()) {
  if (!contractSchemas[name]) notes.push(`live-only schema (not compared): ${name}`);
}

for (const n of notes) console.log(`note: ${n}`);
if (drift.length > 0) {
  console.log(`\nCONTRACT DRIFT (${drift.length}):`);
  for (const d of drift) console.log(`- ${d}`);
  process.exit(1);
}
console.log("\ncontract matches the running app: no drift.");
