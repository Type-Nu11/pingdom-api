#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="${1:-${ROOT_DIR}/build/inventory/current-system-inventory.md}"
MIGRATION_DIR="${ROOT_DIR}/src/main/resources/db/migration"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

require_command rg
require_command find
require_command sort

mkdir -p "$(dirname "${OUTPUT_FILE}")"

mapping_count="$({ rg -n '@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)' \
  "${ROOT_DIR}/src/main/java" || true; } | wc -l | tr -d ' ')"
migration_count="$(find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*__*.sql' | wc -l | tr -d ' ')"
table_count="$({ rg -io 'create table( if not exists)?[[:space:]]+[a-zA-Z0-9_]+' \
  "${MIGRATION_DIR}" || true; } | sed -E 's/.*create table( if not exists)?[[:space:]]+//' \
  | tr '[:upper:]' '[:lower:]' | sort -u | wc -l | tr -d ' ')"
scheduled_count="$({ rg -n '@Scheduled' "${ROOT_DIR}/src/main/java" || true; } | wc -l | tr -d ' ')"

latest_migration="$(find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*__*.sql' \
  | while IFS= read -r migration_file; do
      filename="${migration_file##*/}"
      version="${filename#V}"
      printf '%s %s\n' "${version%%__*}" "${filename}"
    done \
  | sort -n | tail -1)"

{
  echo '# Current System Inventory'
  echo
  echo 'This file is generated from the checked-out source tree. It is an implementation inventory, not a live database audit.'
  echo
  echo '## Summary'
  echo
  echo "- Controller mapping annotations: ${mapping_count}"
  echo "- Flyway migration files: ${migration_count}"
  echo "- Latest Flyway migration: V${latest_migration}"
  echo "- Distinct CREATE TABLE declarations: ${table_count}"
  echo "- @Scheduled execution points: ${scheduled_count}"
  echo
  echo '## API Mapping Locations'
  echo
  rg -n '@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)' \
    "${ROOT_DIR}/src/main/java" || true
  echo
  echo '## Flyway Migrations'
  echo
  find "${MIGRATION_DIR}" -maxdepth 1 -type f -name 'V*__*.sql' | sort
  echo
  echo '## Table Declarations'
  echo
  rg -io 'create table( if not exists)?[[:space:]]+[a-zA-Z0-9_]+' "${MIGRATION_DIR}" \
    | sed -E 's/.*create table( if not exists)?[[:space:]]+//' \
    | tr '[:upper:]' '[:lower:]' | sort -u
  echo
  echo '## Scheduled Execution Points'
  echo
  rg -n -B 2 -A 5 '@Scheduled' "${ROOT_DIR}/src/main/java" || true
} > "${OUTPUT_FILE}"

echo "Inventory written to ${OUTPUT_FILE}"
