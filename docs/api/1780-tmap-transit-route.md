# TMAP 대중교통 경로 API (#1780)

기존 `POST /routes`에 `mode=transit`을 추가한다. JWT 인증, 좌표 검증, 호출 제한과
`Cache-Control: no-store` 계약은 자동차 경로와 동일하다. 자동차 `mode=car`는 기존
NAVER Directions 연동과 응답을 변경하지 않는다.

## 요청과 응답

요청은 WGS84 `origin`, `destination`과 `mode=transit`을 사용한다. 성공 응답은
`mode=transit`, `provider=tmap`과 `itineraries`를 반환한다. 각 대안 경로에는 전체
소요시간·거리·환승·도보·요금과 `legs`의 이동수단, 노선, 출발·도착, 경로 좌표,
경유 정류장이 포함된다.

TMAP의 `WALK`, `BUS`, `SUBWAY`, `EXPRESS BUS`, `TRAIN`, `AIRPLANE`, `FERRY` 구간을
공급자 응답 그대로 구분한다. 경로가 없으면 `ROUTE_NOT_FOUND`, 공급자 timeout은
`ROUTE_PROVIDER_TIMEOUT`, 비활성·키 누락·인증·쿼터·장애·비정상 응답은
`ROUTE_PROVIDER_UNAVAILABLE`을 반환한다.

HTTP 400은 `INVALID_ROUTE_REQUEST`, HTTP 404는 `ROUTE_NOT_FOUND`로 구분한다. 401, 403,
429, 5xx와 그 밖의 공급자 오류는 내부 상태를 노출하지 않고 `ROUTE_PROVIDER_UNAVAILABLE`로
처리한다.

## 운영 설정

```text
TMAP_TRANSIT_ENABLED=true
TMAP_TRANSIT_APP_KEY=<secret>
TMAP_TRANSIT_BASE_URL=https://apis.openapi.sk.com
TMAP_TRANSIT_CONNECT_TIMEOUT=2s
TMAP_TRANSIT_READ_TIMEOUT=8s
TMAP_TRANSIT_REQUEST_TIMEOUT=10s
```

appKey는 환경 secret으로만 관리하며 요청·응답·로그에 기록하지 않는다. TMAP 응답은
DB나 캐시에 저장하지 않아 공급자 약관의 24시간 보관 제한을 침범하지 않는다. 자동
재시도와 redirect를 비활성화해 과금 중복과 인증 헤더 유출을 방지한다.

공식 규격: <https://openapi.sk.com/products/detail?svcSeq=59&menuSeq=492>
