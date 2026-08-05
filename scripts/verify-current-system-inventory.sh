#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="$(mktemp)"
trap 'rm -f "${OUTPUT_FILE}"' EXIT

"${ROOT_DIR}/scripts/collect-current-system-inventory.sh" "${OUTPUT_FILE}" >/dev/null

for section in '## Summary' '## API Mapping Locations' '## Flyway Migrations' \
  '## Table Declarations' '## Scheduled Execution Points'; do
  rg -Fq "${section}" "${OUTPUT_FILE}" || {
    echo "Missing inventory section: ${section}" >&2
    exit 1
  }
done

rg -Fq 'Flyway migration files: 87' "${OUTPUT_FILE}"
rg -Fq 'Latest Flyway migration: V87' "${OUTPUT_FILE}"
rg -Fq 'OutboxEventWorker.java' "${OUTPUT_FILE}"
rg -Fq 'map_place' "${OUTPUT_FILE}"

echo 'Current system inventory verification passed.'
