# TMAP 보행자 운영 검증 (#1798)

`POST /routes`, `mode=walk`의 503을 설정·공급자·앱 API 단계로 분리해 확인한다. 키·JWT·실제 사용자 좌표·공급자 원문은 기록하지 않는다.

## 실패 분류

배포 후 `Route provider failure provider=tmap routeType=pedestrian` 로그의 `outcome`을 확인한다.

| outcome | 의미 | 조치 |
| --- | --- | --- |
| `configuration` | 비활성 또는 appKey 누락 | 운영 환경 파일에 `TMAP_PEDESTRIAN_ENABLED=true`와 전용 appKey를 등록하고 app 재생성 |
| `authentication` | 공급자 401/403 | TMAP 키와 보행자 상품 권한 확인 |
| `quota` | 공급자 429 | 쿼터·과금 한도 확인 |
| `provider_error` | 공급자 5xx | 공급자 상태와 네트워크 확인 |
| `communication` | 연결·통신 실패 | DNS·TLS·egress와 연결 상태 확인 |
| `timeout` | 전체 요청 제한 시간 초과 | 공급자 지연과 timeout 설정 확인 |
| `invalid_response` | HTTP 2xx 응답 계약 불일치 | 상태와 배포 버전을 기록하고 응답 계약 확인 |

로그에는 appKey, 좌표, 공급자 본문을 포함하지 않는다. 실행 컨테이너의 값 대신 상태만 확인한다.

```sh
docker compose exec -T app sh -c '
case "${TMAP_PEDESTRIAN_ENABLED:-false}" in
  true) echo "tmap_pedestrian_enabled=true" ;;
  *) echo "tmap_pedestrian_enabled=false_or_invalid" ;;
esac
if [ -n "$(printf "%s" "${TMAP_PEDESTRIAN_APP_KEY:-}" | tr -d "[:space:]")" ]; then
  echo "TMAP_PEDESTRIAN_APP_KEY=present"
else
  echo "TMAP_PEDESTRIAN_APP_KEY=absent_or_blank"
fi
'
```

## 활성화 및 검증

1. TMAP 보행자 경로안내 권한이 있는 서버 전용 키를 운영 환경 파일에 등록한다.
2. `TMAP_PEDESTRIAN_ENABLED=true`를 설정하고 동일한 `PINGDOM_ENV_FILE`을 사용해 app을 재생성한다. 단순 restart는 환경변수 변경을 반영하지 않는다.
3. 아래 스크립트로 서로 다른 국내 경로 2쌍의 공급자 응답과 인증된 외부 API 계약을 검증한다.
4. 실제 Android 앱에서 경로선·거리·시간과 안내 진입을 확인하고 시각·배포 버전·traceId를 이슈에 기록한다.

```sh
TMAP_PEDESTRIAN_ENABLED=true
TMAP_PEDESTRIAN_APP_KEY=<server-app-key>
TMAP_PEDESTRIAN_BASE_URL=https://apis.openapi.sk.com
PINGDOM_APP_BASE_URL=https://www.typenull.xyz
PINGDOM_ACCESS_TOKEN=<temporary-access-token>
./scripts/verify-tmap-pedestrian-api.sh
```

스크립트는 공급자와 앱 응답에서 `mode=walk`, `provider=tmap`, 숫자형 거리·시간, 경로점 2개 이상을 확인한다. 키가 없으면 종료 코드 2, 검증 실패는 종료 코드 1을 반환한다. 앱 URL 또는 JWT가 없으면 공급자 검증만 수행하고 앱 검증을 `UNVERIFIED`로 남긴다.

실패 시 `TMAP_PEDESTRIAN_ENABLED=false`로 되돌리고 app을 재생성한다. 공급자 직접 호출과 readiness 성공은 실제 앱 복구를 대신하지 않는다.
