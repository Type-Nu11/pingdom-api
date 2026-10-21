# 관리자 장소 지도 API 계약 v2 (#1825)

`GET /admin/places/map`에서 현재 영역의 장소 수로 전체 마커 표시 가능 여부를 판단한다.
초기 정책은 **0~100개 전체 표시, 101개 이상 마커 없이 정확한 개수와 확대 안내**다.
목록 페이지·zoom·화면 크기는 표시 대상을 줄이는 조건이 아니다.

## 인증 및 요청

기존 관리자 JWT/세션 및 `@AdminOnly`를 사용한다. 성공 응답은 DTO이며 오류는 기존
`{"message":"...","code":"..."}` 형식이다. 응답은 `Cache-Control: no-store`다.

| 파라미터 | 필수 | 규칙 |
|---|---|---|
| west, south, east, north | O | WGS84. 유한한 숫자, 경도 -180~180, 위도 -85.05112878~85.05112878. west < east, south < north. 날짜변경선 횡단 미지원 |
| zoom | O | 정수 0~20. 호환 입력이며 표시 가능 여부에 영향 없음 |
| width, height | O | 각각 정수 64~8192 CSS px. 호환 입력이며 표시 가능 여부에 영향 없음 |
| keyword | X | 최대 100자, trim. 장소명·영문명·주소 부분 검색, 숫자는 등록자 ID 검색도 적용 |
| category | X | 기존 목록과 같은 카테고리 별칭 정규화. 알 수 없는 값은 OTHER |
| discoveryStatus | X | VISIBLE / HIDDEN. 생략하면 모두 포함 |
| operatingStatus | X | OPERATING / TEMPORARILY_CLOSED / PERMANENTLY_CLOSED. 생략하면 모두 포함 |

```http
GET /admin/places/map?west=128.50&south=35.80&east=128.70&north=35.95&zoom=0&width=1200&height=800&category=CAFE
```

목록의 page/limit/sortParam 및 클라이언트 표시 한도 파라미터는 제공하지 않는다.
기존 `GET /admin/places` 기본 limit=20과 목록·상세·공개 지도 API는 그대로다.
장소 클릭은 `GET /admin/places/{id}`를 호출한다.

## 집계와 표시 상태

동일 필터·bbox의 유효 좌표 장소를 DB에서 `COUNT(*)`로 집계한다. 좌표/location 누락·빈 geometry·범위 밖 좌표·위경도와 location 불일치는 제외한다.
지역 코드가 없거나 연결되지 않아도 포함하고, 같은 좌표의 서로 다른 ID를 합치지 않는다.
집계와 마커 조회는 동일 `REPEATABLE_READ` 스냅샷을 사용한다.

| 현재 영역 수 | displayStatus | markers | complete | incompleteReason / recoveryAction |
|---|---|---|---|---|
| 0 | MARKERS | [] | true | null / null |
| 1~100 | MARKERS | 전체 장소, ID 오름차순 | true | null / null |
| 101 이상 | ZOOM_REQUIRED | [] | false | RESULT_BUDGET_EXCEEDED / ZOOM_IN |

- `viewportPlaceCount`: 정확한 현재 영역 전체 수. 마커 배열 길이나 목록 전체 건수와 혼동하지 않는다.
- `displayLimit`: 서버에서 실제 적용한 한도. 기본 100. `admin.place-map.display-limit` / `ADMIN_PLACE_MAP_DISPLAY_LIMIT`로 1~100 사이에서 낮출 수 있다. 이전 표시 객체 예산 설정 `max-items` / `ADMIN_PLACE_MAP_MAX_ITEMS`는 사용하지 않는다.
- `displayStatus`: 프론트가 사용할 최종 표시 상태. 100개에서는 markers가 정확히 100개다. 표의 경계는 실제 displayLimit에 적용한다.
- `mode`: 호환상 항상 `SPATIAL`. zoom으로 지역 모드를 선택하지 않는다.
- `regions`, `clusters`: 호환상 항상 빈 배열. 대표 장소·그룹 토큰을 만들지 않는다.
- 101개 이상도 **HTTP 200**이다. 집계 후 마커 상세 조회·지역/격자 재집계·대표 조회를 수행하지 않는다.

### 정상 1건: 축소 상태에서도 전체 마커

```json
{
  "mode": "SPATIAL", "zoom": 0,
  "regions": [], "clusters": [],
  "markers": [{
    "id": 1820, "name": "예시 카페", "latitude": 35.875, "longitude": 128.610,
    "category": "CAFE", "discoveryStatus": "HIDDEN", "operatingStatus": "OPERATING"
  }],
  "viewportPlaceCount": 1, "displayLimit": 100, "displayStatus": "MARKERS",
  "complete": true, "incompleteReason": null, "recoveryAction": null
}
```

### 정상 0건

```json
{
  "mode": "SPATIAL", "zoom": 13, "regions": [], "clusters": [], "markers": [],
  "viewportPlaceCount": 0, "displayLimit": 100, "displayStatus": "MARKERS",
  "complete": true, "incompleteReason": null, "recoveryAction": null
}
```

### 101건: 최대 확대에서도 한도 초과

```json
{
  "mode": "SPATIAL", "zoom": 20, "regions": [], "clusters": [], "markers": [],
  "viewportPlaceCount": 101, "displayLimit": 100, "displayStatus": "ZOOM_REQUIRED",
  "complete": false, "incompleteReason": "RESULT_BUDGET_EXCEEDED", "recoveryAction": "ZOOM_IN"
}
```

프론트는 기존 마커를 비우고 실제 개수와 “이 영역에 장소가 많습니다. 지도를 확대해주세요.”를 표시한다.
최대 확대·동일 좌표 밀집 등으로 줄지 않으면 기존 목록 검색·페이지로 관리한다. 자동 무한 확대/재조회는 하지 않는다.

## v1 소비자와 전환

2026-10-22 확인한 `origin/release` 소스는 #1821의 지역/그룹 v1이며,
`origin/develop`에는 #1824의 대표 장소 계약이 병합되어 있다.
관련 프론트 이슈 `Type-Nu11/pingdom-admin#330`은 계획 변경으로 종료되었고, #331이 새 정책을 추적한다.
운영 서버에 관리자 인증으로 실조회한 결과는 아니다.

같은 경로의 **의미 변경**이므로 서버 반영과 프론트 #331의 계약 전환을 조율해야 한다.
배포 후 `/v3/api-docs/admin`의 두 새 필드·예시를 대조하고 인증된 실제 조회를 확인한다.
필드 추가와 빈 배열 유지가 v1의 지역/그룹 동작까지 보존한다는 의미는 아니다.
v1 `complete=true`는 그룹을 통한 전체 표현이었고, v2는 모든 **개별 마커**를 반환했다는 뜻이다.

기존 `GET /admin/places/map/clusters/{clusterId}/places?page=1&limit=20`는 이전 토큰의 호환 조회로 유지한다.
토큰의 5분 만료·변조/구성/표시 필드 변경 409, 기존 page/limit 검증·인증·no-store는 유지한다.
v2는 토큰을 발급하지 않으며 새 프론트 흐름에서는 이 endpoint를 호출하지 않는다.
지역 데이터·집계 코드와 호환 endpoint를 일괄 삭제하지 않는다.
병합된 `Cluster.representative` 필드와 필수 스키마도 호환상 유지하되 v2 지도는 clusters를 비워 반환한다.

#1823 / 병합된 PR #1824는 대표 장소 방식이며 #1825가 제품 방향을 대체한다.
새 구현은 #1824가 병합된 develop 위로 rebase하고, 대표 선정 실행 경로를 전체 마커 정책으로 전환한다.
기존 그룹 토큰 조회·응답 스키마는 유지하며 프론트 #331은 v2의 표시 상태를 사용한다.

## 오류·최신성·프론트 처리

| HTTP | code | 처리 |
|---|---|---|
| 400 | ADMIN_PLACE_MAP_INVALID_REQUEST 또는 기존 바인딩/Validation 오류 | 경계·크기·zoom·필터 입력 확인 |
| 401 / 403 | 기존 인증/관리자 권한 오류 코드 | 기존 로그인/권한 처리 |
| 409 | ADMIN_PLACE_MAP_CLUSTER_INVALID | 호환 그룹 토큰 만료·변경 |
| 503 | ADMIN_PLACE_MAP_QUERY_TIMEOUT | 조회 실패 안내와 사용자 재시도. 초과 상태/0건과 구분 |

SQL `statement_timeout`은 기존 쿼리당 3초다. 전체 응답시간 보장이 아니다.
응답 캐시·사전 집계를 추가하지 않는다. 다음 조회는 커밋된 변경을 반영한다.
프론트는 idle을 debounce하고 이전 요청을 취소하며 늦은 응답을 무시한다.
목록 페이지 변경·정상 응답마다 자동 fit하지 않고, 장소 수정/삭제 후에는 현재 영역을 재조회한다.

## 검증과 운영 측정

재현 명령:

```bash
./gradlew test
./gradlew postgresIntegrationTest --tests '*AdminPlaceMapQueryRepositoryPostgreSqlIntegrationTest'
./gradlew postgresSmokeTest --tests '*AdminPlaceMapQueryRepositoryPostgreSqlIntegrationTest'
./gradlew integrationTest --tests '*AdminPlaceMapOpenApiContractTest' --tests '*AdminMapPlaceControllerTest.adminMap*'
./gradlew verifyOpenApiContract
```

실제 PostGIS 회귀는 0/1/99/100/101/10,000건, 낮은/높은 zoom, 같은 좌표 ID 보존,
미연결 지역·모든 상태/필터·경계·잘못된 좌표, count 후 별도 연결의 101번째 장소 삽입,
호환 그룹 토큰과 3초 timeout을 검증한다. 100개 초과에서는 count 쿼리만 실행되는지도 확인한다.
같은 회귀를 PR의 `postgresSmokeTest` 게이트에도 포함한다.
가상 10,000건 HTTP 시간과 count의 `EXPLAIN (ANALYZE, BUFFERS)`는 테스트 출력에 별도 기록한다.
이번 로컬 단일 실행은 HTTP dispatch·직렬화 포함 약 134ms, count SQL 실행 약 5.6ms였다.
모든 장소가 같은 좌표인 가상 fixture이며 운영 데이터의 분포·부하를 재현한 결과는 아니다.

운영 실데이터의 응답시간·실행 계획은 아직 측정하지 않았다. 로컬 가상 데이터·mock·통합 테스트를
운영 측정으로 해석하지 않는다. 100은 초기 제품 정책이며 렌더링·서버 성능 보장값이 아니다.
