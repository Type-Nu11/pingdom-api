# Pingdom 2.0 목표 아키텍처와 도메인 이벤트

## 1. 목적과 문서 상태

이 문서는 Pingdom Backend에서 도메인 이벤트와 Outbox를 선택하는 기준, 현재 이벤트의
책임과 전달 보장, 운영 시 확인·복구 경로를 정의한다. 모듈 간 후속 처리의 책임을
분리하되, 현재 구현보다 강한 전달 보장을 문서만으로 선언하지 않는 것이 목적이다.

이 문서는 2026-07-10 기준 `develop` 구현을 설명하고, 이후 신규 이벤트를 설계할 때의
목표 규칙을 함께 제시한다. 현재 구현과 목표 규칙이 다른 항목은 코드 변경으로 간주하며,
별도 이슈에서 API·Flyway·운영 영향을 검토한다.

관련 문서:

- [Pingdom 2.0 리팩터링 범위와 성공 지표](pingdom-2.0-refactoring.md)
- [목표 아키텍처](README.md)
- [Pingdom 2.0 출시 전환·적용·복구 Runbook](../refactoring-rollout-runbook.md)
- [운영 관측성](../observability.md)
- [API 오류 코드 및 재시도 정책](../api-error-code-retry-policy.md)
- [장소 추천 행동 전환 도메인 기준](place-recommendation-conversion.md)

### 1.1 포함 범위

- 현재 Spring 애플리케이션 이벤트와 Outbox 이벤트의 발행자·소비자·전달 방식
- 동기 트랜잭션, 커밋 후 처리, Outbox 처리의 선택 기준
- 이벤트 이름, payload, 멱등성, 추적 정보, 오류·재처리 기준
- 이벤트 변경 시 API, Flyway migration, OpenAPI baseline, 운영 문서의 검토 기준

### 1.2 제외 범위

- 기존 이벤트 이름, payload, handler, scheduler의 즉시 코드 변경
- 메시지 브로커 도입 또는 모놀리스 분리
- 공개 API, DB schema, Outbox 보관 정책의 변경
- 이벤트 처리 실패를 소급 보정하는 데이터 수정

## 2. 용어와 전달 보장

| 용어 | 의미 | 현재 구현의 예시 |
| --- | --- | --- |
| 도메인 이벤트 | 상태 변경 뒤 확정된 비즈니스 사실을 표현하는 애플리케이션 내부 메시지 | `PostReportCreatedEvent` |
| 커밋 후 이벤트 | 원래 트랜잭션이 성공한 뒤 후속 처리를 시작하는 Spring 이벤트 | 개인정보 이력, 추천 노출 기록 |
| Outbox 작업 요청 | DB 트랜잭션에서 Outbox row로 저장한 뒤 worker가 외부 부수효과를 수행하도록 하는 작업 | 이메일, FCM, S3 삭제 |
| 발행자 | 이벤트 사실 또는 작업 요청을 만드는 모듈·유스케이스 | `identity`, `engagement`, `place` |
| 소비자 | 이벤트를 받아 후속 상태·외부 효과를 처리하는 listener 또는 Outbox handler | 개인정보 이력 listener, notification handler |
| 멱등성 | 같은 메시지가 다시 처리돼도 의도하지 않은 상태 변경이 생기지 않는 성질 | Outbox deduplication key, 소비자별 중복 방지 |

`*_REQUESTED` Outbox type은 확정된 도메인 사실의 이름이 아니라 외부 처리를 요청하는
기술 작업 이름이다. 현재 이름은 호환성을 위해 유지한다. 신규 내부 도메인 이벤트는
`PostReported`, `UserWithdrawn`처럼 과거 시제로, 이미 확정된 사실만 표현한다.

### 2.1 처리 방식 선택 기준

| 상황 | 선택 | 이유와 제약 |
| --- | --- | --- |
| 핵심 상태와 후속 규칙이 반드시 함께 확정돼야 함 | 같은 동기 트랜잭션 또는 명시적 application 호출 | listener 실패가 원래 요청을 실패시킬 수 있다. |
| 원래 상태가 확정된 뒤의 내부 기록·집계이며 누락을 허용하거나 별도 보정할 수 있음 | `AFTER_COMMIT` Spring 이벤트 | DB에 작업이 남지 않으므로 전달·재처리를 보장하지 않는다. |
| 이메일, FCM, S3처럼 외부 시스템 호출 또는 재시도가 필요한 부수효과 | Outbox | 핵심 상태와 작업 요청을 함께 저장하고 worker 상태·backoff·최종 실패를 관리한다. |
| 처리 실패를 데이터 손실 없이 추적·재실행해야 함 | Outbox 또는 별도 영속 작업 모델 | 일반 Spring 이벤트와 executor는 대체 수단이 아니다. |

## 3. 현재 이벤트 인벤토리

### 3.1 Spring 애플리케이션 이벤트

| 이벤트 | 발행 시점·발행자 | 소비자와 효과 | 전달·실패·복구 기준 |
| --- | --- | --- | --- |
| `PostReportCreatedEvent` | `engagement.PostReportService`가 신고를 저장·flush한 뒤 같은 트랜잭션에서 발행 | `PostReportCreatedEventListener`가 신고 사유 중복과 다수 신고 조건으로 신고 점수를 조정 | 기본 `@EventListener`의 동기 호출이다. listener 예외는 발행 호출로 전파되어 원래 신고 트랜잭션을 실패시킬 수 있다. 별도 재시도 저장소는 없다. |
| `PrivacyProcessingEvent` | 사용자 데이터 export, 회원 탈퇴 요청·익명화에서 `identity`가 발행 | `privacy.PrivacyProcessingHistoryEventListener`가 개인정보 처리 이력을 별도 트랜잭션으로 저장 | `AFTER_COMMIT`과 `fallbackExecution=true`를 사용한다. 저장 실패는 로그로 남기고 원래 요청을 되돌리지 않으며 자동 재시도는 없다. |
| `PrivacyProcessingBulkEvent` | 보존 기간이 지난 탈퇴 사용자 정리에서 `identity`가 발행 | 개인정보 처리 이력을 사용자별로 생성해 저장 | 단건 이벤트와 같은 커밋 후·별도 트랜잭션 규칙을 따른다. 실패한 이력의 재생성은 별도 보정 작업으로만 검토한다. |
| `PlaceRecommendationExposureRecordRequestedEvent` | 추천 응답을 조합한 `place.PlaceRecommendationQueryServiceImpl`이 발행 | `PlaceRecommendationExposureEventListener`가 커밋 후 executor에서 노출 원천 로그를 저장 | 추천 응답과 노출 저장은 같은 보장이 아니다. executor 실패는 오류 로그만 남기며, 누락된 원천 노출은 snapshot 재동기화로 복구되지 않는다. |

`PrivacyProcessingEvent`의 `fallbackExecution=true`는 활성 트랜잭션이 없는 발행도
listener를 실행할 수 있음을 뜻한다. 따라서 해당 이벤트를 "항상 커밋 후"로 표현해서는
안 된다. 추천 노출 이벤트는 request ID를 payload에 넣지만 executor가 MDC를 자동 전파한다는
보장은 없으므로, 비동기 처리에 필요한 상관관계 식별자는 payload에 명시한다.

### 3.2 Outbox 작업 요청

Outbox는 `event_id`, `deduplication_key`, event type, payload, aggregate 정보와 처리 상태를
`outbox_event`에 저장한다. worker는 `PENDING` 또는 `RETRY`를 선점해 `PROCESSING`으로
전이하고, handler 결과에 따라 `SUCCEEDED`, `RETRY`, `FAILED`로 전이한다.

| Outbox type | 발행자와 payload 기준 | 소비자 | 멱등성·운영 기준 |
| --- | --- | --- | --- |
| `EMAIL_VERIFICATION_REQUESTED` | `identity.AuthServiceImpl`; 사용자 ID, 이메일, 인증 코드 | `notification.EmailVerificationOutboxHandler` | 사용자·인증 코드·만료 시각 기반 key를 사용한다. 이메일 발송 실패는 Outbox 상태와 delivery 기록으로 확인한다. |
| `PASSWORD_RESET_REQUESTED` | `identity.AuthServiceImpl`; 사용자 ID, 이메일, 재설정 token·만료 시각 | `notification.PasswordResetOutboxHandler` | token hash·만료 시각 기반 key를 사용한다. payload에는 민감 정보가 포함될 수 있으므로 로그·운영 조회에 원문을 노출하지 않고, 저장·보관 정책 변경은 보안 검토 이슈로 분리한다. |
| `MAP_IMAGE_LIKED` | `engagement.MapImageLikeService`; 이미지 ID, 소유자 ID, 좋아요 사용자 ID | `notification.MapImageLikedOutboxHandler` | 저장된 like ID 기반 key를 사용한다. FCM 실패와 notification delivery 결과를 함께 확인한다. |
| `S3_OBJECT_DELETE_REQUESTED` | `shared.S3ObjectDeleteOutboxPublisher`; S3 key, 삭제 사유, aggregate 정보 | `shared.S3ObjectDeleteOutboxHandler` | 정규화한 S3 key의 hash를 key로 사용한다. 동일 key 삭제는 소비자가 반복 실행돼도 안전해야 한다. |

Outbox의 `deduplication_key`는 같은 작업 요청의 중복 저장을 막는 기준일 뿐, 외부 공급자의
정확히 한 번(exactly-once) 처리를 보장하지 않는다. handler는 중복 호출과 부분 실패를
전제로 구현하고, aggregate ID와 event ID로 원인을 추적한다.

## 4. 신규 이벤트 설계 규칙

### 4.1 이름과 payload

1. 내부 도메인 이벤트는 과거 시제로 이름을 짓고, 아직 일어나지 않은 의도나 명령을 담지
   않는다.
2. payload에는 소비자가 필요한 최소 식별자만 넣는다. aggregate type·ID, 발생 시각, 이벤트
   버전, 요청 또는 상관관계 ID가 필요한지 설계 시 명시한다.
3. 개인정보, 인증 코드, 재설정 token 등 민감 정보는 필요한 외부 처리를 제외하고 payload에
   넣지 않는다. 예외가 필요하면 저장 위치, 접근 경로, 보관 기간, 로그 마스킹을 함께
   검토한다.
4. DTO나 Entity를 그대로 전달하지 않는다. 소비자가 의존해야 하는 값만 명시적 record 또는
   payload 타입으로 정의한다.

### 4.2 발행·소비·실패 규칙

1. 발행자는 상태 전이와 이벤트의 인과관계를 소유한다. 다른 모듈은 발행 모듈의 Entity를
   직접 수정해 이벤트를 대신 만들지 않는다.
2. 동기 listener는 원래 유스케이스의 성공 조건일 때만 사용한다. 그렇지 않으면
   `AFTER_COMMIT` 또는 Outbox를 검토한다.
3. 외부 호출, 재시도, 운영자가 실패를 조회·복구해야 하는 작업은 Outbox를 사용한다.
4. 소비자는 중복 처리, 순서 변경, 이미 삭제된 aggregate, 부분 실패를 안전하게 처리한다.
5. 이벤트 이름·payload·전달 방식 변경은 소비자와 운영 절차의 계약 변경이다. Outbox payload
   형식 변경은 대기 중인 기존 row를 처리할 수 있는지와 롤백 호환성을 먼저 검토한다.

### 4.3 구현 전 검토 항목

| 확인 항목 | 완료 조건 |
| --- | --- |
| 소유 모듈 | 발행자와 상태 전이의 소유자가 하나의 모듈·유스케이스로 명확하다. |
| 전달 보장 | 동기, 커밋 후, Outbox 중 하나를 선택한 이유와 유실 가능성을 기록했다. |
| 소비자 계약 | 각 소비자의 입력, 멱등성 key, 실패 시 원래 요청 영향이 명확하다. |
| 관측성 | event ID 또는 request ID, aggregate, handler, 실패 원인을 확인할 경로가 있다. |
| 재처리 | 재처리 가능 여부, 금지 조건, 수동 보정 책임자가 정해져 있다. |
| 공개 계약 | API·OpenAPI·Flyway·운영 문서 영향과 필요한 검증을 표시했다. |

## 5. 운영 점검과 장애 대응

### 5.1 Outbox

1. `FAILED` 또는 반복 `RETRY`를 발견하면 event ID, event type, aggregate type·ID, attempt
   count, last error와 요청 상관관계를 보존한다.
2. 공급자 설정, network, payload, handler 로그를 확인하고 원인을 먼저 제거한다.
3. 최종 실패 row는 `OUTBOX_RECOVERY` 권한을 가진 관리자가
   `POST /admin/outbox-events/{eventId}/retry`로 `RETRY` 전환할 수 있다. 요청 사유와 전후
   상태는 관리자 감사 이력에 남으며, 행 잠금으로 동일 event의 중복 상태 전이를 막는다.
   운영자는 DB를 직접 수정하지 않는다.
4. 재시도 전에는 외부 효과의 중복이 안전한지 확인한다. 재시도가 위험하면 handler를 멈추고
   영향 범위와 수동 보정 방식을 결정한다.

Outbox 상태 지표와 alert 기준은 [운영 관측성](../observability.md), 배포·복구의 공통 절차는
[Pingdom 2.0 출시 전환·적용·복구 Runbook](../refactoring-rollout-runbook.md)을 따른다.

### 5.2 비내구성 Spring 이벤트

1. 먼저 원래 명령의 요청 ID, aggregate ID, 발생 시각과 listener 오류 로그를 확보한다.
2. 동기 이벤트는 원래 요청이 실패했는지와 트랜잭션 rollback 여부를 확인한다. 같은 요청을
   무작정 재전송하지 않고, 중복 신고·상태 전이 조건을 먼저 검증한다.
3. 커밋 후 이벤트는 원래 상태가 이미 확정되었는지 확인한다. 실패한 개인정보 이력이나 추천
   노출은 자동 재시도 대상이 아니며, 재생성 가능 여부와 데이터 영향은 별도 보정으로
   판단한다.
4. 추천 노출의 원천 로그가 없으면 snapshot 재동기화로 복구되지 않는다. 지표 영향 범위와
   원인을 기록하고, 누락된 요청을 임의로 다시 만들지 않는다.

## 6. 공개 계약과 변경 이력

초기 문서화 작업은 Java 코드와 공개 계약을 변경하지 않았다. 이후 #809에서 관리자 Outbox
조회·재처리 API, 권한·감사·metric, Flyway 제약과 조회 인덱스를 추가했다. 비내구성 Spring
이벤트의 일반 재생과 메시지 브로커 도입은 여전히 별도 범위다.

| 계약 | 이벤트 변경 시 확인할 사항 |
| --- | --- |
| HTTP API·OpenAPI | 이벤트 변경이 요청 성공·오류 응답·비동기 완료 시점에 영향을 주는지 확인하고, API 변경 시 `verifyOpenApiContract`를 실행한다. |
| Flyway·DB | Outbox column, enum, payload 보관, deduplication key 변경은 새 migration과 기존 대기 row 호환성을 검토한다. |
| 운영 | worker, handler, retry, notification delivery, metric·alert와 Runbook을 함께 갱신한다. |
| 보안·개인정보 | payload의 민감 정보, 로그 마스킹, 접근 권한, 보관 기간을 검토한다. |

| 일자 | 범위 | 내용 | 상태 |
| --- | --- | --- | --- |
| 2026-07-10 | #542, #543 | 현재 Spring 이벤트와 Outbox 작업 요청의 책임·전달 보장·설계 기준을 문서화 | 완료 |
| 2026-07-10 | #544 | 구현·계약 대조 기준과 기존 제한 사항을 기록 | 완료 |
| 2026-07-10 | #545 | Outbox 및 비내구성 이벤트의 점검·복구 절차를 Runbook·관측성 문서에 연결 | 완료 |
| 2026-08-10 | #809, #810, #811 | Outbox 운영 조회·권한 기반 수동 재처리·감사·관측 계약 추가 | 완료 |
