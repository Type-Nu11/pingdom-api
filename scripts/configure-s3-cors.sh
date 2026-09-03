#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: AWS_S3_BUCKET=<bucket> CORS_ALLOWED_ORIGINS=<origin,...> \
  ./scripts/configure-s3-cors.sh [--dry-run]

Configure the S3 bucket CORS rules used by browser presigned PUT uploads.

  --dry-run  Print the generated AWS CLI payload without changing AWS.
USAGE
}

fail() {
  echo "${1}" >&2
  exit 1
}

dry_run=false
case "${1:-}" in
  "") ;;
  --dry-run) dry_run=true ;;
  --help|-h) usage; exit 0 ;;
  *) usage >&2; exit 1 ;;
esac

: "${AWS_S3_BUCKET:?AWS_S3_BUCKET is required.}"
: "${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required (comma-separated origins).}"

# S3 compares origins exactly. Reject paths and trailing slashes before applying
# the rule so a visually similar but ineffective origin is not deployed.
IFS=',' read -r -a raw_origins <<< "${CORS_ALLOWED_ORIGINS}"
origins_json=""
for raw_origin in "${raw_origins[@]}"; do
  origin="$(printf '%s' "${raw_origin}" | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')"
  if [[ ! "${origin}" =~ ^https?://[^/:[:space:]]+(:[0-9]+)?$ \
        || "${origin}" == *\"* || "${origin}" == *\\* ]]; then
    fail "Invalid CORS origin: ${origin} (expected http(s)://host[:port])"
  fi
  origins_json+="${origins_json:+,}\"${origin}\""
done

cors_configuration="$(printf '{"CORSRules":[{"AllowedOrigins":[%s],"AllowedMethods":["PUT","GET","HEAD"],"AllowedHeaders":["Content-Type","x-amz-*"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3000}]}' "${origins_json}")"

if [[ "${dry_run}" == true ]]; then
  printf '%s\n' "${cors_configuration}"
  exit 0
fi

command -v aws >/dev/null 2>&1 || fail 'AWS CLI is required to apply S3 CORS configuration.'

aws s3api put-bucket-cors \
  --bucket "${AWS_S3_BUCKET}" \
  --cors-configuration "${cors_configuration}"

echo "S3 CORS configuration applied to ${AWS_S3_BUCKET}."
