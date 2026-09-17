#!/usr/bin/env bash
set -euo pipefail

NAVER_BASE_URL="${NAVER_MAP_BASE_URL:-https://naveropenapi.apigw.ntruss.com}"
CLIENT_ID="${NAVER_MAP_CLIENT_ID:-}"
CLIENT_SECRET="${NAVER_MAP_CLIENT_SECRET:-}"
APP_BASE_URL="${PINGDOM_APP_BASE_URL:-}"
ACCESS_TOKEN="${PINGDOM_ACCESS_TOKEN:-}"
FAILED=0

if [ -z "${CLIENT_ID//[[:space:]]/}" ] || [ -z "${CLIENT_SECRET//[[:space:]]/}" ]; then
  echo 'UNVERIFIED: NAVER_MAP_CLIENT_ID 또는 NAVER_MAP_CLIENT_SECRET이 없습니다.' >&2
  exit 2
fi

verify_region() {
  local name="$1"
  local longitude="$2"
  local latitude="$3"
  local expected_code="$4"
  local response_file
  local metrics
  local http_status
  local elapsed_seconds
  local status_code
  local region_code
  local sido
  local sigungu

  response_file="$(mktemp)"
  if ! metrics="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code} %{time_total}' \
      --get "${NAVER_BASE_URL}/map-reversegeocode/v2/gc" \
      --data-urlencode "request=coordsToaddr" \
      --data-urlencode "coords=${longitude},${latitude}" \
      --data-urlencode "sourcecrs=epsg:4326" \
      --data-urlencode "orders=legalcode" \
      --data-urlencode "output=json" \
      --header "x-ncp-apigw-api-key-id: ${CLIENT_ID}" \
      --header "x-ncp-apigw-api-key: ${CLIENT_SECRET}")"; then
    rm -f "${response_file}"
    echo "FAIL ${name}: 네이버 API 연결에 실패했습니다." >&2
    FAILED=1
    return
  fi

  read -r http_status elapsed_seconds <<< "${metrics}"
  if ! jq -e . "${response_file}" >/dev/null 2>&1; then
    rm -f "${response_file}"
    echo "FAIL ${name}: 네이버 API 응답이 JSON 형식이 아닙니다. http=${http_status} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi
  status_code="$(jq -r '.status.code // "missing"' "${response_file}")"
  region_code="$(jq -r '[.results[]? | select(.name == "legalcode") | .code.id][0] // ""' "${response_file}")"
  sido="$(jq -r '[.results[]? | select(.name == "legalcode") | .region.area1.name][0] // ""' "${response_file}")"
  sigungu="$(jq -r '[.results[]? | select(.name == "legalcode") | .region.area2.name][0] // ""' "${response_file}")"
  rm -f "${response_file}"

  if [ "${http_status}" != '200' ] || [ "${status_code}" != '0' ] \
      || [ "${region_code:0:5}" != "${expected_code}" ] || [ -z "${sido}" ]; then
    echo "FAIL ${name}: http=${http_status} status=${status_code} regionCode=${region_code:0:5} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi

  if [ -z "${sigungu}" ] && [ "${sido}" != '세종특별자치시' ]; then
    echo "FAIL ${name}: 시군구가 비어 있습니다. elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi

  echo "PASS ${name}: regionCode=${region_code:0:5} elapsed=${elapsed_seconds}s"
}

verify_app_cache() {
  local response_file
  local metrics
  local attempt

  if [ -z "${APP_BASE_URL//[[:space:]]/}" ] || [ -z "${ACCESS_TOKEN//[[:space:]]/}" ]; then
    echo 'UNVERIFIED app-cache: PINGDOM_APP_BASE_URL 또는 PINGDOM_ACCESS_TOKEN이 없습니다.'
    return
  fi

  for attempt in 1 2; do
    response_file="$(mktemp)"
    if ! metrics="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code} %{time_total}' \
        --get "${APP_BASE_URL}/places/local-hot" \
        --data-urlencode 'latitude=37.5172' \
        --data-urlencode 'longitude=127.0473' \
        --data-urlencode 'page=1' \
        --data-urlencode 'limit=1' \
        --header "Authorization: Bearer ${ACCESS_TOKEN}")"; then
      rm -f "${response_file}"
      echo "FAIL app-cache attempt=${attempt}: Pingdom API 요청에 실패했습니다." >&2
      FAILED=1
      return
    fi
    rm -f "${response_file}"
    echo "APP_CACHE attempt=${attempt}: http=${metrics%% *} elapsed=${metrics#* }s"
  done
}

verify_region 'seoul' '127.0473' '37.5172' '11680'
verify_region 'gwangyang' '127.5850' '34.9765' '46230'
verify_region 'sejong' '127.2654387' '36.5008113' '36110'
verify_app_cache

if [ "${FAILED}" -ne 0 ]; then
  exit 1
fi

echo 'Naver local-region verification passed.'
