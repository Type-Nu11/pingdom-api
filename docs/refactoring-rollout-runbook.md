# 리팩터링 적용·복구 Runbook

## 목적

이 문서는 Pingdom 2.0 리팩터링을 배포할 때 변경 유형을 분류하고, 적용 전 확인·배포 후
점검·장애 복구를 일관되게 수행하기 위한 절차다. 책임과 성공 지표는 [리팩터링 범위와
성공 지표](architecture/pingdom-2.0-refactoring.md)를 기준으로 한다.

이 Runbook은 기존 [DB migration 운영 Runbook](database-migration.md), [DB 백업/복구
절차](database-backup-restore.md), [운영 관측성](observability.md)을 대체하지 않는다.
리팩터링 변경에 필요한 진입 순서와 확인 신호를 연결한다.

## 1. 배포 전 분류

변경 요청마다 아래 유형을 모두 표시한다. 해당하지 않는 항목은 `해당 없음`으로 남긴다.

| 변경 유형 | 대표 사례 | 필수 확인 |
| --- | --- | --- |
| 모듈·도메인 | 책임 이동, 상태 전이 변경, Query 분리 | 소유 모듈, 전이 전후 상태, 트랜잭션 경계, 영향받는 호출자 |
| API | Controller, DTO, 인증·인가, 오류 응답 변경 | OpenAPI baseline, v1 경로 하위 호환, 관련 controller 테스트 |
| DB | Entity 매핑, 인덱스, 제약 조건, migration 추가 | 새 Flyway version, 사전 데이터 조건, 백업·복구, migration 통합 테스트 |
| 비동기·외부 연동 | Outbox handler, 메일·FCM·S3 처리 변경 | 재시도·중복 처리 안전성, 실패 기록, metric·alert, 수동 재처리 방법 |
| 운영 설정 | Compose, profile, 환경 변수, scheduler 설정 | readiness, 설정 값, 롤백 가능한 이전 값, 배포 문서 |

## 2. 적용 전 점검

1. 변경 요청에 모듈 소유자, 상태 전이, API·DB·운영 영향, 검증 방법을 기록한다.
2. API 변경이면 `./gradlew verifyOpenApiContract`를 실행한다. 호환되지 않는 변경은
   클라이언트 전환과 기준 스펙 갱신이 승인된 경우에만 반영한다.
3. migration 변경이면 먼저 [DB 백업/복구 절차](database-backup-restore.md)에 따라 백업을
   만들고 읽기 가능성을 확인한다. 이어서 [DB migration 운영 Runbook](database-migration.md)의
   사전 조건을 확인하고 Flyway migration 통합 테스트를 실행한다.
4. Outbox 또는 외부 연동 변경이면 event type, deduplication key, handler, 재시도·최종 실패
   처리, 관측 metric을 대조한다. 외부 호출 실패가 핵심 상태 변경을 되돌리지 않는지 확인한다.
5. 문서만 변경하는 경우에는 소스 대조, 상대 링크, `git diff --check`만 수행한다. API export,
   Testcontainers, 전체 Gradle 테스트는 실행하지 않는다.

## 3. 배포 직후 점검

### 공통

1. `docker compose ps`에서 app, postgres, redis의 상태를 확인한다.
2. `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`로 readiness와
   DB 연결을 확인한다.
3. 문제 요청의 `X-Request-Id`로 애플리케이션 로그를 추적한다.

### API 변경

- 새 경로와 기존 v1 경로의 인증·오류 응답을 확인한다.
- OpenAPI 계약 검증 결과와 실제 배포 대상의 API group이 일치하는지 확인한다.
- 기존 공개 경로가 의도 없이 404 또는 권한 오류로 바뀌지 않았는지 확인한다.

### migration 변경

- 애플리케이션 로그에서 Flyway 적용 실패가 없는지 확인한다.
- `flyway_schema_history`의 version, description, success 값을 확인한다.
- migration이 데이터 사전 조건을 요구하면 배포 후 데이터와 인덱스 상태를 추가로 확인한다.

### Outbox·외부 연동 변경

- `pingdom.outbox.events{status="FAILED"}`가 `0`보다 크면 원인을 확인한다.
- `pingdom.outbox.processed{result="retry"}`가 연속된 poll 주기 동안 증가하면 외부 연동과
  handler 로그를 확인한다.
- `pingdom.outbox.max_attempts_exceeded`와 `pingdom.outbox.stale_recovered` 증가는 즉시
  조사한다.
- 이메일·FCM 변경은 notification delivery 기록의 채널, 상태, 오류 코드, 재시도 가능 여부를
  함께 확인한다.

세부 metric과 alert 기준은 [운영 관측성](observability.md)을 따른다.

## 4. 장애 대응과 복구

### API 또는 모듈 리팩터링 실패

1. 장애 요청의 `X-Request-Id`와 변경 전후 오류 응답을 보존한다.
2. DB schema 변경이 없다면 이전 애플리케이션 이미지 또는 검증된 이전 커밋으로 되돌린다.
3. 공개 API를 되돌릴 수 없다면 새 경로를 제거하지 말고 호환 adapter 또는 기존 경로를
   우선 복구한다.
4. 원인과 임시 대응, 영구 수정 범위를 별도 이슈에 기록한다.

### migration 실패

1. 애플리케이션을 중지하고 `flyway_schema_history`와 실제 schema 변경 여부를 확인한다.
2. 이미 적용된 migration 파일을 수정하거나 `flyway repair`로 오류를 숨기지 않는다.
3. 부분 적용 또는 데이터 손상이 의심되면 배포 전 백업으로 복구한다.
4. 정확한 절차는 [DB migration 운영 Runbook](database-migration.md)과 [DB 백업/복구
   절차](database-backup-restore.md)를 따른다.

### Outbox 또는 외부 연동 실패

1. event ID, event type, aggregate, attempt count, 마지막 오류를 보존한다.
2. 공급자 설정·자격 증명·네트워크·payload를 확인한다.
3. 원인이 해결된 뒤 최종 실패 이벤트만 재시도 대상으로 전환한다. 동일 이벤트의 중복 처리
   안전성을 먼저 확인한다.
4. 재시도가 위험하거나 데이터 정합성에 영향이 있으면 handler를 중지하고 수동 보정 범위를
   결정한다.

## 5. 완료 기록

배포가 안정화된 뒤 변경 요청에 다음을 남긴다.

- 변경 유형과 실제 영향 범위
- 실행한 검증과 결과
- health, OpenAPI, Flyway, Outbox 중 해당 신호의 확인 결과
- 발견된 경고·실패와 담당자·후속 이슈
- 복구가 필요했다면 사용한 백업·되돌린 버전·재배포 조건
