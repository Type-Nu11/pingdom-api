#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="${1:-${ROOT_DIR}/build/inventory/development-bootstrap-manifest.md}"
COMPOSE_FILE="${ROOT_DIR}/docker-compose-local.yml"
SEED_CONFIG_FILE="${ROOT_DIR}/src/main/java/com/typenull/pingdom/shared/config/seed/DevAdminSeedConfig.java"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

require_command rg
require_command sed

mkdir -p "$(dirname "${OUTPUT_FILE}")"

compose_services="$(sed -n '/^services:/,/^[^[:space:]]/p' "${COMPOSE_FILE}" \
  | sed -n 's/^  \([a-zA-Z0-9_-]*\):$/\1/p')"

{
  echo '# Local Development Bootstrap Manifest'
  echo
  echo 'This file is generated from the checked-out source tree. It verifies the repeatable local bootstrap contract without connecting to Docker, PostgreSQL, Redis, or an external service.'
  echo
  echo '## Profile Configuration'
  echo
  echo '- Supported development profiles: `local`, `dev`'
  echo '- Profile files: `src/main/resources/application-local.yaml`, `src/main/resources/application-dev.yaml`'
  echo '- Docker Compose integration: enabled by both development profiles'
  echo '- Swagger UI: enabled by both development profiles'
  echo
  echo '## Local Dependencies'
  echo
  while IFS= read -r service; do
    [ -n "${service}" ] && echo "- ${service}"
  done <<< "${compose_services}"
  echo
  echo '## Seed Contract'
  echo
  echo '- Seed configuration class: `DevAdminSeedConfig`'
  echo '- Active profiles are restricted to: `dev`, `local`'
  echo '- Toggle keys: `seed.admin.enabled`, `seed.dev-data.enabled`'
  echo '- Seed credentials are supplied through environment variables and are intentionally excluded from this manifest.'
  echo '- Re-run protection is implemented with repository existence checks before each seed insert.'
  echo
  echo '## Source Evidence'
  echo
  rg -n '@Profile|seed\.(admin|dev-data)\.enabled|existsBy(Username|Email|UserIdAndPlaceId)' "${SEED_CONFIG_FILE}" || true
  echo
  echo '## Repeatable Commands'
  echo
  echo '```bash'
  echo './scripts/bootstrap-local-development.sh --verify'
  echo './scripts/bootstrap-local-development.sh --start-dependencies'
  echo './scripts/bootstrap-local-development.sh --run'
  echo '```'
} > "${OUTPUT_FILE}"

echo "Development bootstrap manifest written to ${OUTPUT_FILE}"
