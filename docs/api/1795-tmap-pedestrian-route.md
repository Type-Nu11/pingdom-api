# TMAP 보행자 경로 API (#1795)

기존 `POST /routes`에 `mode=walk`을 추가한다. JWT 인증, WGS84 좌표 검증, 사용자/IP 호출 제한과
`Cache-Control: no-store` 계약은 자동차·대중교통 경로와 동일하다. 자동차 `mode=car`와 대중교통
`mode=transit`의 공급자 및 응답은 변경하지 않는다.

## 요청과 응답

요청은 기존 `origin`, `destination`, `mode` 구조를 사용한다. 서버는 TMAP
`POST /tmap/routes/pedestrian?version=1`을 호출하며 공급자 `appKey`를 클라이언트에 노출하지 않는다.
현재 추천 경로(`searchOption=0`)만 제공하며 경유지·대로 우선·최단·계단 회피 옵션은 공개 요청에 받지 않는다.

성공 응답은 `mode=walk`, `provider=tmap`, 총거리(m), 소요시간(초), 출발→도착 순서의 전체 경로와
구간별 안내 문구·거리·시간·경로를 반환한다. 공급자 LineString 좌표를 WGS84 이름 있는 객체로 변환하며
직선 경로나 입력 좌표로 대체하지 않는다. 총거리·시간이 없거나 경로가 2점 미만이거나 좌표가 잘못된
성공 응답은 `ROUTE_PROVIDER_UNAVAILABLE`로 처리한다.

## 오류 계약

- 잘못된 좌표·동일 좌표·공급자 HTTP 400: `INVALID_ROUTE_REQUEST`
- 자전거 요청: `UNSUPPORTED_ROUTE_MODE`
- 공급자 HTTP 404 또는 빈 경로: `ROUTE_NOT_FOUND`
- 연결·읽기·전체 응답 timeout: `ROUTE_PROVIDER_TIMEOUT`
- 비활성·키 누락·공급자 인증/쿼터/장애·비정상 응답: `ROUTE_PROVIDER_UNAVAILABLE`

공급자 원문 오류나 키를 응답·로그로 전달하지 않으며 자동 재시도와 redirect를 비활성화한다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `TMAP_PEDESTRIAN_ENABLED` | `false` | 운영 확인 후 보행자 공급자 활성화 |
| `TMAP_PEDESTRIAN_APP_KEY` | 없음 | 서버 전용 TMAP appKey |
| `TMAP_PEDESTRIAN_BASE_URL` | `https://apis.openapi.sk.com` | 인증정보 없는 HTTPS 공급자 주소 |
| `TMAP_PEDESTRIAN_CONNECT_TIMEOUT` | `2s` | 연결 제한 시간 |
| `TMAP_PEDESTRIAN_READ_TIMEOUT` | `8s` | 개별 읽기 제한 시간 |
| `TMAP_PEDESTRIAN_REQUEST_TIMEOUT` | `10s` | 연결 풀 대기부터 전체 본문 수신까지 제한 시간 |

대중교통과 보행자는 같은 TMAP 앱의 키를 사용할 수 있지만 운영 환경변수는 기능별 활성화와 롤백을 위해
분리한다. 경로 응답은 DB·Redis·애플리케이션 캐시에 저장하지 않는다.

공급자 계약은 [TMAP 보행자 경로안내 공식 문서](https://tmap-skopenapi.readme.io/reference/%EB%B3%B4%ED%96%89%EC%9E%90-%EA%B2%BD%EB%A1%9C%EC%95%88%EB%82%B4)를 따른다.
