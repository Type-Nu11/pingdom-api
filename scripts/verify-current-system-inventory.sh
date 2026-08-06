#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="$(mktemp)"
trap 'rm -f "${OUTPUT_FILE}"' EXIT

"${ROOT_DIR}/scripts/collect-current-system-inventory.sh" "${OUTPUT_FILE}" >/dev/null

expected_migration_count="$(find "${ROOT_DIR}/src/main/resources/db/migration" \
  -maxdepth 1 -type f -name 'V*__*.sql' | wc -l | tr -d ' ')"
expected_latest_version="$(find "${ROOT_DIR}/src/main/resources/db/migration" \
  -maxdepth 1 -type f -name 'V*__*.sql' \
  | sed -E 's#^.*/V([0-9]+)__.*#\1#' | sort -n | tail -1)"

for section in '## Summary' '## API Mapping Locations' '## Flyway Migrations' \
  '## Table Declarations' '## Scheduled Execution Points'; do
  rg -Fq "${section}" "${OUTPUT_FILE}" || {
    echo "Missing inventory section: ${section}" >&2
    exit 1
  }
done

rg -Fq "Flyway migration files: ${expected_migration_count}" "${OUTPUT_FILE}"
rg -Fq "Latest Flyway migration: V${expected_latest_version}" "${OUTPUT_FILE}"
rg -Fq 'OutboxEventWorker.java' "${OUTPUT_FILE}"
rg -Fq 'map_place' "${OUTPUT_FILE}"

echo 'Current system inventory verification passed.'
