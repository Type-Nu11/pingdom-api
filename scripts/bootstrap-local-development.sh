#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose-local.yml"

usage() {
  cat <<'USAGE'
Usage: ./scripts/bootstrap-local-development.sh [option]

  --verify              Verify the source-based local bootstrap contract (default)
  --start-dependencies  Start PostgreSQL and Redis, waiting for their health checks
  --run                 Start dependencies, then run the application with the local profile
  --stop-dependencies   Stop the local PostgreSQL and Redis containers
USAGE
}

start_dependencies() {
  docker compose -f "${COMPOSE_FILE}" up -d --wait
}

case "${1:---verify}" in
  --verify)
    exec "${ROOT_DIR}/scripts/verify-development-bootstrap.sh"
    ;;
  --start-dependencies)
    start_dependencies
    ;;
  --run)
    start_dependencies
    cd "${ROOT_DIR}"
    exec env SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
    ;;
  --stop-dependencies)
    exec docker compose -f "${COMPOSE_FILE}" down
    ;;
  --help|-h)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
