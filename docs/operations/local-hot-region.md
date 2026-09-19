# 좌표 기반 지역 핫플 운영 설정

대상 API: `GET /places/local-hot?latitude={latitude}&longitude={longitude}&page=1&limit=20`

좌표 조회는 네이버 역지오코딩으로 법정동 시·군·구를 판정하고, 저장된 `regionCode` 조회는 외부 지도 API를 호출하지 않는다. 기존 Kakao 설정과 지도 viewport·장소 응답 계약은 유지한다.

## 지도 제공자 선택과 환경 변수

환경 변수 값에는 실제 키를 기록하거나 로그·이슈에 첨부하지 않는다. `NAVER_MAP_CLIENT_ID`와 `NAVER_MAP_CLIENT_SECRET`은 서버용 Naver Cloud Platform 인증정보이며, 지도 화면용 클라이언트 키와 별도로 관리한다.

| 목적 | 환경 변수 | 기본값 | 역할 |
| --- | --- | --- | --- |
| 네이버 지역 판정 사용 | `NAVER_LOCAL_REGION_ENABLED` | `false` | `true`일 때 Naver Resolver를 선택한다. |
| 네이버 Client ID | `NAVER_MAP_CLIENT_ID` | 빈 값 | 역지오코딩 요청의 `x-ncp-apigw-api-key-id` 헤더에 사용한다. |
| 네이버 Client Secret | `NAVER_MAP_CLIENT_SECRET` | 빈 값 | 역지오코딩 요청의 `x-ncp-apigw-api-key` 헤더에 사용한다. |
| 네이버 API 주소 | `NAVER_MAP_BASE_URL` | `https://naveropenapi.apigw.ntruss.com` | Naver Cloud Platform API 기본 주소다. |
| 연결·응답 제한 | `NAVER_MAP_CONNECT_TIMEOUT`, `NAVER_MAP_READ_TIMEOUT` | `2s`, `3s` | 외부 API 호출 제한 시간이다. |
| 네이버 지역 캐시 | `NAVER_MAP_REGION_CACHE_TTL`, `NAVER_MAP_REGION_CACHE_MAX_ENTRIES` | `10m`, `10000` | 좌표별 행정구역 판정 결과 캐시 정책이다. |
| Kakao fallback 사용 | `KAKAO_LOCAL_REGION_ENABLED`, `KAKAO_REST_API_KEY` | `false`, 빈 값 | Naver를 비활성화한 경우에만 기존 Kakao Resolver를 설정할 수 있다. |
| 누락 지역 backfill | `LOCAL_HOT_REGION_BACKFILL_ENABLED` | `false` | 애플리케이션 시작 시 `region_code`가 없는 장소만 갱신한다. |
| backfill 묶음 크기 | `LOCAL_HOT_REGION_BACKFILL_BATCH_SIZE` | `100` | 한 번의 시작 작업에서 조회할 최대 장소 수다. |

Naver 전환 예시:

```env
NAVER_LOCAL_REGION_ENABLED=true
NAVER_MAP_CLIENT_ID=<server-client-id>
NAVER_MAP_CLIENT_SECRET=<server-client-secret>
LOCAL_HOT_REGION_BACKFILL_ENABLED=false
```

`NAVER_LOCAL_REGION_ENABLED=true`이면 Kakao 설정 유무와 무관하게 Naver Resolver가 선택된다. Naver를 비활성화하고 Kakao를 사용하려면 `NAVER_LOCAL_REGION_ENABLED=false`, `KAKAO_LOCAL_REGION_ENABLED=true` 및 `KAKAO_REST_API_KEY`를 함께 설정한다. 둘 다 비활성화하거나 선택된 제공자의 인증정보가 비어 있으면 좌표 기반 조회는 사용할 수 없다.

배포 Compose의 `app` 서비스는 서버 체크아웃의 `.env`를 읽는다. 값을 바꾼 뒤에는 승인된 배포 절차로 컨테이너를 재생성해야 반영된다. backfill은 기본 비활성이며, 기존 `region_code`가 있는 장소를 다시 조회하거나 변경하지 않는다.

## API 계약과 오류 확인

성공 시 응답의 `region`에는 `regionCode`, `sido`, `sigungu`, `regionName`이 포함된다. 지역에 공개·운영 중인 장소가 없더라도 지역 판정이 성공하면 `200`과 빈 `places` 목록을 반환한다.

| 응답 | 의미와 확인할 항목 |
| --- | --- |
| `200` | 좌표 지역 판정 또는 저장된 `regionCode` 조회 성공. 빈 목록은 지역 내 조회 가능한 장소가 없음을 뜻한다. |
| `404 LOCAL_HOT_REGION_NOT_FOUND` | Naver가 법정동 코드를 반환하지 않았거나, 전달한 `regionCode`가 PostgreSQL에 없다. 좌표·지역 코드와 지역 저장 상태를 확인한다. |
| `503 LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE` | 선택된 Resolver가 비활성화됐거나 인증정보가 누락됐다. 제공자 선택 플래그와 ID·Secret 존재 여부만 확인한다. |
| `502 LOCAL_HOT_REGION_RESOLUTION_FAILED` | Naver 외부 호출·응답 처리 실패다. Naver 인증 권한·쿼터, DNS·네트워크, timeout을 확인한다. |

Naver 요청 실패의 상세 원인과 인증 값은 API 응답에 노출하지 않는다. 키 존재 여부는 유효한 키·권한·외부 API 접근 성공을 보장하지 않는다.

## 안전한 설정 상태 확인

실행 컨테이너에서는 키 원문 대신 설정 여부만 확인한다.

```sh
docker compose exec -T app sh -c '
case "${NAVER_LOCAL_REGION_ENABLED:-false}" in
  true) echo "naver_region_enabled=true" ;;
  *) echo "naver_region_enabled=false_or_invalid" ;;
esac
for name in NAVER_MAP_CLIENT_ID NAVER_MAP_CLIENT_SECRET; do
  eval "value=\${$name:-}"
  if [ -n "$(printf "%s" "$value" | tr -d "[:space:]")" ]; then
    echo "$name=present"
  else
    echo "$name=absent_or_blank"
  fi
done
case "${LOCAL_HOT_REGION_BACKFILL_ENABLED:-false}" in
  true) echo "region_backfill_enabled=true" ;;
  *) echo "region_backfill_enabled=false_or_invalid" ;;
esac
'
```

설정 변경 뒤에는 인증된 국내 좌표 요청으로 `200`과 올바른 시·군·구를 확인한다. backfill을 켠 배포는 `region_code IS NULL`인 기존 장소만 대상인지, 성공·실패 집계 로그만 기록되는지 확인한 뒤 즉시 기본값인 `false`로 되돌린다.

## 지도 링크 전환 기록

`POST /places/{placeId}/map-link-conversions`의 `provider` 예시는 `NAVER`다. 이는 현재 지도 전환 기록의 기본 예시일 뿐이며, 기존 `KAKAO` 출처 기록과 과거 장소의 `kakaoPlaceId`·`GeocodingSource.KAKAO` 데이터는 제거하거나 변환하지 않는다.
