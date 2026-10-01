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

## 이슈 #1751 공개 OpenAPI HTTPS URL 및 root 경로

### 확인한 현상과 원인 확인 경계

2026-10-01 11:29 KST 공개 HTTPS 응답에서 다음을 확인했다. 리다이렉트는 따라가지 않았다.

- `/v3/api-docs`: 301, `Location: http://www.typenull.xyz:8081/v3/api-docs/`.
- `/v3/api-docs/`: 404.
- `admin`, `merchant`, `app`, `common`, `consulting` 그룹: 모두 200, 생성된 서버 URL은 `http://www.typenull.xyz`.
- Swagger UI와 swagger-config: 200.

`pingdom-infra`의 기존 소스는 `/v3/api-docs/` prefix location에만 `proxy_pass`를 선언했다.
Nginx는 이 구성에서 slash 없는 `/v3/api-docs`를 slash가 붙은 경로로 자동 301 처리한다.
별도 exact location으로 원래 경로를 전달하면 이를 방지할 수 있다.
이 동작은 로컬 OpenResty로 검증할 수 있지만, 운영의 실행 설정·이미지와 TLS 프록시 체인은
현재 세션에서 직접 조회하지 못했다. 운영 301 생성 인스턴스와 HTTPS 정보 유실 지점은 적용 전에 확정한다.

### 적용 대상과 신뢰 경계

- 프록시 저장소의 문서 경로에만 `openapi_proxy_header.conf`를 적용한다. 일반 API의 Lua 인증 경로는 유지한다.
- `/v3/api-docs`를 exact location으로 전달하고 `/swagger-ui`의 안내 리다이렉트는 상대 경로로 반환한다.
- TLS/중간 프록시의 직접 연결 IP는 `configs/conf.d/shared/openapi_trusted_proxies.conf`에서 관리한다.
  기본값은 loopback뿐이다. 실제 운영 연결 IP를 확인하지 않고 임의의 사설망 전체나 `0.0.0.0/0`을 추가하지 않는다.
- OpenResty 앞의 프록시는 외부 Host를 유지하고, 외부 클라이언트가 넣은 forwarded 헤더를 제거하거나
  신뢰 가능한 값으로 덮어쓴다. TLS 종료 지점은 `X-Forwarded-Proto: https`를 설정하며 이후 hop은 이를 보존한다.
- 문서 요청의 host는 수신한 Host에서 결정한다. 신뢰하지 않는 peer의 scheme/client IP 헤더는 무시한다.
  공개 기본 포트는 HTTPS 443, HTTP 80이며 내부 listen 포트는 전달하지 않는다.
  공개 API가 비표준 포트를 사용하도록 변경되면 프록시 port 정책과 테스트를 별도로 수정해야 한다.
- Spring의 `TRUSTED_PROXY_IPS_REGEX`는 Spring이 직접 관찰하는 OpenResty IP를 지정한다.
  OpenResty가 신뢰하는 TLS 프록시 IP와 혼동하지 않는다. Docker NAT가 있으면 각 hop에서 보이는 주소를 확인한다.
- Spring Boot 3.3.5의 native 처리에서 사용하는 `X-Forwarded-Host`, `X-Forwarded-Port`를 YAML에도 명시했다.
  운영 URL을 OpenAPI 빈에 고정하지 않아 로컬 HTTP·명세 export 동작을 유지한다.
- 문서 공개 토글 3개와 일반 API·관리자 인증 정책은 그대로 유지한다. root 공개 여부를 변경하는 작업은 포함하지 않는다.

### 검증 범위와 운영 완료 조건

로컬 검증은 다음 범위다. 전체 테스트, 운영 배포, 실제 TLS 체인 및 브라우저 Try It Out 검증을 포함하지 않는다.

```bash
# 단위 테스트: 설정 계약과 실제 Tomcat valve의 신뢰/비신뢰 peer 해석
./gradlew test --tests '*ForwardedHeadersConfigurationTest' --tests '*ForwardedHeadersValveTest'

# 승인된 Spring 통합 테스트: 실제 Tomcat과 OpenResty 연결, 문서/보호 API 접근 정책
# PINGDOM_INFRA_TEST_ROOT는 함께 변경한 pingdom-infra checkout의 절대 경로다.
PINGDOM_INFRA_TEST_ROOT=/absolute/path/to/pingdom-infra ./gradlew integrationTest --tests '*ForwardedHeadersIntegrationTest' --tests '*SwaggerPublicAccessSecurityTest' --tests '*SwaggerProductionSecurityTest'

# pingdom-infra 저장소: 임시 OpenResty의 문서 경로·헤더 처리 검증
python3 tests/openapi_proxy_test.py
```

독립 OpenResty 테스트의 backend는 HTTP fixture다. Spring 통합 테스트에
`PINGDOM_INFRA_TEST_ROOT`를 지정하면 실제 실행 중인 Spring 서버를 upstream으로 연결한다.
환경변수를 생략하면 두 OpenResty 연동 테스트만 skip된다. Docker와 Python 3가 필요하다.
원본 `nginx.conf`와 include 구조를 사용하며 임시 복사본의 upstream과 Lua guard만 fixture로 치환한다.
일반 경로의 Lua 인증은 401 fixture이므로 실제 Rust/Zig JWT 검증과 운영 upstream 검증으로 보고하지 않는다.
Spring 통합 테스트에서는 리다이렉트를 추적하지 않고 root와 다섯 그룹의 HTTPS base URL,
host/port 해석, 로컬 HTTP URL 유지, 공개 토글 및 보호 API 인증 거부를 확인한다.
같은 그룹의 기본 Springdoc 캐시에 HTTP/HTTPS 요청 24개를 동시에 보내 요청별 URL을 검증한다.
OpenResty 연동은 직접 연결한 비신뢰 peer와 loopback 신뢰 peer에서 문서 8개 경로를 조회해
공개 상태의 200/HTTPS URL과 비공개 상태의 401을 확인한다.
HTTP 연결/응답, subprocess 실행과 컨테이너 정리에는 시간 제한을 둔다.

2026-10-01 최초 검증에서 Java 21 컴파일, 단위 테스트 3개와 Spring 통합 테스트 7개가 통과했다.
리뷰 보완 후 forwarded/production 두 클래스의 통합 테스트 8개를 실행했다.
검증기의 401 상태 파싱 오류를 수정하고 실패한 연동 테스트 1개만 재실행해 통과했다.
OpenResty 1.31.1.1에서 원본 설정 구문 검사와 독립 경로·헤더 검증 14개도 통과했다.
운영 완료 조건은 아직 미검증이다.

운영 완료 조건:

1. 변경 전 TLS/OpenResty 실행 설정, peer IP, backend 이미지와 문서 공개 토글을 기록하고 원본을 백업한다.
2. 운영 upstream을 유지해 프록시 설정 구문을 검증한다. 실제 연결 IP를 두 계층의 신뢰 설정에 적용한다.
3. 프록시 반영 후 root 문서가 의도된 응답을 반환하고 내부 HTTP 주소나 8081로 유도하지 않는지 확인한다.
4. 다섯 그룹의 모든 `servers[].url`이 `https://www.typenull.xyz`인지 확인한다.
5. 실제 HTTPS Swagger 화면에서 승인된 읽기 API를 실행한다. 기존 인증을 사용하고 Network/Console에서
   HTTPS 요청, HTTP downgrade·mixed-content 부재를 확인한다. 토큰과 개인정보는 기록하지 않는다.
6. 문서 공개 토글이 꺼진 환경의 접근 거부와 일반 보호 API의 인증 거부가 유지되는지 확인한다.

실패 시 프록시의 변경된 location, HTTP map, 문서 헤더·allowlist와 앞단 TLS 설정을 백업본으로 복원한다.
필요한 경우 이전 backend 이미지와 `TRUSTED_PROXY_IPS_REGEX`도 복원한다. 이미지만 되돌리는 자동 롤백으로
설정 파일과 환경변수까지 복구됐다고 가정하지 않는다.

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
