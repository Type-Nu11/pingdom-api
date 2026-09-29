# 앱 내부 자동차 경로 API (#1735)

## 구현 계약

`POST /routes`, `Authorization: Bearer <access token>`이 필요하다. 기존 JWT 필터와 계정 상태 검증을 그대로 사용한다. 추가 디바이스 서명 헤더, 공급자 키, 장소 ID는 요청하지 않는다. 문서는 `/v3/api-docs/app`, Swagger의 `장소 탐색` 그룹에 포함된다.

```json
{
  "origin": { "latitude": 37.5665, "longitude": 126.9780 },
  "destination": { "latitude": 37.4979, "longitude": 127.0276 },
  "mode": "car"
}
```

- WGS84 JSON 숫자: latitude [-90, 90], longitude [-180, 180]. 누락/null/숫자 문자열/비유한값/동일 좌표는 400이다.
- `car`만 지원한다. `walk`, `transit`, `bike`는 422이며 다른 값은 400이다. 자동차나 직선 경로로 대체하지 않는다.
- 조회 시점의 `traoptimal` 배열 첫 번째 추천 경로를 반환한다. 경유지, 차량 옵션, 미래 출발 시각, 복수 경로는 받지 않는다.
- 성공 응답은 별도 envelope 없이 아래 필드로 반환한다. 아래는 **합성 형식 예시이며 실제 호출 증거가 아니다.**

```json
{
  "mode": "car",
  "provider": "naver",
  "distanceMeters": 12500,
  "durationSeconds": 1800,
  "path": [
    { "latitude": 37.56, "longitude": 126.97 },
    { "latitude": 37.52, "longitude": 127.01 },
    { "latitude": 37.49, "longitude": 127.02 }
  ]
}
```

`path`는 공급자의 [longitude, latitude]를 이름 있는 객체로 변환한 전체 도로 형상이며 출발→도착 순서를 유지한다. 입력 좌표로 끝점을 바꾸거나 단순화하지 않는다. 거리는 공급자의 m 값, 시간은 ms를 `ceil(ms / 1000)`으로 올린 0 이상의 정수 초다. 공급자 성공 응답에 경로 2점 미만·잘못된 좌표·누락/음수 거리·시간이 있으면 503이다. 부정확한 성공 응답을 생성하지 않는다.

## 오류와 프론트 처리

공통 `{ "message": "공개 메시지", "code": "안정된 코드" }` 형식을 사용한다.

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_ROUTE_REQUEST | 잘못된 입력. 공급자 코드 1(동일 지점), 5(거리 제한) 포함 |
| 422 | UNSUPPORTED_ROUTE_MODE | walk/transit/bike. 외부 지도 이동 안내 |
| 422 | ROUTE_NOT_FOUND | 공급자 코드 2/3/4. 도로 주변이 아니거나 자동차 경로 없음 |
| 401/403 | 기존 인증·권한 코드 | JWT/계정 상태/접근 권한 오류 |
| 429 | ROUTE_RATE_LIMITED | 사용자/IP별 호출 제한. Retry-After 이후 재시도 |
| 503 | ROUTE_PROVIDER_UNAVAILABLE | 비활성/키 미설정, 공급자 인증 실패·쿼터·장애·비정상 응답 |
| 503 | RATE_LIMIT_UNAVAILABLE | 기존 제한 저장소 장애 정책에 따라 차단 |
| 504 | ROUTE_PROVIDER_TIMEOUT | 연결/읽기/전체 응답 타임아웃 또는 공급자 HTTP 504 |

네이버가 지역 미지원을 별도 코드로 보장하지 않으므로 `UNSUPPORTED_ROUTE_REGION`을 추측해 반환하지 않는다. 공급자 401/403은 사용자의 JWT 오류로 전달하지 않고 503으로 정규화한다. 공급자 429도 사용자 429와 분리한다.

```json
{"message":"자동차 경로를 찾을 수 없습니다.","code":"ROUTE_NOT_FOUND"}
```
```json
{"message":"지원하지 않는 이동수단입니다.","code":"UNSUPPORTED_ROUTE_MODE"}
```
```json
{"message":"경로 공급자 응답 시간이 초과되었습니다.","code":"ROUTE_PROVIDER_TIMEOUT"}
```

## 설정과 운영

| 환경 변수 | 기본값 | 의미 |
| --- | --- | --- |
| NAVER_DIRECTIONS_ENABLED | false | 계정 이용 조건 확인 후 활성화 |
| NAVER_DIRECTIONS_CLIENT_ID | 없음 | 서버 전용 Client ID |
| NAVER_DIRECTIONS_CLIENT_SECRET | 없음 | 서버 전용 Client Secret |
| NAVER_DIRECTIONS_BASE_URL | https://maps.apigw.ntruss.com | Maps 계정 상품에 맞는 HTTPS 호스트 |
| NAVER_DIRECTIONS_CONNECT_TIMEOUT | 2s | 연결 제한 시간 |
| NAVER_DIRECTIONS_READ_TIMEOUT | 8s | 개별 socket 읽기 제한 시간 |
| NAVER_DIRECTIONS_REQUEST_TIMEOUT | 10s | 연결 풀 대기부터 전체 본문 수신까지 제한 시간 |
| ROUTE_QUERY_USER_LIMIT / ROUTE_QUERY_USER_WINDOW | 10 / PT1M | 사용자별 제한 |
| ROUTE_QUERY_IP_LIMIT / ROUTE_QUERY_IP_WINDOW | 100 / PT1M | IP별 제한 |

경로 조회 전용 Apache HttpClient 5 비동기 클라이언트를 사용한다. Spring Boot가 관리하는 버전을 적용하며 다른 공급자 클라이언트 설정은 변경하지 않는다. 연결 2초·읽기 8초와 별도로, 연결 풀 대기부터 전체 본문 수신까지 기본 10초 제한을 적용한다. 제한 시간을 넘기거나 대기 스레드가 중단되면 실제 HTTP 요청을 취소한다. 응답 데이터를 조금씩 계속 보내는 공급자도 전체 제한 시간을 연장할 수 없다. 자동 재시도와 리다이렉트는 클라이언트 설정으로 비활성화한다. 연결 풀은 전체/호스트별 최대 20개, I/O 스레드는 2개다. 기존 Redis 호출 제한을 재사용하고 신규 캐시/DB 스키마는 추가하지 않는다. 제한 창은 첫 요청 이후 유지되는 기존 Redis 정책이며 429의 `Retry-After`는 사용자/IP 창 중 긴 기간을 초 단위로 올린 안전한 대기 상한이다. 잘못된 요청도 Controller AOP 진입 시 제한에 포함될 수 있다.

경로를 DB나 캐시에 저장하지 않으며 성공 응답은 `Cache-Control: no-store`다. 공급자 원문 예외/응답, 키, 좌표를 직접 로깅하지 않는다. 운영에서 HTTP client wire/TRACE 로그나 요청 본문 수집 기능을 별도로 켜지 않는다.

`pingdom.route.provider.duration` Timer의 count/totalTime/max로 호출 수·응답시간을 관찰한다. `provider=naver`, `outcome=success|invalid_request|not_found|authentication|quota|timeout|unavailable|invalid_response`만 사용한다. 키/좌표/사용자 식별자를 태그에 넣지 않는다. 비활성·키 미설정은 공급자를 호출하지 않으므로 Timer에 집계하지 않는다. 성공률은 success count / 전체 count, 기술 실패율은 authentication/quota/timeout/unavailable/invalid_response count / 전체 count로 계산한다.

## 공급자 이용 및 활성화 전 확인

코드 연동 대상은 NAVER Directions 5로 선택했다. 자동차 지원, 경도→위도 입력, 거리 m·시간 ms, 요청 옵션·오류 코드는 [Directions 5 공식 문서](https://api.ncloud-docs.com/docs/application-maps-directions5)를 기준으로 한다. 현재 Maps 기본 호스트 및 인증 헤더는 [Maps 공통 문서](https://api.ncloud-docs.com/docs/application-maps-overview)를 따른다.

실제 계정 콘솔·상품 계약은 이 변경으로 확인되지 않는다. 따라서 서비스 제공 가능, 유료 사용 승인, 쿼터, 출처 의무를 확인했다고 주장하지 않는다. 다음 항목을 운영 담당자가 확인한 뒤 활성화한다.

- 기존 계정의 Maps 상품과 Directions 5 사용 선택 여부, 적용 호스트 및 전용 키 준비.
- 실제 계정 요금제·무료 제공 조건·월/일 쿼터·월 비용 상한. [상품/요금 안내](https://www.ncloud.com/product/applicationService/maps)와 계정 콘솔을 함께 확인한다.
- 앱 내부 네이버 지도 표시, 로고/출처/링크 요구사항. 필수 표기를 프론트 #381 인계 문서에 확정한다.
- 캐시는 사용하지 않는다. 추후 저장/캐시를 추가하면 별도 조건을 다시 확인한다.
- 기능 해제 시 `NAVER_DIRECTIONS_ENABLED=false` 후 환경 설정이 반영되는 방식으로 재시작하여 503과 공급자 미호출을 확인한다.

## 검증과 인계 경계

- 단위/로컬 HTTP: 실제 외부 호출 없이 좌표·단위·경로 원본 유지·오류·비활성·비밀값 보호·호출 제한을 검증한다.
- `RouteApiIntegrationTest`: 실제 JWT 필터·Controller AOP·OpenAPI를 확인한다. 공급자·Redis·계정 상태 조회는 mock이며 운영 프록시를 검증하지 않는다.
- 실제 공급자: 국내 자동차 이동 가능한 좌표 2쌍 이상으로 원본/정규화 단위와 경로를 확인해야 한다. 실패 사례는 운영 장애를 유발하지 말고 테스트 결과와 구분해 인계한다.
- 배포: 앱 HTTPS 환경에서 유효 JWT로 성공 응답, 무효/누락 JWT 401, HTTP/내부 포트 리다이렉트 없음, 호출 제한과 지표를 확인해야 한다.
- 최종 인계: 환경 URL, `/v3/api-docs/app`, `POST /routes`, JWT, 비밀값 없는 실제 응답, 확정 출처 표기 조건을 프론트 #381에 전달한다. 실기기 렌더링은 프론트 담당이다.

계정 활성화·실제 공급자 호출·배포·프론트 전달 전에는 이슈 전체 완료로 표시하지 않는다.

### 이번 변경의 확인 결과 (2026-09-29)

- JDK 21에서 운영/테스트 코드 컴파일 및 직접 관련 6개 클래스 **99개 테스트 통과**.
- 최초 범위: `NaverDirectionsClientTest` 37, `RouteQueryServiceTest` 22, `RouteControllerTest` 16, `AbuseRateLimitServiceTest` 13, `RedisRateLimitStoreTest` 7, `ControllerConventionTest` 4.
- 기존 build 파일 해시 읽기가 대기 상태여서 산출물을 삭제하지 않고 `/tmp/pingdom-1735-NoGwwP/build`에 분리해 검증했다.
- 리뷰 수정 후 `NaverDirectionsClientTest` 37, `NaverDirectionsTransportTest` 7, `RouteControllerTest` 16으로 총 **60개 테스트 재검증 통과**. 로컬 소켓으로 느린 분할 응답의 전체 시간 제한과 연결 취소, 호출 스레드 중단, 연결 중단·429·503에서 재시도 금지, 리다이렉트 금지, 전체 본문 수신을 검증했다. 응답 좌표의 내부 `valid` 속성도 노출하지 않는다.
- `RouteApiIntegrationTest` **5개 통과**: JWT 누락·refresh token 거부, 디바이스 헤더 없는 유효 JWT 성공, 공급자 호출 전 429 차단, 앱 OpenAPI 계약·그룹 분리. 공급자·Redis·계정 상태는 mock이다. 전체 테스트는 실행하지 않았다.
- 로컬 `.env`에 Directions 전용 설정 키가 선언되어 있지 않음을 확인했다. 키 원문은 출력하지 않았다. 실제 계정 상품 활성화·요금·표시 조건, 공급자 호출, 배포 검증 및 프론트 인계는 미완료다.
