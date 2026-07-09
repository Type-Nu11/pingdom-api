# Pingdom 2.0 서버 리팩터링 범위와 성공 지표

## 1. 목적과 문서 상태

이 문서는 Pingdom 2.0 서버 리팩터링에서 변경할 수 있는 경계와 변경하면 안 되는
계약을 정의한다. 신규 기능이나 구조 변경을 설계할 때 이 문서를 먼저 확인하고, 구현
완료 후에는 이 문서의 성공 지표로 결과를 확인한다.

이 문서는 2026-07-10 기준 `develop` 구현을 기준선으로 한다. [목표 아키텍처
문서](README.md)는 지향 구조를, 이 문서는 현재 구현과 목표 구조의 차이 및 전환 기준을
설명한다. 현재와 목표를 같은 사실로 표현하지 않는다.

### 1.1 포함 범위

- 도메인 용어, 모듈 책임, 허용된 협력 방식
- 신고, 이의제기, 게시글 노출, 사용자 탈퇴, 추천 행동 전환, Outbox의 상태 전이
- API, Flyway migration, OpenAPI baseline, 운영 관측성의 계약 보존 기준
- 리팩터링 배포 전·후 확인과 복구 기준

### 1.2 제외 범위

- 공개 API 경로·요청·응답의 의도되지 않은 변경
- 이미 운영에 적용된 Flyway migration 수정
- 데이터 모델, SLO 수치, 외부 연동 공급자의 임의 교체
- 이 문서에서 발견한 구조적 불일치의 즉시 코드 수정

마지막 항목은 중요하다. 불일치를 발견하면 이 문서의 변경 이력에 근거와 영향을 남기고,
코드 변경은 별도 이슈와 검토로 진행한다.

## 2. 기준 정보와 용어

계약별 기준 정보는 서로 다르다. 현재 동작은 Java 코드와 테스트로, 공개 HTTP 호환성은
OpenAPI 기준 스펙과 호환성 검증으로, 운영 DB schema는 적용된 Flyway migration과
`flyway_schema_history`로 확인한다. 문서는 이 구현 계약을 설명하며, 계약과 다르면
문서만으로 계약을 바꾸지 않는다.

| 용어 | 의미 | 소유 경계 |
| --- | --- | --- |
| 모듈 | 하나의 도메인 책임을 소유하는 최상위 패키지 | `com.typenull.pingdom.<module>` |
| Command | 상태를 바꾸고 규칙을 검증하는 유스케이스 | application service 및 domain |
| Query | 상태를 변경하지 않고 화면·운영 조회 모델을 조합하는 유스케이스 | application query service |
| 도메인 이벤트 | 이미 확정된 사실을 알리는 애플리케이션 내부 이벤트 | 발행 모듈과 소비 모듈 |
| Outbox 이벤트 | DB 트랜잭션 안에서 저장한 뒤 비동기로 외부 부수효과를 처리하는 이벤트 | `shared.outbox` |
| 공개 계약 | 앱·관리자·운영자가 외부에서 의존하는 API, DB migration, 작업 절차 | OpenAPI, Flyway, 운영 문서 |

`Map`은 현재 장소·게시글·신고 API에서 사용 중인 v1 경로 명칭이다. 새 내부 책임을
정할 때는 경로 명칭이 아니라 아래 모듈 책임을 기준으로 판단한다. 기존 경로의 하위
호환 규칙은 [v1 API 경로 정책](../api-path-policy.md)을 따른다.

추천 노출·클릭·행동 전환의 현재 용어와 귀속 조건은
[장소 추천 행동 전환 도메인 기준](place-recommendation-conversion.md)을 따른다. 이 기준은
실제 방문을 수집하지 않는 현재 구현을 행동 전환으로 명확히 구분한다.

Spring 이벤트와 Outbox 작업 요청의 현재 전달 보장, 책임, 장애 대응 기준은
[Pingdom 2.0 목표 아키텍처와 도메인 이벤트](pingdom-2.0-domain-events.md)를 따른다.

데이터 migration과 공개 API를 함께 변경하는 경우에는 단계별 호환성, 백업·복구와
deprecation 판단을 [데이터 마이그레이션, 호환 API, 롤백 정책](pingdom-2.0-migration-compatibility-rollback.md)에
따른다.

## 3. 모듈 책임과 협력 경계

### 3.1 현재 기준선

| 모듈 | 소유 책임 | 대표 협력 대상 |
| --- | --- | --- |
| `identity` | 계정, 인증, OAuth, 토큰, 사용자 상태와 탈퇴 | `notification`, `privacy`, `shared` |
| `place` | 장소, 좌표, 북마크, 장소 추천과 추천 스냅샷 | `post`, `shared` |
| `post` | 지도 이미지 게시글, 이미지 처리, S3 객체 수명 | `place`, `shared` |
| `engagement` | 좋아요, 게시글 신고, 신고 정책 | `post`, `identity`, `notification` |
| `moderation` | 관리자 조회·조치, 제재, 신고·이의제기, 감사 이력 | `identity`, `post`, `engagement`, `place` |
| `notification` | 이메일·FCM, 알림 설정, 발송 이력, Outbox handler | `identity`, `engagement`, `shared.outbox` |
| `privacy` | 개인정보 처리 이력, 사용자 데이터 내보내기, 탈퇴 데이터 정리 | `identity`, `shared` |
| `shared` | 보안, 공통 예외, rate limit, Outbox, 관측성, 설정, 기술 지원 | 모든 모듈 |

`shared`는 공통 기술 경계를 위한 패키지다. 개별 도메인의 정책이나 상태 전이를
`shared`에 추가하지 않는다. Outbox는 외부 부수효과의 전달 보장을 위한 기술 경계이므로
`shared.outbox`에 둔다.

### 3.2 목표 규칙

- Controller는 application 유스케이스만 호출한다.
- 상태 전이와 트랜잭션 경계는 application과 domain이 소유한다.
- infrastructure는 저장·외부 SDK 연동을 담당하며 도메인 정책을 결정하지 않는다.
- 다른 모듈의 상태 변경은 그 모듈의 공개 유스케이스 또는 확정된 이벤트를 우선 사용한다.
- Query는 Command의 내부 구현을 재사용하지 않고, 필요한 조회 모델을 별도로 조합한다.
- 외부 연동 실패는 공급자 예외를 그대로 노출하지 않고 모듈 오류 또는 Outbox 재시도
  상태로 번역한다.

현재 `moderation`의 신고·이의제기 처리에는 `identity`, `post`, `engagement`의 저장소를
하나의 트랜잭션에서 조합하는 흐름이 있다. 이는 현재 동작을 보존해야 하는 전환 대상이다.
해당 흐름을 분리할 때는 상태 전이 표, API 계약, 감사 이력과 롤백 방법을 함께 검토한다.
저장소 직접 참조만 제거하는 변경은 허용하지 않는다.

### 3.3 동기 처리와 비동기 처리 결정

| 상황 | 처리 방식 | 이유 |
| --- | --- | --- |
| 신고 수락·반려, 이의제기 승인·반려, 사용자 제재, 게시글 숨김·복구 | 하나의 동기 트랜잭션 | 사용자에게 보이는 핵심 상태와 감사 이력이 함께 확정돼야 한다. |
| 계정·게시글·장소의 일반 상태 변경 | 해당 모듈 Command 트랜잭션 | 규칙 검증과 상태 변경의 원자성을 보장한다. |
| 이메일 발송, FCM 발송, S3 객체 삭제 | Outbox 저장 후 비동기 처리 | 외부 시스템 지연·실패가 핵심 DB 트랜잭션을 롤백하지 않게 한다. |
| 추천 노출 기록처럼 후속 기록이 실패해도 조회 응답을 막지 않는 처리 | 커밋 후 이벤트 또는 별도 비동기 처리 | 사용자 응답과 후속 기록을 분리한다. |
| 관리자 목록·지표·설명 조회 | read-only Query | 조인·정렬·집계 요구를 상태 변경과 분리한다. |

## 4. 상태 전이 기준

상태 이름을 추가·삭제하거나 전이 조건을 바꾸는 변경은 API·DB·운영 계약 변경으로
취급한다. 아래 표의 전이는 현재 구현을 기준으로 한다.

| 대상 | 시작 상태 | 전이 | 책임과 조건 |
| --- | --- | --- |
| 사용자 | `ACTIVE` | `WITHDRAWN` | `identity`가 탈퇴 시 개인정보를 비식별화하고 토큰·FCM 토큰·제재를 해제한다. `WITHDRAWN`은 현재 복구 전이가 없다. |
| 게시글 신고 | `PENDING` | `ACCEPTED` 또는 `DECLINED` | `moderation`만 대기 신고를 처리한다. 수락은 신고 대상 사용자 제재와 게시글 숨김을 같은 트랜잭션에서 처리한다. |
| 게시글 신고 | `ACCEPTED` | `RESTORED` | 승인된 이의제기에 따라 신고 복구, 게시글 복구 및 제재 해제를 조건부로 처리한다. |
| 게시글 노출 | `ACTIVE` | `AUTO_HIDDEN` | `engagement` 신고 정책 또는 관리자 신고 수락이 게시글을 숨긴다. 이미 숨김 상태면 다시 숨기지 않는다. |
| 게시글 노출 | `AUTO_HIDDEN` | `ACTIVE` | 이의제기 승인 또는 관리자 복구가 수행한다. 이미 노출 상태면 다시 복구하지 않는다. |
| 신고 이의제기 | `SUBMITTED` | `APPROVED` 또는 `REJECTED` | `moderation`만 접수 상태를 처리한다. 대상 사용자는 수락된 신고 또는 숨김 게시글에만 이의제기를 제출할 수 있다. |
| 추천 행동 전환 | 최근 추천 클릭 | `BOOKMARK` 또는 `LIKE` 전환 기록 | `place`가 클릭 후 7일 이내의 행동만 귀속한다. 전환은 유형별·사용자별·장소별로 한 번만 기록하며, 실제 방문 상태는 없다. |
| Outbox | `PENDING` 또는 `RETRY` | `PROCESSING` | worker가 준비된 이벤트를 선점한다. |
| Outbox | `PROCESSING` | `SUCCEEDED` | handler가 외부 부수효과를 성공적으로 처리한다. |
| Outbox | `PROCESSING` | `RETRY` 또는 `FAILED` | 실패 횟수와 backoff 정책으로 결정한다. `FAILED` 이벤트는 운영 조치로 `RETRY`로 되돌릴 수 있다. |

`NotificationDeliveryStatus`는 Outbox 자체 상태가 아니라 발송 결과 기록이다. 이메일과
FCM의 `SUCCEEDED`, `FAILED`, `RETRY_SCHEDULED`, `FINAL_FAILED`를 Outbox 상태와 혼동하지
않는다.

## 5. 예외와 오류 응답 경계

현재 HTTP 오류 응답은 `GlobalExceptionHandler`가 다음 유형을 공통 형식으로 변환한다.

| 오류 영역 | 예외와 코드 | HTTP 응답 기준 |
| --- | --- | --- |
| 인증·계정 | `AuthException`, `AuthErrorCode` | 상태 코드, `message`, `code` |
| 지도·장소·게시글·신고 | `MapException`, `MapErrorCode` | 상태 코드, `message`, `code` |
| 관리자 | `AdminException`, `AdminErrorCode` | 상태 코드, `message`, `code` |
| 알림 설정 | `NotificationsException`, `NotificationsErrorCode` | 상태 코드, `message`, `code` |
| rate limit | `RateLimitException` | 상태 코드, `message`, `code` |
| 입력·DB 제약 | validation, constraint, 일부 무결성 제약 | 입력 오류 또는 현재 제약별 변환 규칙 |

외부 연동용 `EmailSendException`, `FcmSendException`, S3 예외는 공개 API 오류 코드로 직접
새지 않게 한다. 동기 요청에서는 모듈 오류로 번역하고, Outbox handler에서는 실패 기록과
재시도 정책으로 처리한다.

`UsersException`은 현재 `ChangeInfoService`에서 사용하지만 `GlobalExceptionHandler`의
직접 처리 대상은 아니다. 이 문서는 이를 현재 구현 관찰로 기록하며, 응답 계약을 바꾸는
수정은 별도 이슈에서 OpenAPI baseline과 함께 다룬다.

## 6. 공개 계약 보존 기준

| 계약 | 기준 위치 | 리팩터링 시 해야 할 일 |
| --- | --- | --- |
| HTTP API | `src/test/resources/openapi-baseline`, `build.gradle` | 공개 API 변경이 있으면 `verifyOpenApiContract`로 호환성을 확인하고, 의도된 변경만 기준 스펙과 문서에 반영한다. |
| API 경로 | [v1 API 경로 정책](../api-path-policy.md) | 기존 v1 경로는 클라이언트 협의 없이 rename·삭제하지 않는다. |
| DB 스키마 | `src/main/resources/db/migration` | 적용된 migration은 수정하지 않고 새 version으로 추가한다. 운영 절차는 [DB migration Runbook](../database-migration.md)을 따른다. |
| DB 복구 | [DB 백업/복구 절차](../database-backup-restore.md) | schema 또는 데이터 변경 전 백업·복구 가능성을 확인한다. |
| 데이터 migration·호환 API | [마이그레이션·호환 API·롤백 기준](pingdom-2.0-migration-compatibility-rollback.md) | `EXPAND`·`BACKFILL`·`SWITCH`·`CONTRACT` 단계, 기존 앱·v1 API 호환, 이전 이미지와 DB 복구의 선택 조건을 확인한다. |
| 비동기·운영 | [운영 관측성](../observability.md) | Outbox 실패·재시도·고착 복구 지표와 요청 추적 ID를 확인한다. |
| 도메인 이벤트 | [목표 아키텍처와 도메인 이벤트](pingdom-2.0-domain-events.md) | 전달 보장, 소비자 멱등성, payload·재처리·관측성 계약을 확인한다. |
| 추천 행동 전환 | [장소 추천 행동 전환 도메인 기준](place-recommendation-conversion.md) | 노출·클릭·행동 전환의 귀속 조건, 원천 로그와 snapshot 차이, 재동기화 가능 범위를 확인한다. |

문서만 수정하는 작업은 OpenAPI export나 Flyway 통합 테스트를 실행하지 않는다. Java,
OpenAPI baseline, migration, 배포 설정 중 하나가 함께 변경될 때만 변경 유형에 맞는 검증을
추가한다.

## 7. 성공 지표와 판정

수치 기반 SLO는 운영 기준선이 확보된 뒤 변경 요청별로 정한다. 기준선 없이 임의의
성능·오류율 수치를 성공 기준으로 선언하지 않는다. 아래 지표는 모든 리팩터링에서
확인한다.

| 지표 | 증거 | 완료 판정 |
| --- | --- | --- |
| 책임 추적성 | 변경 목록에 모듈 소유자, 상태 전이, 계약 영향, 검증 방법이 모두 기록됨 | 변경 항목 100%가 표의 네 항목을 가진다. |
| 경계 보존 | 코드 리뷰와 모듈 의존 대조 | 새 직접 저장소 의존 또는 타 모듈 상태 변경은 공개 유스케이스·이벤트·전환 예외 중 하나로 설명된다. |
| API 호환성 | `verifyOpenApiContract` 결과 | API 관련 변경에서 비의도적 호환성 실패가 없다. |
| migration 안전성 | Flyway migration 통합 테스트와 배포 전 백업 | migration 관련 변경에서 새 migration, 검증 성공, 복구 경로가 함께 확인된다. |
| 비동기 안정성 | Outbox/알림 지표와 운영 로그 | `FAILED` 이벤트, 반복 `RETRY`, 고착 복구 증가를 확인하고, 남은 항목은 원인·조치 담당자를 기록한다. |
| 운영 가시성 | health, 요청 ID, 관련 metric | 배포 후 정상 readiness와 변경 유형에 맞는 관측 신호를 확인한다. |

변경별 상세 절차는 [리팩터링 적용·복구 Runbook](../refactoring-rollout-runbook.md)을
따른다.

## 8. 변경 이력과 검토 규칙

| 일자 | 범위 | 내용 | 상태 |
| --- | --- | --- | --- |
| 2026-07-10 | #534, #535 | 현재 모듈·상태·예외·계약 기준선과 성공 지표를 최초 문서화 | 완료 |
| 2026-07-10 | #536 | 목표 아키텍처 문서와 구현 패키지·OpenAPI·Flyway·운영 문서의 연결 기준을 정리 | 완료 |
| 2026-07-10 | #537 | 리팩터링 적용·점검·복구 Runbook을 추가 | 완료 |
| 2026-07-10 | #538, #539, #540, #541 | 추천 행동 전환의 용어·귀속 규칙·운영 점검 기준을 문서화 | 완료 |
| 2026-07-10 | #542, #543, #544, #545 | 도메인 이벤트·Outbox 책임, 전달 보장, 운영·복구 기준을 문서화 | 완료 |
| 2026-07-10 | #554, #555, #556, #557 | 데이터 migration, 호환 API, 단계적 배포와 rollback 판단 기준을 문서화 | 완료 |

이 문서 변경은 다음 순서로 검토한다.

1. 변경 대상이 소유 모듈과 상태 전이를 명확히 하는지 확인한다.
2. API, migration, 비동기 처리, 운영 절차 중 영향을 받는 계약을 표시한다.
3. 호환성·복구·관측 검증을 변경 요청에 포함한다.
4. 문서에서 확인한 코드 불일치는 별도 이슈로 분리한다.
