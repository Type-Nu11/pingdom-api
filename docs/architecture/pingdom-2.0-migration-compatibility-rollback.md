# Pingdom 2.0 데이터 마이그레이션, 호환 API, 롤백 정책

## 1. 목적과 문서 상태

이 문서는 Pingdom Backend에서 데이터 마이그레이션과 공개 API 변경을 함께 배포할 때의
호환성 기준과 롤백 판단 기준을 정의한다. Flyway migration, OpenAPI 기준 스펙, v1 경로,
백업·복구 절차가 각각 다른 계약을 관리하므로, 하나의 변경 요청에서 적용 순서와 책임을
일관되게 판단하는 것이 목적이다.

이 문서는 2026-07-10 기준 `develop` 구현을 기준선으로 한다. 현재 Flyway migration은
`V1`부터 `V29`까지이며, 공개 HTTP 호환성은
`src/test/resources/openapi-baseline`과 `verifyOpenApiContract`로 검증한다. 이후 기준선이
변경되면 실제 migration·OpenAPI 계약을 먼저 갱신하고 이 문서의 예시를 대조한다.

관련 문서:

- [Pingdom 2.0 리팩터링 범위와 성공 지표](pingdom-2.0-refactoring.md)
- [v1 API 경로 정책](../api-path-policy.md)
- [DB migration 운영 Runbook](../database-migration.md)
- [DB 백업/복구 절차](../database-backup-restore.md)
- [리팩터링 적용·복구 Runbook](../refactoring-rollout-runbook.md)

### 1.1 포함 범위

- Flyway migration, 데이터 backfill, 애플리케이션 배포의 순서와 책임
- 기존 v1 API와 OpenAPI 계약을 보존하는 변경·deprecation 기준
- 호환되는 schema 변경과 파괴적 변경의 구분
- 배포 실패 시 이전 애플리케이션 배포, 호환 adapter 유지, DB 백업 복구의 판단 기준
- 변경 요청에 기록해야 할 사전 조건, 검증, 운영 확인, 후속 정리 항목

### 1.2 제외 범위

- 기존 migration의 즉시 수정, Flyway down migration 도입, DB schema의 자동 rollback
- 특정 기능의 API·Entity·migration 구현 변경
- 자동 백업, 데이터 보정 도구, 새 API versioning 체계의 즉시 도입
- 운영 DB의 직접 수정 또는 배포 중인 데이터에 대한 임의 정리

이 문서에서 발견한 구현 불일치나 자동화 요구는 별도 이슈로 분리한다. 문서만으로 공개
계약이나 운영 DB의 상태를 바꾸지 않는다.

## 2. 기준 정보와 책임

| 대상 | 현재 기준 정보 | 변경 책임 | 완료 판단 |
| --- | --- | --- | --- |
| DB schema | `src/main/resources/db/migration`, `flyway_schema_history` | 변경을 소유한 모듈과 배포 담당자 | 새 migration, 사전 데이터 조건, 적용 결과가 모두 확인된다. |
| 기존 DB 최초 전환 | Flyway baseline version `1`, `FLYWAY_BASELINE_ON_MIGRATE` | 배포 담당자와 DBA 또는 인프라 담당자 | 기존 schema를 잘못 재실행하지 않고 `V2` 이후 migration이 적용된다. |
| HTTP API | Controller·DTO와 `src/test/resources/openapi-baseline` | API를 소유한 모듈 | 기존 v1 경로·응답과 신규 계약의 호환성이 확인된다. |
| 배포·복구 | Compose 상태, application log, health, 백업 파일 | 배포 담당자 | 배포 후 확인 신호와 되돌릴 수 있는 경로가 변경 요청에 기록된다. |

`flyway_schema_history`는 적용된 schema 이력의 기준이고, OpenAPI baseline은 공개 HTTP
계약의 기준이다. 둘 중 하나만 통과해도 다른 계약의 안전성을 대신 보장하지 않는다.
`verifyOpenApiContract`의 성공은 형식 호환성 증거일 뿐, 권한·오류 응답·업무 의미의 변경은
별도로 검토해야 한다.

## 3. 변경 설계 기준

### 3.1 단계적 데이터 변경

DB와 API가 함께 바뀌는 변경은 가능하면 다음 단계를 분리한다. 단순 인덱스 추가처럼
데이터와 호출 계약에 영향이 없는 변경은 필요한 단계만 수행한다.

| 단계 | 목표 | 완료 조건 | 이전 버전과의 관계 |
| --- | --- | --- | --- |
| `EXPAND` | 새 column·table·index·읽기 경로를 추가한다. | 새 schema가 적용되고 사전 조건을 만족한다. | 이전 앱이 새 schema와 함께 동작해야 한다. |
| `BACKFILL` | 기존 데이터를 새 표현으로 채운다. | 대상·실패·재실행 조건과 결과 검증 쿼리가 기록된다. | 기존 읽기·쓰기를 제거하지 않는다. |
| `SWITCH` | 새 앱과 신규 API가 새 데이터를 사용한다. | OpenAPI, 애플리케이션 테스트, 운영 확인이 끝난다. | 이전 API 또는 호환 adapter를 유지한다. |
| `CONTRACT` | 더 이상 사용하지 않는 schema·API를 제거한다. | 클라이언트 전환, 백업·복구 검토, 별도 배포 승인이 끝난다. | 파괴적 변경은 같은 배포에 섞지 않는다. |

`BACKFILL`은 대상 범위, 실행 중 쓰기와의 관계, 재실행 안전성, 완료를 판별할 쿼리를
변경 요청에 남긴다. 대량 데이터 변환은 애플리케이션 기동 시간에 무조건 수행하지 않으며,
작업 방식과 중단·재개 책임을 먼저 정한다.

### 3.2 Flyway migration 기준

1. 운영에 적용된 migration 파일은 수정하지 않는다. 수정이 필요하면 새 version migration을
   추가한다.
2. `EXPAND` 단계에서는 nullable column, 별도 table, 호환되는 index처럼 이전 애플리케이션이
   실행 가능한 변경을 우선 검토한다. 새 `NOT NULL`, unique constraint, 데이터 삭제는 사전
   데이터 조건과 backfill 없이 적용하지 않는다.
3. `CONTRACT` 단계의 column 삭제, type 축소, 제약 강화, 대량 데이터 삭제는 별도 배포로
   분리하고, 적용 전 백업의 읽기 가능성과 복구 판단자를 명시한다.
4. migration 실패 시 `flyway repair`로 실패를 숨기지 않는다. `flyway_schema_history`와 실제
   schema를 확인해 변경이 적용되지 않았을 때만 정리 가능 여부를 검토한다.
5. Flyway는 현재 down migration이나 자동 schema rollback을 제공하지 않는다. 적용 후 되돌림은
   이전 앱으로의 복귀가 가능한지 먼저 판단하고, DB 복구가 필요한 경우에만 백업 복구 절차를
   따른다.

기존 DB의 최초 전환, extension 사전 조건, validate 실패 대응, 통합 테스트 실행 방법은
[DB migration 운영 Runbook](../database-migration.md)이 기준이다.

### 3.3 공개 API 호환성 기준

| 변경 유형 | 기본 판단 | 필요한 조치 |
| --- | --- | --- |
| 신규 endpoint 또는 선택 요청 필드 추가 | 기존 클라이언트가 기존 요청·경로를 계속 사용할 수 있으면 호환 가능 | OpenAPI diff와 기존 endpoint 동작을 확인한다. |
| 응답의 선택 정보 추가 | 형식상 호환될 수 있으나 클라이언트의 엄격한 역직렬화 여부를 확인해야 한다. | 앱·관리자 클라이언트 영향과 API 문서를 함께 검토한다. |
| 경로·필드·오류 코드 rename 또는 제거 | 비호환 변경 | 신규 경로 또는 호환 adapter를 추가하고 기존 계약을 deprecate한다. |
| 요청 필수값 추가, 응답 필드·enum 값 제거, 인증·상태 코드 의미 변경 | 비호환 변경 | 클라이언트 전환 계획과 기준 스펙 갱신을 승인받은 별도 변경으로 처리한다. |

기존 v1 경로는 내부 패키지 구조를 개선하는 이유만으로 제거하지 않는다. 신규 계약이
필요하면 **신규 경로 추가 → 문서·OpenAPI deprecation 표시 → 클라이언트 전환 확인 → 기존
경로 제거** 순서를 따른다. 세부 경로 규칙은 [v1 API 경로 정책](../api-path-policy.md)을
따른다.

## 4. 배포와 롤백 판단

배포 전에는 변경 요청에 현재 단계, 영향을 받는 API·migration, 백업 파일, 검증 결과,
되돌릴 대상과 결정 책임자를 기록한다. 일반적인 배포 순서는 다음과 같다.

1. 데이터 사전 조건과 새 migration의 영향을 확인한다.
2. DB 변경이 있으면 백업을 만들고 목록 조회 또는 복구 리허설로 읽기 가능성을 확인한다.
3. API 변경이 있으면 OpenAPI 계약 검증과 기존 v1 경로의 인증·오류 응답 확인을 마친다.
4. `EXPAND` migration을 적용한 뒤, 이전 앱이 새 schema에서 실행 가능한지 확인한다.
5. 새 애플리케이션을 배포하고 health, Flyway 이력, API 요청을 확인한다.
6. `BACKFILL`과 `CONTRACT`는 성공 조건이 확인된 별도 단계로 진행한다.

| 상황 | 우선 조치 | 롤백 또는 복구 기준 |
| --- | --- | --- |
| schema 변경이 없는 앱·API 배포 실패 | 이전 검증 이미지 또는 호환 adapter로 복귀한다. | DB 복구는 하지 않는다. |
| 호환되는 `EXPAND` migration 뒤 새 앱 배포 실패 | 이전 앱이 새 schema와 동작하는지 확인한 뒤 이전 이미지로 복귀한다. | migration 파일이나 이력을 수정하지 않는다. |
| `BACKFILL` 실패 또는 결과 불일치 | 추가 쓰기 영향과 처리된 범위를 보존하고 작업을 중지한다. | 재실행이 안전하면 원인을 수정해 재개하고, 정합성 보장이 어렵다면 백업 복구를 결정한다. |
| migration 중 부분 DDL·데이터 변경 의심 | 애플리케이션을 중지하고 이력·실제 schema·백업 시점을 확인한다. | 새 migration으로 안전하게 보정할 수 없으면 백업 복구를 우선 검토한다. |
| 비호환 API가 클라이언트 장애를 유발 | 기존 경로·응답 또는 호환 adapter를 우선 유지한다. | 신규 계약을 즉시 삭제하지 말고 클라이언트 전환 상태를 확인한다. |

DB 복구에는 데이터 손실 가능성이 있다. 따라서 정상 백업, 장애 발생 시점, 복구 후 재배포할
애플리케이션 버전을 확인한 담당자만 복구를 결정한다. 실제 명령은
[DB 백업/복구 절차](../database-backup-restore.md)를 따른다.

## 5. 변경 요청 확인표

| 확인 항목 | 완료 조건 |
| --- | --- |
| 소유와 단계 | 모듈 소유자, `EXPAND`·`BACKFILL`·`SWITCH`·`CONTRACT` 중 해당 단계가 기록된다. |
| DB | 새 migration, 사전 데이터 조건, 적용 후 검증 쿼리, Flyway 통합 테스트 필요 여부가 명확하다. |
| API | 기존 경로·요청·응답·오류 계약 영향과 OpenAPI baseline 갱신 필요 여부가 명확하다. |
| 호환성 | 이전 앱이 새 schema와 동작하는지, 기존 클라이언트가 기존 API를 계속 호출할 수 있는지 설명된다. |
| 복구 | 백업 위치·읽기 확인, 이전 이미지, DB 복구가 필요한 조건과 결정 책임자가 기록된다. |
| 운영 확인 | health, `flyway_schema_history`, 관련 API, 데이터 결과와 경고 신호의 확인 방법이 있다. |

## 6. 문서 연결과 변경 이력

이 문서는 판단 기준을 제공하며, 실행 절차를 중복하지 않는다.

- migration 작성·실패 대응·통합 테스트: [DB migration 운영 Runbook](../database-migration.md)
- 백업 생성·복구 명령: [DB 백업/복구 절차](../database-backup-restore.md)
- 경로 명명·v1 deprecation: [v1 API 경로 정책](../api-path-policy.md)
- 배포 전후 점검·장애 대응 기록: [리팩터링 적용·복구 Runbook](../refactoring-rollout-runbook.md)

| 일자 | 범위 | 내용 | 상태 |
| --- | --- | --- | --- |
| 2026-07-10 | #554, #555 | 데이터 마이그레이션, 호환 API, 롤백의 책임·단계·예외 기준을 문서화 | 완료 |
| 2026-07-10 | #556 | Flyway·OpenAPI·v1 경로·검증 workflow의 현재 기준선을 대조 | 완료 |
| 2026-07-10 | #557 | 배포 전 확인과 rollback 판단을 기존 Runbook에 연결 | 완료 |
