# 프록시 연결 및 Swagger 운영 안내

## 적용 전 확인

이 구성은 기본적으로 loopback만 노출하고 문서는 비공개로 유지한다. 별도 프록시 연결은 운영자가 명시적으로 활성화한다.

1. 프록시의 실제 upstream 주소, 출발지 IP, 오류 로그를 확인한다. 502만으로 원인을 확정하지 않는다.
2. 프록시에서 해당 upstream으로 직접 헬스 체크를 요청한다.
3. 메인 서버의 포트 바인딩과 보안 그룹/방화벽을 비교한다. 다른 서버에서 loopback 바인딩으로 접근할 수 없다.
4. 보안 그룹/방화벽에 승인된 프록시 출발지만 허용한다. 전체 인터넷에 8080을 열지 않는다.

## 환경 설정

운영 Compose 디렉터리의 `.env`에 아래 설정을 관리한다. `.pingdom-deploy.env`는 배포 워크플로가 이미지 값을 저장하며 덮어쓰는 파일이므로 여기에 추가하지 않는다.

```dotenv
# 기본값은 127.0.0.1. 연결 경로 확인 후 이 EC2에 실제 할당된 사설 IP를 지정한다.
PINGDOM_APP_BIND_ADDRESS=127.0.0.1
PINGDOM_OPENAPI_PUBLIC_ACCESS_ENABLED=true
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
```

사설 IP 바인딩은 프록시에서 해당 네트워크로 도달할 수 있을 때 사용한다. AWS 공인 IP는 호스트 인터페이스에 직접 할당된 주소가 아니므로 바인딩 값으로 넣지 않는다. 다른 클라우드의 프록시에서는 사설망 연결 또는 제한된 공인 경로 중 실제 운영 방식을 먼저 확정한다. `0.0.0.0`은 프록시 출발지 제한과 TLS 경계 검토가 끝난 경우에만 사용한다.

`TRUSTED_PROXY_IPS_REGEX`에는 메인 서버가 실제 관찰하는 직접 연결 프록시 IP만 지정한다. Docker NAT와 프록시 체인을 확인하며 무조건 모든 IP를 신뢰하지 않는다. TLS 종료 지점에서 올바른 `X-Forwarded-Proto`를 전달해야 한다.

Swagger 공개에는 세 토글이 모두 필요하다. `dev` 프로필은 활성화하지 않는다. 기본 설정과 일반 API 인증은 유지된다. 프록시에서도 Swagger UI/명세와 인증 전 API가 Bearer 검사에 막히지 않아야 한다.

## 적용 및 검증

- 변경 전 저장소 커밋, 실행 이미지, Compose와 `.env`의 원본을 권한 제한된 위치에 백업한다. 비밀값을 콘솔이나 이슈에 출력하지 않는다.
- 현재 배포 이미지 `PINGDOM_APP_IMAGE`를 유지하며 Compose 구성을 검증한다. 전체 `docker compose config` 출력을 공유하면 비밀값이 노출될 수 있으므로 필요한 포트와 공개 토글만 확인한다.
- 승인된 배포 시 앱만 재생성한다. DB/Redis를 재생성하거나 볼륨을 삭제하지 않는다.
- 실제 바인딩 주소의 `/actuator/health/readiness`와 승인된 프록시에서의 헬스 체크를 확인한다. GitHub Actions의 readiness는 `docker compose port app 8080` 결과를 사용한다.
- 실제 도메인에서 Swagger 화면, CSS/JS, `/v3/api-docs`, `/v3/api-docs/swagger-config`를 확인한다.
- 로그인은 프록시 토큰 검사에 막히지 않아야 하며, 보호 API는 Bearer 없이 401이어야 한다. 실제 계정 비밀번호를 HTTP로 보내 검증하지 않는다.

## 롤백

현재 워크플로의 자동 롤백은 이전 이미지만 복원한다. Compose 또는 환경 변경의 복구를 보장하지 않는다.

설정 변경 배포에 실패하면 백업한 Compose와 `.env`를 먼저 복원하고, 기록한 이전 이미지를 `PINGDOM_APP_IMAGE`로 지정하여 앱만 재생성한다. 이전 바인딩 주소로 readiness를 다시 확인한다. 보안 그룹/방화벽 변경도 변경 전 기록을 기준으로 복구한다. 공유 `.env`는 이후 다른 변경이 있었는지 확인한 뒤 복원한다.

## 이슈 #1723 진단 기록

2026-09-22 확인 당시 TLS 서버는 `34.64.51.29:80`으로 전달했고 해당 주소 직접 호출에서 502가 재현됐다. 그 서버의 실제 upstream은 미확인이다. 메인 EC2는 loopback 8080 바인딩, 내부 health 200, Swagger 401이었다. 이 기록을 현재 설정이라고 가정하지 말고 적용 직전에 재확인한다.

## 이슈 #1728 앱 조회 인증 및 리다이렉트 복구

현재 합의는 부가 디바이스 인증을 보류하고 Bearer JWT로 앱을 인증하는 것이다.
`X-Timestamp`, `X-SignatureBase64`, `X-App-Version`, `X-Device-Id` 누락으로
정상 JWT 요청을 차단하지 않는다. JWT 서명·만료·사용자 상태·권한 검사는 유지한다.
디바이스 인증 재도입과 HMAC 계약은 별도 후속 작업이다.

### 현재 확인한 범위

2026-09-27 18:44 KST 공개 주소에 JWT 없이 GET 요청한 결과:

| 요청 | 응답 |
| --- | --- |
| `/users/me` | 400, `missing required header: x-timestamp` |
| `/places?page=1&limit=1` | 301, `http://www.typenull.xyz:8081/places/?page=1&limit=1` |
| `/reservations?page=1&limit=1` | 301, `http://www.typenull.xyz:8081/reservations/?page=1&limit=1` |

응답의 Server 헤더는 `openresty`였다. 헤더만으로 실제 오류 생성 인스턴스를
확정할 수 없으며, 위 결과는 인증된 앱 요청이나 백엔드 직접 호출의 검증이 아니다.
현재 백엔드에는 두 목록 경로가 직접 매핑되어 있다. JWT `typ=JWT` 및 HS512
발급 변경은 커밋 `36109018`에 포함되어 있지만 운영 이미지 반영 여부는 별도로 확인한다.

### 수정 및 회귀 확인 순서

1. 각 프록시의 실행 설정·upstream·로그와 백엔드 실행 이미지 커밋을 확인한다.
   모든 경로·인스턴스에서 부가 디바이스 검사가 비활성화됐는지 확인한다.
2. `/places`, `/reservations`의 slash 추가 리다이렉트를 생성하는 계층을 찾는다.
   가능하면 공개 경로를 그대로 upstream에 전달한다. 리다이렉트가 필요하면
   공개 HTTPS 호스트를 유지하고 쿼리 문자열을 보존하며 내부 포트를 노출하지 않는다.
   백엔드 전달 헤더 변경은 실제 원인이 확인된 경우에만 적용하고 신뢰 프록시 범위를 유지한다.
3. 서버가 발급한 정상 JWT로 `/users/me`, `/users/me/bookmarks`, `/places`,
   `/places/map?west=128&south=35&east=129&north=36&zoom=14`, `/reservations`를 조회한다.
   부가 서명 헤더 없이 API 계약에 맞는 JSON을 반환해야 한다. 빈 목록과 조회 오류를 구분한다.
4. JWT 누락·변조는 `401 INVALID_TOKEN`, 만료는 `401 EXPIRED_TOKEN`인지 확인한다.
   리다이렉트 자동 추적 없이 최초 상태와 Location도 확인한다.
5. 실제 앱에서 프로필·즐겨찾기·지도·예약을 확인하고 반영 시각, 이미지 커밋,
   프록시 설정 버전, 마스킹한 상태/오류 코드와 요청 ID를 기록한다.
   JWT와 개인정보를 로그·이슈에 남기지 않는다.

로컬 MockMvc 테스트는 실제 운영 프록시를 통과하지 않는다. 인증 매트릭스의 지도 조회는
H2에서 실행할 수 없는 PostGIS 저장소만 mock으로 대체하므로 실제 지도 SQL 검증도 포함하지 않는다.
로컬 테스트 통과나
비인증 요청만으로 장애 해결을 선언하지 않고, 운영 배포와 인증된 앱 검증 후 종료한다.
변경 전 프록시 설정을 백업하고 설정 검증 후 반영한다. 실패하면 해당 설정 버전과
필요한 앱 이미지만 복원하며, DB·Redis·볼륨은 변경하지 않는다.
