#!/bin/bash
set -euo pipefail
# Regenerate TypeScript + Java clients from the contract.
# NEVER hand-edit ./typescript or ./java — edit docs/openapi/core.yaml instead.
cd "$(dirname "$0")"

# TypeScript client
npx @openapitools/openapi-generator-cli generate \
  -i ../../docs/openapi/core.yaml \
  -g typescript-fetch \
  -o ./typescript \
  --additional-properties=supportsES6=true,typescriptThreePlus=true

# Java client
npx @openapitools/openapi-generator-cli generate \
  -i ../../docs/openapi/core.yaml \
  -g java \
  -o ./java \
  --additional-properties=library=restclient,java21=true,useJakartaEe=true

echo "Clients regenerated from docs/openapi/core.yaml"
