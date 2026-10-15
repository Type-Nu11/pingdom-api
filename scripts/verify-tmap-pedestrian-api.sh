#!/usr/bin/env bash
set -euo pipefail

# 키·JWT·공급자 원문 응답을 출력하지 않고 TMAP 보행자와 Pingdom 응답 계약만 검증한다.
TMAP_BASE_URL="${TMAP_PEDESTRIAN_BASE_URL:-https://apis.openapi.sk.com}"
APP_KEY="${TMAP_PEDESTRIAN_APP_KEY:-}"
APP_BASE_URL="${PINGDOM_APP_BASE_URL:-}"
ACCESS_TOKEN="${PINGDOM_ACCESS_TOKEN:-}"
FAILED=0

if [ -z "${APP_KEY//[[:space:]]/}" ]; then
  echo 'UNVERIFIED: TMAP_PEDESTRIAN_APP_KEY가 없습니다.' >&2
  exit 2
fi

verify_provider() {
  local name="$1" origin_longitude="$2" origin_latitude="$3" destination_longitude="$4" destination_latitude="$5"
  local response_file metrics http_status elapsed_seconds distance duration point_count

  response_file="$(mktemp)"
  if ! metrics="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code} %{time_total}' --max-time 12 \
      --request POST "$TMAP_BASE_URL/tmap/routes/pedestrian?version=1" \
      --header "appKey: $APP_KEY" --header 'Accept: application/json' --header 'Content-Type: application/json' \
      --data "{\"startX\":${origin_longitude},\"startY\":${origin_latitude},\"endX\":${destination_longitude},\"endY\":${destination_latitude},\"startName\":\"%EC%B6%9C%EB%B0%9C\",\"endName\":\"%EB%8F%84%EC%B0%A9\",\"reqCoordType\":\"WGS84GEO\",\"resCoordType\":\"WGS84GEO\",\"searchOption\":\"0\",\"sort\":\"index\"}")"; then
    rm -f "$response_file"
    echo "FAIL provider ${name}: TMAP 보행자 API 연결에 실패했습니다." >&2
    FAILED=1
    return
  fi

  read -r http_status elapsed_seconds <<< "$metrics"
  distance="$(jq -r '[.features[]?.properties.totalDistance | numbers] | first // "missing"' "$response_file" 2>/dev/null || echo missing)"
  duration="$(jq -r '[.features[]?.properties.totalTime | numbers] | first // "missing"' "$response_file" 2>/dev/null || echo missing)"
  point_count="$(jq -r '[.features[]? | select(.geometry.type == "LineString") | .geometry.coordinates[]?] | length' "$response_file" 2>/dev/null || echo 0)"
  rm -f "$response_file"

  if [ "$http_status" != '200' ] || ! [[ "$distance" =~ ^[0-9]+$ ]] \
      || ! [[ "$duration" =~ ^[0-9]+$ ]] || ! [[ "$point_count" =~ ^[0-9]+$ ]] || [ "$point_count" -lt 2 ]; then
    echo "FAIL provider ${name}: http=${http_status} distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi
  echo "PASS provider ${name}: distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s"
}

verify_app() {
  local name="$1" origin_longitude="$2" origin_latitude="$3" destination_longitude="$4" destination_latitude="$5"
  local response_file metrics http_status elapsed_seconds mode provider distance duration point_count

  if [ -z "${APP_BASE_URL//[[:space:]]/}" ] || [ -z "${ACCESS_TOKEN//[[:space:]]/}" ]; then
    echo "UNVERIFIED app ${name}: PINGDOM_APP_BASE_URL 또는 PINGDOM_ACCESS_TOKEN이 없습니다."
    return
  fi

  response_file="$(mktemp)"
  if ! metrics="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code} %{time_total}' --max-time 15 \
      --request POST "$APP_BASE_URL/routes" --header "Authorization: Bearer $ACCESS_TOKEN" \
      --header 'X-Client-Type: App' --header 'Content-Type: application/json' \
      --data "{\"origin\":{\"latitude\":${origin_latitude},\"longitude\":${origin_longitude}},\"destination\":{\"latitude\":${destination_latitude},\"longitude\":${destination_longitude}},\"mode\":\"walk\"}")"; then
    rm -f "$response_file"
    echo "FAIL app ${name}: Pingdom API 연결에 실패했습니다." >&2
    FAILED=1
    return
  fi

  read -r http_status elapsed_seconds <<< "$metrics"
  mode="$(jq -r '.mode // "missing"' "$response_file" 2>/dev/null || echo missing)"
  provider="$(jq -r '.provider // "missing"' "$response_file" 2>/dev/null || echo missing)"
  distance="$(jq -r '.distanceMeters // "missing"' "$response_file" 2>/dev/null || echo missing)"
  duration="$(jq -r '.durationSeconds // "missing"' "$response_file" 2>/dev/null || echo missing)"
  point_count="$(jq -r '.path | if type == "array" then length else 0 end' "$response_file" 2>/dev/null || echo 0)"
  rm -f "$response_file"

  if [ "$http_status" != '200' ] || [ "$mode" != 'walk' ] || [ "$provider" != 'tmap' ] \
      || ! [[ "$distance" =~ ^[0-9]+$ ]] || ! [[ "$duration" =~ ^[0-9]+$ ]] \
      || ! [[ "$point_count" =~ ^[0-9]+$ ]] || [ "$point_count" -lt 2 ]; then
    echo "FAIL app ${name}: http=${http_status} mode=${mode} provider=${provider} distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s" >&2
    FAILED=1
    return
  fi
  echo "PASS app ${name}: mode=${mode} provider=${provider} distanceMeters=${distance} durationSeconds=${duration} pathPoints=${point_count} elapsed=${elapsed_seconds}s"
}

verify_provider 'seoul-cityhall-to-gwanghwamun' '126.9780' '37.5665' '126.9769' '37.5759'
verify_provider 'gangnam-to-seolleung' '127.0276' '37.4979' '127.0507' '37.5045'
verify_app 'seoul-cityhall-to-gwanghwamun' '126.9780' '37.5665' '126.9769' '37.5759'
verify_app 'gangnam-to-seolleung' '127.0276' '37.4979' '127.0507' '37.5045'

if [ "$FAILED" -ne 0 ]; then exit 1; fi
echo 'TMAP pedestrian verification passed.'
