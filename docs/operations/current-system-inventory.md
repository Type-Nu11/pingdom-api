# 현행 API·테이블·배치 의존성 인벤토리

이 문서는 #547의 소스 기준 구성 점검 결과다. 운영 DB에 접속해 현재 행 수를 조사하는 문서가 아니라,
현재 체크아웃한 코드가 필요로 하는 API·스키마·배치·외부 의존성을 재현 가능하게 확인하는 기준선이다.

## 수집 절차

```bash
./scripts/collect-current-system-inventory.sh
./scripts/verify-current-system-inventory.sh
```

첫 명령은 `build/inventory/current-system-inventory.md`를 생성한다. 생성 파일에는 Controller mapping
위치, Flyway 파일, `CREATE TABLE` 선언, `@Scheduled` 실행 지점이 모두 들어간다. 두 번째 명령은
핵심 구간과 현재 Flyway 기준선을 확인한다. `build/` 아래의 결과는 Git에 커밋하지 않는다.

Controller mapping 수는 `@GetMapping` 같은 선언의 개수이며, 실제 공개 endpoint 수와 같지 않을 수 있다.
한 mapping이 여러 경로를 선언하거나 class-level mapping과 결합되기 때문이다. 외부 계약의 정본은 항상
Springdoc export 결과와 OpenAPI baseline 검증이다.

CI artifact나 배포 증적을 별도 위치에 둘 때는 출력 경로를 첫 번째 인자로 전달한다.

```bash
./scripts/collect-current-system-inventory.sh /tmp/pingdom-inventory.md
```

실행 중인 환경을 확인해야 할 때는 이 소스 인벤토리와 아래 운영 확인 절차를 함께 사용한다.

## API 기준선

- API 경로의 정본은 Controller mapping과 Springdoc이 생성하는 OpenAPI 문서다.
- `dev`, `openapi-export` 프로필에서 `app`, `common`, `web` OpenAPI 그룹을 제공한다.
- 기준 스펙은 `src/test/resources/openapi-baseline`에 있고, 아래 명령으로 호환성을 확인한다.

```bash
./gradlew exportOpenApiSpecs
./gradlew verifyOpenApiContract
```

API 변경이 없는 구성 점검에서는 baseline을 갱신하지 않는다. 새 경로·요청·응답 변경은 생성 스펙과
baseline diff를 검토한 뒤 별도 API 이슈에서 반영한다.

| 변경 유형 | OpenAPI baseline | Flyway migration | 이 인벤토리 문서 |
| --- | --- | --- | --- |
| 설정·운영 절차만 변경 | 검토만 수행 | 검토만 수행 | 절차와 영향 갱신 |
| 공개 API 계약 변경 | 생성·호환성 검증 후 갱신 | 데이터 영향 검토 | API 기준선 갱신 |
| 영속 모델 변경 | API 영향 검토 | 새 version 추가 및 backfill 검토 | 테이블·배치 영향 갱신 |
| batch 추가·변경 | API 영향 검토 | 상태 저장 시 migration 검토 | 주기·중복 실행·복구 절차 갱신 |

실행 중인 서비스의 API 문서는 `dev`, `openapi-export` 프로필에서만 제공된다. 운영 health 확인에는
OpenAPI endpoint 대신 아래 probe를 사용한다.

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

인증이 필요한 API의 수동 점검은 관리자 또는 테스트 계정의 토큰을 사용하고, 실제 사용자 토큰과
Authorization header 원문은 인벤토리 증적에 기록하지 않는다.

## 데이터베이스 기준선

| 항목 | 현행 기준 | 확인 방법 |
| --- | --- | --- |
| DB | PostgreSQL + PostGIS | `DB_URL`, `docker-compose-local.yml` |
| ORM schema 정책 | `ddl-auto=validate` | 애플리케이션 시작 시 검증 |
| Flyway | 활성화, baseline version `1`, validate 활성화 | `application.yaml` |
| Migration 파일 | `V1`부터 `V87`까지 87개 | 수집 스크립트 |
| 테이블 선언 | distinct `CREATE TABLE` 77개 | 수집 스크립트 |
| 필수 extension | `postgis`, `pg_trgm` | `docs/database-migration.md` |

운영 배포 전에는 아래를 확인한다.

```sql
SELECT version, description, type, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 20;

SELECT extname
FROM pg_extension
WHERE extname IN ('postgis', 'pg_trgm');
```

실제 DB의 테이블 목록은 migration SQL 선언 수와 같다고 가정하지 않는다. extension, Flyway history,
운영 보정 테이블을 구분하기 위해 아래 쿼리 결과를 배포 증적으로 남긴다.

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

Flyway 오류 복구, concurrent index migration, 기존 데이터 backfill 판단은
[DB migration 문서](../database-migration.md)를 따른다. 이미 적용된 migration 파일은 수정하지 않고,
새 변경은 새 version 파일로 추가한다.

## 배치·비동기 실행 의존성

`PingdomApplication`은 scheduling을 활성화한다. 현재 실행 지점은 수집 스크립트에서 매번 확인하며,
운영 영향이 큰 항목은 아래와 같다.

| 작업 | 기본 주기 | 주요 의존성·영향 |
| --- | --- | --- |
| Outbox ready event 처리 | 5초 | DB outbox, executor, 이벤트 전달 |
| Outbox 성공 이벤트 정리 | 10분 | retention 7일, outbox 삭제 |
| Outbox 상태 metric 갱신 | 30초 | DB outbox, actuator metric |
| 임시 제재 만료 | 1시간 | 사용자 접근 상태 변경 |
| 탈퇴 사용자 물리 삭제 | 24시간 | user retention, 연관 데이터 정리 |
| 여행 데이터 보존 정리 | 1시간 | 탈퇴 사용자 여행 데이터 정리 |
| 개인정보 처리 이력 정리 | 24시간 | 보존 기간 경과 이력 삭제 |
| 방문 증빙 보존 정리 | 24시간 | 만료 visit evidence 삭제 |
| 접근 상태 cache 정리 | 1분 | in-memory access cache |
| 장소 좌표 token 정리 | 1분 | in-memory coordinate token |

배치가 멈추면 즉시 재시도하지 말고 health, `X-Request-Id`, Outbox metric과 worker 로그를 먼저
확인한다. Outbox의 실패·재시도 기준은 [관측성 문서](../observability.md)를 따른다.

현재 `@Scheduled` 작업은 애플리케이션 인스턴스마다 등록된다. 다중 인스턴스 배포에서는 Outbox처럼
DB claim 또는 idempotency가 있는 작업과 그렇지 않은 정리 작업을 구분해 점검한다. 새 배치를 추가할 때는
중복 실행 허용 여부, 잠금 또는 idempotency 근거, 실패 시 재실행 방법을 이 문서와 해당 기능 이슈에 기록한다.

## 실행 환경과 외부 의존성

| 의존성 | 용도 | 구성 출처 | 점검 포인트 |
| --- | --- | --- | --- |
| PostgreSQL/PostGIS | 영속 데이터·공간 조회·Flyway | `DB_*`, Compose | extension, migration history, readiness |
| Redis | rate limit, 분산 상태 | `REDIS_*` | 연결·timeout·key prefix |
| AWS S3 | 이미지 저장 | `AWS_*` | bucket, region, credential |
| Postmark | 이메일 인증·비밀번호 재설정 | `POSTMARK_*` | sender, callback base URL |
| Google OAuth | OAuth 로그인 | `GOOGLE_*` | redirect URI |
| FCM | push notification | `FCM_KEY_PATH` | 운영 key mount 여부 |

`local`, `dev` 프로필은 Docker Compose와 개발 seed를 켤 수 있다. 운영 배포에서는
`SEED_ADMIN_ENABLED=false`, `SEED_DEV_DATA_ENABLED=false`를 명시하고 기본 개발 계정을 사용하지 않는다.
로컬 seed의 계정과 중복 실행 규칙은 [로컬 개발 문서](../local-development.md)를 따른다.

## 점검 증적 형식

점검 완료 기록에는 비밀값 대신 다음만 남긴다.

- 배포 대상 Git commit과 실행 프로필
- 수집 스크립트와 `verifyCurrentSystemInventory` 실행 결과
- Flyway 최신 version, 성공 여부, extension 조회 결과
- liveness/readiness 상태와 확인 시각
- Outbox `FAILED` 상태 수와 최근 worker 오류 여부

이 형식으로 남기면 설정값을 노출하지 않고도 다음 배포에서 기준선 차이를 비교할 수 있다.

## 배포 전 환경 변수 점검

값 자체를 로그나 티켓에 남기지 않고, 배포 환경에 키가 존재하는지만 확인한다. 최소 확인 대상은
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `MERCHANT_VERIFICATION_ENCRYPTION_KEY`,
`AWS_S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`다. 이메일·OAuth·FCM을
사용하는 배포는 해당 provider 설정도 함께 확인한다.

프록시 뒤에서 실행한다면 `TRUSTED_PROXY_IPS_REGEX`가 실제 프록시 대역만 포함하는지 확인한다.
허용되지 않은 forwarded header는 신뢰하지 않아야 하며, 기본 loopback 설정을 운영에서 그대로 사용하면
클라이언트 IP 기반 rate-limit과 HTTPS redirect 판단이 틀어질 수 있다.

## 운영 영향과 롤백 기준

1. 이 이슈는 설정값, API, Flyway 파일을 변경하지 않는다. 따라서 배포 시 schema migration이나 API
   baseline 변경은 발생하지 않는다.
2. 수집 스크립트는 읽기 전용이며 DB·Redis·외부 API에 연결하지 않는다. 코드 기준 인벤토리와 live
   상태를 혼동하면 안 된다.
3. inventory 수치가 달라지면 새 Controller, migration, batch가 추가되었는지 diff에서 확인하고 관련
   이슈·운영 문서를 갱신한다.
4. 문서 또는 스크립트만 되돌리면 되며, 애플리케이션 데이터와 배치 상태에는 롤백 영향이 없다.

현행 점검 결과만으로 추가 환경 변수를 도입하거나 기본값을 바꾸지 않는다. 구성 변경은 실제 장애 원인,
보안 요구 또는 배포 환경 차이가 확인된 별도 이슈에서 영향 범위와 롤백 방법을 검토한 뒤 적용한다.
