#!/usr/bin/env bash
set -euo pipefail

# 이 스크립트는 키·JWT·공급자 원문 응답을 출력하지 않고 Directions 5와 Pingdom 계약만 검증한다.
NAVER_BASE_URL="${NAVER_DIRECTIONS_BASE_URL:-https://maps.apigw.ntruss.com}"
CLIENT_ID="${NAVER_DIRECTIONS_CLIENT_ID:-}"
CLIENT_SECRET="${NAVER_DIRECTIONS_CLIENT_SECRET:-}"
APP_BASE_URL="${PINGDOM_APP_BASE_URL:-}"
ACCESS_TOKEN="${PINGDOM_ACCESS_TOKEN:-}"
FAILED=0

if [ -z "${CLIENT_ID//[[:space:]]/}" ] || [ -z "${CLIENT_SECRET//[[:space:]]/}" ]; then
  echo 'UNVERIFIED: NAVER_DIRECTIONS_CLIENT_ID 또는 NAVER_DIRECTIONS_CLIENT_SECRET이 없습니다.' >&2
  exit 2
fi

verify_provider() {
  local name="$1"
  local origin_longitude="$2"
  local origin_latitude="$3"
  local destination_longitude="$4"
  local destination_latitude="$5"
  local response_file metrics http_status elapsed_seconds code distance duration point_count

  response_file="$(mktemp)"
  if ! metrics="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code} %{time_total}' --max-time 12 \
      --get "$NAVER_BASE_URL/map-direction/v1/driving" \
      --data-urlencode "start=${origin_longitude},${origin_latitude}" \
      --data-urlencode "goal=${destination_longitude},${destination_latitude}" \
      --data-urlencode 'option=traoptimal' \
      --header "x-ncp-apigw-api-key-id: $CLIENT_ID" \
      --header "x-ncp-apigw-api-key: $CLIENT_SECRET" \
      --header 'Accept: application/json')"; then
    rm -f "$response_file"
    echo "FAIL provider ${name}: 네이버 Directions API 연결에 실패했습니다." >&2
    FAILED=1
    return
  fi

  read -r http_status elapsed_seconds <<< "$metrics"
  if ! jq -e . "$response_file" >/dev/null 2>&1; then
    rm -f "$response_file"
    echo "FAIL provider ${name}: JSON이 아닌 응답입니다. http=${http_status} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi
  code="$(jq -r '.code // "missing"' "$response_file")"
  distance="$(jq -r '.route.traoptimal[0].summary.distance // "missing"' "$response_file")"
  duration="$(jq -r '.route.traoptimal[0].summary.duration // "missing"' "$response_file")"
  point_count="$(jq -r '.route.traoptimal[0].path | if type == "array" then length else 0 end' "$response_file")"
  rm -f "$response_file"

  if [ "$http_status" != '200' ] || [ "$code" != '0' ] \
      || ! [[ "$distance" =~ ^[0-9]+$ ]] || ! [[ "$duration" =~ ^[0-9]+$ ]] \
      || ! [[ "$point_count" =~ ^[0-9]+$ ]] || [ "$point_count" -lt 2 ]; then
    echo "FAIL provider ${name}: http=${http_status} code=${code} distance=${distance} durationMs=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi

  echo "PASS provider ${name}: distanceMeters=${distance} durationMs=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s"
}

verify_app() {
  local name="$1"
  local origin_longitude="$2"
  local origin_latitude="$3"
  local destination_longitude="$4"
  local destination_latitude="$5"
  local response_file metrics http_status elapsed_seconds distance duration point_count

  if [ -z "${APP_BASE_URL//[[:space:]]/}" ] || [ -z "${ACCESS_TOKEN//[[:space:]]/}" ]; then
    echo "UNVERIFIED app ${name}: PINGDOM_APP_BASE_URL 또는 PINGDOM_ACCESS_TOKEN이 없습니다."
    return
  fi

  response_file="$(mktemp)"
  if ! metrics="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code} %{time_total}' --max-time 15 \
      --request POST "$APP_BASE_URL/routes" \
      --header "Authorization: Bearer $ACCESS_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "{\"origin\":{\"latitude\":${origin_latitude},\"longitude\":${origin_longitude}},\"destination\":{\"latitude\":${destination_latitude},\"longitude\":${destination_longitude}},\"mode\":\"car\"}")"; then
    rm -f "$response_file"
    echo "FAIL app ${name}: Pingdom API 연결에 실패했습니다." >&2
    FAILED=1
    return
  fi

  read -r http_status elapsed_seconds <<< "$metrics"
  distance="$(jq -r '.distanceMeters // "missing"' "$response_file" 2>/dev/null || echo missing)"
  duration="$(jq -r '.durationSeconds // "missing"' "$response_file" 2>/dev/null || echo missing)"
  point_count="$(jq -r '.path | if type == "array" then length else 0 end' "$response_file" 2>/dev/null || echo 0)"
  rm -f "$response_file"

  if [ "$http_status" != '200' ] || ! [[ "$distance" =~ ^[0-9]+$ ]] \
      || ! [[ "$duration" =~ ^[0-9]+$ ]] || ! [[ "$point_count" =~ ^[0-9]+$ ]] || [ "$point_count" -lt 2 ]; then
    echo "FAIL app ${name}: http=${http_status} distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi

  echo "PASS app ${name}: distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s"
}

# 서울 도심·강남의 서로 다른 국내 자동차 경로 두 쌍이다.
verify_provider 'seoul-cityhall-to-gangnam' '126.9780' '37.5665' '127.0276' '37.4979'
verify_provider 'jamsil-to-yeouido' '127.1002' '37.5133' '126.9245' '37.5219'
verify_app 'seoul-cityhall-to-gangnam' '126.9780' '37.5665' '127.0276' '37.4979'
verify_app 'jamsil-to-yeouido' '127.1002' '37.5133' '126.9245' '37.5219'

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi

echo 'Naver Directions verification passed.'
