#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_FILE="$(mktemp)"
LOCAL_PROFILE_FILE="${ROOT_DIR}/src/main/resources/application-local.yaml"
DEV_PROFILE_FILE="${ROOT_DIR}/src/main/resources/application-dev.yaml"
COMPOSE_FILE="${ROOT_DIR}/docker-compose-local.yml"
BOOTSTRAP_SCRIPT="${ROOT_DIR}/scripts/bootstrap-local-development.sh"
trap 'rm -f "${OUTPUT_FILE}"' EXIT

"${ROOT_DIR}/scripts/collect-development-bootstrap-manifest.sh" "${OUTPUT_FILE}" >/dev/null
"${ROOT_DIR}/scripts/verify-current-system-inventory.sh" >/dev/null

for section in '## Profile Configuration' '## Local Dependencies' '## Seed Contract' \
  '## Source Evidence' '## Repeatable Commands'; do
  rg -Fq "${section}" "${OUTPUT_FILE}" || {
    echo "Missing development bootstrap section: ${section}" >&2
    exit 1
  }
done

for expected_value in 'application-local.yaml' 'application-dev.yaml' '- postgres' '- redis' \
  'Active profiles are restricted to: `dev`, `local`' \
  'seed.admin.enabled' 'seed.dev-data.enabled' \
  'existsByUsername' 'existsByEmail' 'existsByUserIdAndPlaceId'; do
  rg -Fq -- "${expected_value}" "${OUTPUT_FILE}" || {
    echo "Missing development bootstrap evidence: ${expected_value}" >&2
    exit 1
  }
done

if rg -q 'admin1234!|pingdom1234!' "${OUTPUT_FILE}"; then
  echo 'Development bootstrap manifest must not expose seed credential defaults.' >&2
  exit 1
fi

for profile_file in "${LOCAL_PROFILE_FILE}" "${DEV_PROFILE_FILE}"; do
  rg -Uq 'docker:\n[[:space:]]+compose:\n[[:space:]]+enabled: true' "${profile_file}" || {
    echo "Docker Compose must be enabled in ${profile_file##*/}." >&2
    exit 1
  }
  rg -Uq 'swagger-ui:\n[[:space:]]+enabled: true' "${profile_file}" || {
    echo "Swagger UI must be enabled in ${profile_file##*/}." >&2
    exit 1
  }
done

[ "$(rg -c 'healthcheck:' "${COMPOSE_FILE}")" -eq 2 ] || {
  echo 'Local Compose must define health checks for PostgreSQL and Redis.' >&2
  exit 1
}

rg -Fq 'SPRING_PROFILES_ACTIVE=local' "${BOOTSTRAP_SCRIPT}" || {
  echo 'Local bootstrap runner must explicitly use the local profile.' >&2
  exit 1
}

echo 'Development bootstrap verification passed.'
