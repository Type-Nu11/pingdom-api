# 관리자 장소 지도 API 계약 (#1820)

프론트 전달용 v1 (대표 장소 확장: #1823). 관리자 목록의 페이지와 독립적으로 지도 영역을 조회한다.
기존 공개 지도 API와 관리자 목록·상세 API는 변경하지 않는다.

## 인증 및 공통 규칙

- 기존 관리자 JWT/세션 인증 및 `@AdminOnly` 권한 정책을 사용한다.
- 성공 응답은 DTO 직접 반환. 오류는 기존 `{ "message": "...", "code": "..." }` 형식이다.
- 좌표는 WGS84 경도/위도, 화면 크기는 device pixel이 아닌 CSS pixel이다.
- 목록의 page/limit/sortParam을 지도 요청에 전달하지 않는다.
- 숫자 ID는 기존 API와 동일한 JSON number다.

## 1. 지도 조회

`GET /admin/places/map`

| 파라미터 | 필수 | 규칙/기본값 |
|---|---|---|
| west, south, east, north | O | 유한한 숫자. 경도 -180~180, 위도 -85.05112878~85.05112878. west < east, south < north. 날짜변경선 횡단 미지원 |
| zoom | O | 정수 0~20 |
| width, height | O | 각각 정수 64~8192 CSS px |
| keyword | X | trim 후 최대 100자. 기본 빈 문자열. 기존 목록처럼 장소명·영문명·주소 부분 검색, 숫자는 등록자 ID 검색도 적용 |
| category | X | 기존 관리자 목록과 동일한 카테고리 별칭 정규화. 알 수 없는 값은 OTHER로 정규화 |
| discoveryStatus | X | VISIBLE / HIDDEN. 생략하면 모두 포함 |
| operatingStatus | X | OPERATING / TEMPORARILY_CLOSED / PERMANENTLY_CLOSED. 생략하면 모두 포함 |

유효한 좌표와 PostGIS location이 있는 장소만 지도 대상으로 삼는다. 물리 삭제된 장소는 제외된다.
location과 위경도는 일치해야 하며 불일치 레코드는 제외한다. 지역 전체 수는 좌표 유무와 무관하게
유효 지역 코드가 연결된 장소를 포함한다. 필터는 지역·공간·그룹 내부 목록에 동일하게 적용된다.

```http
GET /admin/places/map?west=128.50&south=35.80&east=128.70&north=35.95&zoom=13&width=1200&height=800&category=CAFE
```

### 표현 및 집계

| zoom | mode | 표현 |
|---|---|---|
| 0~8 | SIDO | 시·도 전체 수 + 지역 미연결 장소의 공간 그룹/마커 |
| 9~11 | SIGUNGU | 시·군·구 전체 수 + 지역 미연결 장소의 공간 그룹/마커 |
| 12~20 | SPATIAL | 공간 그룹과 단일 장소 마커 |

프론트는 zoom으로 응답 구조를 추정하지 않고 mode 및 각 배열을 사용한다. 모든 배열은 항상 존재한다.
지역은 **현재 화면 안에 해당 필터의 장소가 한 개 이상 있는 경우** 선택한다. 행정 경계 교차 판정은
현재 경계 데이터가 없어 제공하지 않는다. regions.bounds는 해당 지역의 필터 적용 후 유효 좌표 장소 범위이며
행정 경계가 아니다(`boundsSource=PLACE_EXTENT`). 표시 좌표는 현재 화면 안에 있는 동일 필터 장소의 평균이므로 화면 밖으로 나가지 않는다. 확대 bounds는 지역 전체 장소 범위를 유지한다.
지역 코드 미연결 장소는 clusters/markers로 별도 표시하여 조용히 누락시키지 않는다.

- regions[].count: REGION 범위, 동일 필터의 지역 전체 장소 수. 좌표 없는 장소도 지역 코드가 있으면 포함.
- viewportPlaceCount: 현재 화면 안 동일 필터·유효 좌표 장소 수. 지역 전체 수와 합산하지 않는다.
- clusters[].count: VIEWPORT 범위, 현재 요청 영역과 해당 격자의 교집합 장소 수.
- 단일 장소는 마커, 2개 이상은 그룹. 공간 그룹과 마커 사이에 중복이 없다.
- 지역 모드에서는 지역 집계와 미연결 장소의 공간 결과 사이에 중복이 없다.
- 지역 없는 장소, 좌표 없는 장소는 기존 관리자 목록에서 계속 관리할 수 있다.

### 응답 예시: 공간 혼합

```json
{
  "mode": "SPATIAL",
  "zoom": 13,
  "regions": [],
  "clusters": [{
    "clusterKey": "v1:filterHash:resolution:x:y",
    "clusterId": "opaque-token",
    "count": 24,
    "representative": {
      "id": 1810, "name": "대표 카페", "latitude": 35.872, "longitude": 128.602,
      "category": "CAFE", "discoveryStatus": "VISIBLE", "operatingStatus": "OPERATING"
    },
    "latitude": 35.871,
    "longitude": 128.601,
    "bounds": {"west":128.600,"south":35.870,"east":128.603,"north":35.873},
    "countScope": "VIEWPORT",
    "action": "EXPAND", "nextZoom": 14
  }],
  "markers": [{
    "id":1820,"name":"예시 카페","latitude":35.875,"longitude":128.610,
    "category":"CAFE","discoveryStatus":"HIDDEN","operatingStatus":"OPERATING"
  }],
  "viewportPlaceCount": 25,
  "complete": true,
  "incompleteReason": null,
  "recoveryAction": null
}
```

### 응답 예시: 지역 집계

```json
{
  "mode":"SIGUNGU",
  "zoom":10,
  "regions":[{
    "regionLevel":"SIGUNGU","regionCode":"27260","name":"대구광역시 수성구",
    "count":320,"countScope":"REGION","latitude":35.858,"longitude":128.631,
    "bounds":{"west":128.60,"south":35.80,"east":128.76,"north":35.90},
    "boundsSource":"PLACE_EXTENT","action":"EXPAND","nextZoom":12
  }],
  "clusters":[],"markers":[],"viewportPlaceCount":120,
  "complete":true,"incompleteReason":null,"recoveryAction":null
}
```

정상 0건: 해당 zoom의 mode, 배열은 모두 [], viewportPlaceCount=0, complete=true.

### 표시량 및 탐색

- regions + clusters + markers 합계 초기 서버 상한은 200개이며 `admin.place-map.max-items`(환경변수 `ADMIN_PLACE_MAP_MAX_ITEMS`, 허용 4~2000)로 조정한다. 작은 화면은 CSS 면적에 따라 더 낮은 공간 표시 예산을 사용한다.
- 고정 Web Mercator 원점·zoom별 기본 64 CSS px 격자 사용. 화면 이동으로 원점은 변하지 않는다.
- 상한 초과 시 격자 크기를 2배씩 키워 재집계하며, 임의 LIMIT 결과를 전체처럼 반환하지 않는다.
- 지역 수 자체가 상한을 초과하면 전체 요청 영역을 SPATIAL로 전환한다.
- 유한한 재집계 한도 초과 시 배열을 비우고 complete=false,
  incompleteReason=RESULT_BUDGET_EXCEEDED, recoveryAction=ZOOM_IN을 반환한다.
- EXPAND: bounds로 fitBounds한 뒤 zoom이 nextZoom보다 작으면 nextZoom까지 확대한다. SIDO의 nextZoom은 9, SIGUNGU는 12, 공간 그룹은 현재 zoom+1이다. 반드시 현재 단계보다 확대한다. bounds가 점 또는 선이면 지도 SDK의 padding/minimum span을 적용한다.
- clusterKey: 필터·미연결 범위·격자 해상도·셀 좌표로 결정되는 표시용 키. 같은 필터/해상도/셀은 지도 이동과 토큰 재발급에도 동일하다. 필터나 재집계 해상도가 바뀌면 변경된다. clusterId는 만료되는 목록 조회 토큰으로 표시 키로 사용하지 않는다.
- LIST: nextZoom=null. 그룹 내부 장소 목록을 연다. 같은 좌표의 그룹 또는 zoom=20 그룹은 LIST다.
- 지도 이동/필터 변경 시 이전 요청 취소 + 요청 순번 검사로 늦은 응답을 버린다.

## 2. 그룹 내부 장소 목록

`GET /admin/places/map/clusters/{clusterId}/places?page=1&limit=20`

- page: 기본 1, 1~1000000. limit: 기본 20, 1~100. 범위 밖은 400.
- ID 오름차순. 목록 항목은 위 markers와 동일한 최소 필드다.
- 필터는 다시 받지 않는다. 토큰에 원래 필터·요청 영역·고정 격자·집계 정책 v1을 결합한다.
- 토큰은 서버 서명으로 변조를 방지하며 5분간 유효하다. 권한은 매 요청 재검사한다.
- 그룹 구성/표시 필드 변경을 지문으로 감지하면 409로 거부한다. 다른 그룹으로 자동 해석하지 않는다.
- 토큰은 동일 셀이라도 조회 영역·필터가 다르면 별도 탐색 문맥이다. 클라이언트에서 분해/영속 저장하지 않는다.
- 페이지 요청마다 같은 그룹인지 검증한다. 데이터 스냅샷 저장 기능은 제공하지 않는다.

```json
{
  "places":[{"id":1820,"name":"예시 카페","latitude":35.875,"longitude":128.610,
    "category":"CAFE","discoveryStatus":"HIDDEN","operatingStatus":"OPERATING"}],
  "page":1,"limit":20,"totalCount":24,"totalPages":2,"hasNext":true
}
```

장소 클릭 시 기존 `GET /admin/places/{id}`를 호출한다.

## 오류 및 최신성

| HTTP | code | 프론트 처리 |
|---|---|---|
| 400 | ADMIN_PLACE_MAP_INVALID_REQUEST | 경계·크기·zoom·페이지·필터 입력 확인. 바인딩/Validation 오류는 기존 공통 오류 코드 적용 |
| 401 / 403 | 기존 인증/관리자 권한 오류 코드 | 기존 로그인/권한 처리 |
| 409 | ADMIN_PLACE_MAP_CLUSTER_INVALID | 토큰 변조·만료·그룹 변경·정책 버전 변경. 지도 재조회 후 다시 선택 |
| 503 | ADMIN_PLACE_MAP_QUERY_TIMEOUT | 잠시 후 사용자 재조회. 자동 무한 재시도 금지 |

v1은 응답/지역 집계 캐시와 사전 집계를 사용하지 않는다. HTTP Cache-Control: no-store.
변경 커밋 후 다음 조회에 반영되며 응답 내 여러 SQL은 동일 REPEATABLE_READ 스냅샷을 사용한다.
그룹 토큰의 5분 유효기간은 응답 캐시 TTL이 아니다. SQL statement timeout은 쿼리당 3초의 중단 기준이며 전체 API 응답시간 보장이 아니다. 전체 서버 응답시간은 기존 Actuator `http.server.requests`의 지도/그룹 URI 템플릿별 timer로 별도 측정한다(응답 직렬화 포함, 네트워크 왕복 제외). 대구 전체·밀집 영역의 실제 데이터 결과를 기준으로 상한과 확대 정책을 조정한다.
실제 대구 데이터의 실행 계획·응답 시간은 운영 승인 데이터로 별도 측정해야 하며 200개는 성능 보장값이 아니다.

## 프론트 구현 순서

1. 지도 idle(이동·확대 완료) 이벤트를 debounce하고 경계/zoom/크기/필터로 요청한다.
2. 최신 요청 응답의 regions, clusters, markers를 모두 표시한다.
3. 지역 및 EXPAND 그룹은 확대, LIST 그룹은 페이지 목록, 마커는 기존 상세를 연다.
4. 필터 변경 시 이전 그룹 목록/토큰을 폐기한다. 409는 지도 재조회, complete=false는 확대 안내한다.

## 구현 검증 기록

- 전체 단위 테스트 1,455개 통과 후 최종 변경 관련 단위/공개 지도 회귀 19개, PostGIS 7개, 실제 인증 경로 2개 통과.
- 대구 범위를 사용하는 가상 장소 10,000건: HTTP dispatch·응답 직렬화 포함 약 347ms, 공간 집계 SQL 실행 약 74ms, 120개 그룹으로 10,000건 누락 없이 표현.
- 동일 건물 30건 그룹 유지·페이지 조회·변경 후 409, 화면 밖 장소를 포함한 지역 전체 수와 화면 안 대표 좌표, 고정 격자/표시 키 안정성을 확인했다.
- 위 수치는 로컬 단일 실행·가상 데이터이며 운영 대구 실데이터나 p95 보장값이 아니다. 초기 상한 200과 zoom 경계는 유지한다.
- 변경 전 HEAD의 생성 OpenAPI와 비교해 기존 경로/스키마 변경이 없음을 확인하고 신규 두 경로와 지도 전용 스키마만 baseline에 추가했다.

## 대표 장소 확장 (#1823)

- clusters[].representative는 항상 존재하며 markers 항목과 동일한 필드를 사용한다.
- 대표는 현재 요청 영역·필터·그룹에 속하는 실제 장소 중 ID 오름차순 첫 장소다.
- 대표 아이콘은 representative.latitude/longitude에 표시한다. 기존 cluster.latitude/longitude는 그룹 평균 좌표로 호환성을 위해 유지하며 새 아이콘 위치에 사용하지 않는다.
- 동일 구성의 대표는 고정된다. 고정 원점의 2배 단위 격자를 세분화하므로 기존 대표가 남아 있는 하위 그룹에서는 동일 장소가 계속 최소 ID다. 단일 장소로 분리되면 그 장소는 markers로 이동한다.
- 화면 이동·필터·데이터 변경으로 기존 대표가 빠지면 남은 대상 중 최소 ID로 교체한다.
- count는 대표를 포함한다. +N은 count - 1이며 대표를 markers에도 중복 반환하지 않는다. 표시 예산은 기존처럼 그룹 하나를 한 항목으로 센다.
- 대표 아이콘 클릭은 기존 장소 상세 API, +N 클릭은 기존 EXPAND/LIST 및 nextZoom 동작을 사용한다.
- 대표와 그룹 목록은 같은 조회 스냅샷·조건을 사용한다. 기존 지문에 모든 구성원의 ID·이름·좌표·분류·상태가 포함되므로 대표 변경도 기존 토큰 409 검증에 반영된다.
- 지역명·장소 수 표시 방식 변경은 프론트 표현 변경이며 regions 응답 계약은 유지한다.
- 확장 검증: 단위 테스트 1,457개, PostGIS 8개 통과. 관리자/전체 OpenAPI 호환성 검사 통과.
