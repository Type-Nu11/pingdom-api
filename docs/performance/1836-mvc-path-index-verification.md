# 실제 MVC 경로 인덱스 검증 (#1836)

2026-11-12에 `92afc490c04849583874ca097ad03fd43b250fe7`의 경로 인덱스를 실제 Spring MVC 등록 매핑으로 검증했다. `KnownMvcPathIndex`와 `UnknownPathRedirectFilter` 운영 구현은 변경하지 않는다.

## 비교 범위와 재현

- 기준은 `60962db65448bd03b77939b375eb0b452c50f8ca` 필터의 매 요청 `getHandlerMethods()` 호출과 전체 매핑 순회·경로 패턴 판정이다. 현재 테스트 안에서 그 흐름을 재현하며 과거 서버 바이너리를 실행한 비교는 아니다.
- 변경 경로는 현재 `KnownMvcPathIndex.matches`를 직접 호출한다. 경로 전처리, reserved path 검사, JWT·DB·컨트롤러 처리 등 두 구현에 공통인 비용은 측정 구간에서 제외한다.
- 실제 `PingdomApplication`의 test 프로필, H2 및 `RequestMappingHandlerMapping`을 사용했다. 매핑 378개·등록 패턴 378개이며 별도 가짜 controller나 mapping 대역은 추가하지 않았다.
- 환경은 Darwin arm64, Temurin 21.0.13, Spring Boot 3.3.5이다. 이 검증에는 Docker·외부 DB가 필요하지 않다.
- 경로별 두 흐름을 각각 500회 warmup했다. 이후 100요청씩 20개 배치를 두 흐름의 순서를 교대로 바꾸어 실행했다. 동시에 다른 Gradle·DB 성능 측정은 수행하지 않았다.

```sh
./gradlew test \
  --tests com.typenull.pingdom.shared.security.handler.KnownMvcPathIndexTest \
  --tests com.typenull.pingdom.shared.security.handler.UnknownPathRedirectFilterTest \
  integrationTest \
  --tests com.typenull.pingdom.integration.auth.UnknownPathRedirectSecurityTest \
  --tests com.typenull.pingdom.shared.security.handler.KnownMvcPathIndexPerformanceIntegrationTest \
  --no-daemon --max-workers=2 --console=plain
```

JDK 21로 실행한다. 일반 `test`는 `integration` 태그를 제외하므로 실제 Security·MVC 비교는 위의 `integrationTest` 직접 선택이 필요하다. 측정 JSON은 `build/reports/1836-mvc-path-index-cost.json`에 생성된다.

## 실제 경로 존재 판정 비용

검사 수는 기준 전체 순회의 실제 패턴 검사 수다. 인덱스 값은 첫 segment와 공통 패턴으로 제한된 **후보 상한**이며, 실제 short-circuit 검사 횟수와 같다고 가정하지 않는다. CPU·할당량은 요청당 스레드 평균이다. 지연은 100요청 배치 평균의 중앙값·표본 p95로, 개별 HTTP 요청의 tail latency가 아니다.

| 경로 | 판정 | 기준 검사 → 인덱스 후보 상한 | CPU ns 기준 → 인덱스 | 할당 B 기준 → 인덱스 | 중앙값 μs 기준 → 인덱스 | 배치 평균 p95 μs 기준 → 인덱스 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `/users/me` | 등록 | 318 → 43 | 51,264 → 1,092 | 97,826 → 1,808 | 51.328 → 1.103 | 72.751 → 1.296 |
| `/places/42` | 등록 | 133 → 35 | 22,749 → 565 | 56,584 → 1,056 | 22.767 → 0.543 | 24.972 → 0.654 |
| `/admin/places` | 등록 | 302 → 149 | 37,914 → 3,013 | 87,470 → 5,600 | 38.040 → 2.574 | 53.928 → 6.197 |
| `/auth/login` | 등록 | 287 → 12 | 28,211 → 282 | 84,680 → 531 | 27.018 → 0.263 | 46.911 → 0.462 |
| `/unregistered-1836/deep` | 미등록 | 378 → 2 | 30,074 → 439 | 101,424 → 624 | 30.569 → 0.416 | 31.376 → 0.517 |
| `/places/42/unregistered-1836` | 미등록 | 378 → 35 | 31,373 → 1,163 | 101,840 → 2,624 | 30.895 → 1.141 | 32.313 → 1.261 |

여섯 경로의 기준·인덱스 판정이 모두 같았다. `/auth/login`은 POST 매핑이어도 경로가 존재하므로 GET을 로그인 redirect로 바꾸지 않고 기존 MVC method 검증으로 전달한다. 등록·미등록 경로 모두 이 실제 매핑 분포에서 CPU·할당량 감소가 확인되어 기존 인덱스 적용을 유지한다.

서로 다른 임의 URI 10,000개를 입력한 뒤에도 등록 패턴 수 378개와 미등록 첫 segment의 후보 상한 2개는 변하지 않았다. 요청 URI를 캐시하지 않고 시작 시 등록 메타데이터만 보존한다. runtime controller 등록을 도입할 경우 인덱스 갱신은 별도 설계가 필요하다.

## Security·MVC 응답 검증

첫 직접 실행에서 기존 인덱스·필터 단위 28건, 기존 Security 12건과 추가 403 및 실제 MVC 비교 1건은 통과했다. 미등록 GET/HEAD의 상대 `/console/login` redirect, 등록 API의 JSON 401·403, 공개 API의 405, console reserved 경로와 루트 응답이 유지됐다.

추가 media 회귀 두 건에서는 기존 포괄 `GlobalExceptionHandler`가 MVC 예외를 500으로 바꾸는 공백을 재현했다. `POST /auth/login`의 `text/plain` 요청은 415 대신 500, 인증된 보고서 PDF GET의 `Accept: application/json`은 406 대신 500이었다. 이 포괄 처리는 기준 commit에도 존재하며, 경로 인덱스의 잘못된 redirect와 구분한다.

두 예외에 한정한 전용 처리를 추가해 `UNSUPPORTED_MEDIA_TYPE` 415와 `NOT_ACCEPTABLE` 406을 고정 JSON 오류로 반환한다. MVC가 제공한 지원 미디어 헤더는 보존하고 예외 원문은 노출하지 않는다. 실제 잘못된 Content-Type·Accept 요청만 대상이며 범용 예외 정책과 정상 요청 흐름은 유지한다.

최초 선택 실행은 44건 중 42건 통과·위 media 2건 실패였다. 수정 후 이미 통과한 인덱스·필터·Security·비용 비교를 반복하지 않고, 실패한 두 통합 시나리오와 새 예외 매핑 단위 두 건만 선택하여 총 4건이 통과했다. 실패·오류·건너뜀은 0건이며 두 단계에서 서로 다른 46개 케이스를 확인했다.

```sh
./gradlew test \
  --tests com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandlerTest.mapsUnsupportedMediaTypeWithoutLosingSupportedMediaHeader \
  --tests com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandlerTest.mapsNotAcceptableMediaTypeToFixedJsonError \
  integrationTest \
  --tests com.typenull.pingdom.integration.auth.UnknownPathRedirectSecurityTest.registeredPublicApiKeepsUnsupportedMediaType \
  --tests com.typenull.pingdom.integration.auth.UnknownPathRedirectSecurityTest.registeredGetApiKeepsNotAcceptableMediaType \
  --no-daemon --max-workers=2 --console=plain
```

단위 테스트는 415의 지원 미디어 헤더·JSON 응답과 406의 고정 JSON 응답을 확인한다. 실제 Security 체인 테스트는 잘못된 미디어 요청에 해당 상태·공개 오류 코드가 반환되고 로그인 `Location` 헤더가 없음을 확인한다.

## 해석과 적용 한계

- 실제 test 프로필 등록 분포의 로컬 JVM 결과다. 운영 프로필의 조건부 controller, JVM 상태, 매핑 순서가 바뀌면 절대 수치도 달라진다. 운영 HTTP 지연·처리량·SLO를 보장하지 않는다.
- 기준의 실제 매핑 조회·순회를 포함하고, 인덱스의 경로 판정만 직접 호출한다. 공통 request URI parsing·필터 reserved path 검사·최종 MVC 라우팅·인증·직렬화는 포함하지 않는다.
- 시간·CPU·할당량은 관측 결과이며 불안정한 시간 임계값을 테스트 성공 조건으로 사용하지 않는다. `ThreadMXBean` 할당량은 retained heap이나 프로세스 최대 메모리가 아니다.
- 인덱스 snapshot 크기는 등록 메타데이터에 의해 제한된다. 임의 경로에 대한 메모리 상한 검증은 입력 경로를 보관하지 않는 구조와 등록·후보 수 불변의 회귀이며, 장시간 전체 JVM heap 부하 시험을 뜻하지 않는다.
- DB·스키마·저장 흐름 변경은 없다. 배포 시 잘못된 Content-Type·Accept가 기존 500 대신 415·406을 받는다는 점을 클라이언트 오류 분류에서 확인한다. 롤백은 두 media 전용 handler·공개 코드와 관련 테스트를 되돌리는 범위이며, 그러면 기존 500 응답 공백도 돌아온다. 경로 인덱스와 데이터 상태에는 별도 전환이 없다.
