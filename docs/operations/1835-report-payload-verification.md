# 보고서 원문 로딩 경계 검증 (#1835)

2026-11-12에 `develop`의 `92afc490c04849583874ca097ad03fd43b250fe7` 구현을 실제 PostgreSQL에서 확인했다. 운영 코드와 DB 스키마는 추가로 변경하지 않았다.

## 재현 범위와 환경

- 기준 흐름은 `60962db65448bd03b77939b375eb0b452c50f8ca`의 entity 조회 후 dirty checking 수정·entity 삭제·entity PDF 조회이다. 원래 바이너리를 배포하거나 이메일 소유권 모델로 되돌리지 않고, 같은 현재 소유자 ID 조건에 이 payload 처리 흐름을 적용해 비교했다.
- 변경 흐름은 현재 서비스의 metadata projection·조건부 수정/삭제와 PDF 전용 projection이다.
- fixture는 같은 소유자의 보고서 1개, PDF 524,288B와 HTML UTF-8 884,736B이다. 수정은 매 요청 다른 제목을 사용하고 삭제 전 복원은 측정 구간 밖에서 수행했다.
- Darwin arm64, Temurin 21.0.13, Docker PostgreSQL `postgres:16-alpine`을 사용했다. 측정 이미지 digest는 `postgres@sha256:aa6cb33cf4c19c765cefcf48d96176258800870a9a1e930ad126200f9d825139`이다.
- 각 경로를 5회 warmup하고 15회 순차 측정했다. 비교하는 동안 다른 Gradle·DB 측정은 실행하지 않았다.

재현 명령:

```sh
./gradlew postgresIntegrationTest \
  --tests com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportPayloadPostgreSqlIntegrationTest \
  --no-daemon --max-workers=2 --console=plain
```

JDK 21과 Docker daemon이 필요하다. 일반 `test` 및 `postgresSmokeTest`는 이 클래스의 `postgres-integration` 태그를 선택하지 않는다. 원시 결과는 실행 후 `build/reports/1835-report-payload-cost.json`에 생성된다.

## 실제 측정 결과

송수신량과 할당량은 요청당 평균 B이며 소수점 이하는 버렸다. 지연은 ms이다.

| 작업 | 흐름 | DB 수신 B | DB 송신 B | 요청 스레드 할당 B | 중앙값 ms | 표본 p95 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 제목·이메일 수정 | entity | 1,409,270 | 1,409,408 | 9,031,435 | 11.951 | 24.977 |
| 제목·이메일 수정 | metadata | 364 | 361 | 146,059 | 3.109 | 3.777 |
| 삭제 | entity | 1,409,268 | 208 | 6,143,567 | 6.548 | 9.109 |
| 삭제 | metadata | 224 | 216 | 75,344 | 2.235 | 5.358 |
| PDF 다운로드 조회 | entity | 1,409,243 | 149 | 5,087,713 | 7.283 | 9.419 |
| PDF 다운로드 조회 | PDF projection | 524,405 | 128 | 562,670 | 2.981 | 4.013 |

기준 수정은 SELECT와 UPDATE에 모두 `html_content`, `pdf_content`가 포함됐다. 변경 수정은 metadata SELECT → `report_name,email,updated_at,version`만 UPDATE → summary SELECT의 3개 SQL을 사용하며 본문 컬럼은 모두 제외됐다. 소유자 ID와 version 조건도 UPDATE에 유지됐다. SQL 개수는 2개에서 3개가 됐지만 큰 원문의 양방향 재전송은 제거됐다.

기준 삭제는 entity 조회에서 두 원문을 로딩했다. 변경 삭제는 metadata SELECT와 소유자 ID·version 조건 DELETE만 실행했다. PDF 전용 조회는 PDF와 다운로드에 필요한 식별자·제목·발행일·version만 SELECT하며 HTML을 읽지 않았다.

## 회귀 결과

같은 직접 실행에서 기존 회귀 8건과 비교 1건, 총 9건이 통과했다. 실패·오류·건너뜀은 0건이다.

- 큰 원문을 SELECT/UPDATE하지 않는 제목 수정과 정상 삭제.
- 소유권이 다른 사용자 및 소유자 ID가 없는 격리 보고서 접근 거부.
- 수정 후 원문·소유자 보존, version 1회 증가와 수정 시각 유지.
- PDF/HTML 각각의 원문 동등성과 요청한 payload만 조회.
- bulk 수정·삭제 후 이전 관리 엔티티 제거와 pending write의 flush 보존.
- 별도 동시 트랜잭션의 update/update 및 update/delete에서 이전 version 명령의 영향 행 수 0건 확인.

첫 실행에서는 테스트 리소스의 H2 driver가 PostgreSQL URL에 남아 있어 context 초기화에 실패했다. 해당 클래스의 DynamicPropertySource에 `org.postgresql.Driver`를 명시한 뒤 위 9건을 재실행해 성공했다.

## 해석과 적용 한계

- DB 송수신은 테스트 전용 SocketFactory가 **현재 요청 스레드**의 PostgreSQL protocol byte를 계수한 값이다. 격리 컨테이너에서 SSL을 끄고 측정하며 SQL 및 transaction protocol도 포함한다. TCP/IP header·재전송이나 운영 TLS 비용은 포함하지 않는다. 계수 상태는 요청별로 생성하고 `finally`에서 제거하므로 다른 요청·pool 유지 스레드와 합산하지 않는다.
- 메모리는 `ThreadMXBean`의 요청 스레드 누적 할당량이다. ORM·JDBC·projection 처리에 생긴 객체를 포함하며 retained heap, 전체 JVM 최대 heap, PostgreSQL 메모리 또는 HTTP 직렬화 비용을 뜻하지 않는다.
- 표본 15회의 p95는 15번째 순서 통계량이다. 작은 로컬 fixture의 보조 관측이며 운영 처리량이나 운영 p95 보장이 아니다. 지연 수치는 테스트 성공 기준으로 사용하지 않는다.
- 같은 현재 모델에서 payload 접근 전략만 비교했으므로 기준 commit 전체 서버와의 성능 비교라고 해석하지 않는다. 제목 수정의 전송량과 할당량 감소가 확인되어 추가 스키마 분리나 bytecode enhancement는 도입하지 않았다.
- 이번 변경은 테스트·검증 기록만 추가한다. 운영 동작·배포 설정·스키마 변경이나 데이터 이관이 없어 별도의 rollout 전환은 없다. 되돌릴 때도 테스트와 기록 변경만 제거하면 된다.
