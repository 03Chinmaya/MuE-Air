#!/usr/bin/env bash
set -euo pipefail

# Run tsx check-authdb-isolation.ts
cd "$(dirname "$0")/.."
npx tsx scripts/check-authdb-isolation.ts
