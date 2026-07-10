# API 오류 코드 및 재시도 정책

## 목적과 적용 범위

이 문서는 Pingdom Backend가 2026-07-10 기준으로 실제 반환하거나 기록하는 오류 코드와
재시도 동작을 구분해 설명한다. 클라이언트, 운영자, 외부 연동 handler가 오류를 같은
의미로 해석하고, 구현보다 강한 전달 보장이나 재시도 약속을 문서로 선언하지 않는 것이
목적이다.

이 문서는 Java 오류 처리, Security handler, Outbox worker, 공개 HTTP API, OpenAPI
annotation, Flyway migration, Outbox·notification delivery 스키마를 변경하지 않는다.
최종 실패 event를 재처리하는 공개 운영 API도 현재 제공하지 않는다.

관련 문서:

- [v1 API 경로 정책](api-path-policy.md)
- [운영 관측성](observability.md)
- [DB migration 운영 Runbook](database-migration.md)
- [Pingdom 2.0 목표 아키텍처와 도메인 이벤트](architecture/pingdom-2.0-domain-events.md)
- [Pingdom 2.0 출시 전환·적용·복구 Runbook](refactoring-rollout-runbook.md)

## 용어와 책임 경계

| 구분 | 책임 | 식별자 예시 | 재시도 의미 |
| --- | --- | --- | --- |
| HTTP API 오류 코드 | Controller, Security, Controller Advice의 클라이언트 응답 분류 | <code>INVALID_TOKEN</code>, <code>RATE_LIMIT_EXCEEDED</code> | 클라이언트가 요청을 다시 보낼지 판단하는 근거다. 서버의 자동 재시도를 뜻하지 않는다. |
| Outbox 상태 | 외부 부수효과 작업의 worker 처리 상태 | <code>PENDING</code>, <code>RETRY</code>, <code>FAILED</code> | worker가 동일 Outbox event를 다시 처리할지 나타낸다. |
| Notification delivery 오류 코드 | 이메일·FCM 발송 결과의 내부 기록 분류 | <code>EMAIL_SEND_FAILED</code>, <code>FCM_INVALID_TOKEN</code> | 발송 결과 조회·운영 분석용이며 Outbox 재시도를 직접 제어하지 않는다. |
| Provider 오류 코드 | Firebase·Postmark 등이 반환한 원본 코드 | provider별 문자열 | 내부 코드와 별도로 기록하며 API 계약 코드로 노출하지 않는다. |

HTTP API 오류와 비동기 delivery 결과는 같은 요청에서 함께 관찰될 수 있어도 서로 다른
계약이다. 좋아요 요청이 성공한 뒤 FCM 발송이 실패하면 HTTP 성공 여부와 notification
delivery 실패 기록을 분리해 판단한다.

## HTTP 오류 응답 계약

### 현재 응답 형태

| 발생 경로 | 현재 응답 본문 | 비고 |
| --- | --- | --- |
| 도메인 예외, rate-limit 예외, JWT 인증·인가 실패 | <code>{"message":"...","code":"..."}</code> | <code>code</code>는 클라이언트 분기용 문자열이다. |
| Bean Validation 실패 | <code>{"message":"입력값을 확인해주세요.","errors":{"field":"..."}}</code> | 공통 <code>code</code>는 없다. |
| ConstraintViolation, 일반 DataIntegrityViolation, ResponseStatusException | <code>{"message":"..."}</code> | 일부 제약 조건은 409 코드로 변환되지만, 그 외에는 <code>code</code>가 없다. |

따라서 모든 실패 응답에 <code>code</code>가 있다고 가정해서는 안 된다. OpenAPI의
<code>ErrorResponse</code> schema도 일부 Controller 문서에만 사용되므로, 현재 전체
API의 단일 오류 schema를 보장하지 않는다.

### API 코드 카탈로그

아래 상태 코드는 각 enum 또는 Security handler가 현재 설정한 값이다. 메시지는 사용자
표시 문구이므로 제어 흐름은 메시지가 아닌 상태 코드와 <code>code</code>으로 판단한다.

| 영역·정의 위치 | HTTP 상태 | 코드 |
| --- | --- | --- |
| 인증·계정 <code>AuthErrorCode</code> | 401 | <code>INVALID_CREDENTIALS</code>, <code>INVALID_TOKEN</code>, <code>EXPIRED_TOKEN</code>, <code>OAUTH_LINK_TOKEN_INVALID</code> |
| 인증·계정 <code>AuthErrorCode</code> | 403 | <code>ADMIN_ACCESS_REQUIRED</code>, <code>USER_BANNED</code>, <code>USER_WITHDRAWN</code> |
| 인증·계정 <code>AuthErrorCode</code> | 400 | <code>INVALID_PASSWORD_RESET_TOKEN</code>, <code>EXPIRED_PASSWORD_RESET_TOKEN</code>, <code>PASSWORD_MISMATCH</code>, <code>INVALID_EMAIL_VERIFICATION_CODE</code>, <code>EXPIRED_EMAIL_VERIFICATION_CODE</code> |
| 인증·계정 <code>AuthErrorCode</code> | 409 | <code>EMAIL_ALREADY_VERIFIED</code>, <code>DUPLICATE_USERNAME</code>, <code>DUPLICATE_EMAIL</code>, <code>OAUTH_EMAIL_CONFLICT</code>, <code>OAUTH_EMAIL_MISMATCH</code>, <code>OAUTH_ACCOUNT_ALREADY_LINKED</code>, <code>OAUTH_PASSWORD_CONFIRMATION_REQUIRED</code>, <code>OAUTH_LOCAL_PASSWORD_REQUIRED</code> |
| 인증·계정 <code>AuthErrorCode</code> | 404 | <code>OAUTH_ACCOUNT_NOT_LINKED</code>, <code>USER_NOT_FOUND</code> |
| JWT Security handler | 401 | <code>INVALID_TOKEN</code>, <code>EXPIRED_TOKEN</code> |
| JWT Security handler | 403 | <code>ACCESS_DENIED</code> |
| 사용자 <code>UsersErrorCode</code> | 선언상 400·404·409 | <code>PASSWORD_MISMATCH</code>, <code>USER_NOT_FOUND</code>, <code>USERNAME_ALREADY_EXISTS</code> |
| 지도·게시글 <code>MapErrorCode</code> | 400 | <code>PLACE_ID_REQUIRED</code>, <code>PLACE_SEARCH_CONDITION_INVALID</code>, <code>UNSUPPORTED_PLACE_SEARCH_SORT</code>, <code>IMAGE_FILE_EMPTY</code>, <code>IMAGE_FILE_TOO_LARGE</code>, <code>UNSUPPORTED_IMAGE_TYPE</code>, <code>INVALID_IMAGE_FILE</code>, <code>IMAGE_RESOLUTION_TOO_LARGE</code>, <code>ALREADY_LIKED</code>, <code>NOT_LIKED</code>, <code>ALREADY_POSTED</code> |
| 지도·게시글 <code>MapErrorCode</code> | 401 | <code>PLACE_COORDINATE_TOKEN_INVALID</code> |
| 지도·게시글 <code>MapErrorCode</code> | 403 | <code>REPORTER_RESTRICTED</code>, <code>REPORT_APPEAL_NOT_ALLOWED</code>, <code>OTHERS_NOT_DELETED</code>, <code>OTHERS_NOT_UPDATE</code>, <code>OTHERS_PLACE_NOT_DELETED</code> |
| 지도·게시글 <code>MapErrorCode</code> | 404 | <code>IMAGE_NOT_FOUND</code>, <code>REPORT_NOT_FOUND</code>, <code>PLACE_NOT_FOUND</code>, <code>RECOMMENDATION_EXPLANATION_NOT_FOUND</code>, <code>BOOKMARK_NOT_FOUND</code> |
| 지도·게시글 <code>MapErrorCode</code> | 409 | <code>ALREADY_REPORTED_IMAGE</code>, <code>REPORT_APPEAL_ALREADY_EXISTS</code>, <code>PLACE_ALREADY_EXISTS</code>, <code>FAVORITE_ALREADY_EXISTS</code>, <code>BOOKMARK_ALREADY_EXISTS</code> |
| 지도·게시글 <code>MapErrorCode</code> | 500 | <code>DELETE_ERROR</code>, <code>S3_NOT_CONFIGURED</code>, <code>S3_CONNECTION_ERROR</code>, <code>UPLOAD_ERROR</code> |
| 알림 <code>NotificationsErrorCode</code> | 400 | <code>CANNOT_SEND_NOTIFICATION_TO_SELF</code>, <code>INVALID_FCM_TOKEN</code>, <code>INVALID_NOTIFICATION_TIMEZONE</code>, <code>INVALID_QUIET_HOURS</code> |
| 알림 <code>NotificationsErrorCode</code> | 404 | <code>FCM_TOKEN_NOT_FOUND</code>, <code>NOTIFICATION_NOT_FOUND</code> |
| 알림 <code>NotificationsErrorCode</code> | 500 | <code>NOTIFICATION_SEND_FAILED</code> |
| 관리자 <code>AdminErrorCode</code> | 400 | <code>PLACE_MERGE_INVALID_REQUEST</code>, <code>RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST</code>, <code>RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID</code>, <code>RECOMMENDATION_METRIC_QUERY_TOO_LARGE</code>, <code>AD_INVALID_PERIOD</code>, <code>UNSUPPORTED_PLACE_SORT_PARAM</code>, <code>INVALID_SANCTION_PERIOD</code>, <code>INVALID_SANCTION_FILTER_PERIOD</code>, <code>INVALID_AUDIT_LOG_FILTER_PERIOD</code>, <code>INVALID_NOTIFICATION_DELIVERY_FILTER_PERIOD</code> |
| 관리자 <code>AdminErrorCode</code> | 404 | <code>POST_NOT_FOUND</code>, <code>PLACE_NOT_FOUND</code>, <code>PLACE_DUPLICATE_NOT_FOUND</code>, <code>PLACE_MERGE_HISTORY_NOT_FOUND</code>, <code>RECOMMENDATION_EXPLANATION_NOT_FOUND</code>, <code>RECOMMENDATION_TRAFFIC_POLICY_VERSION_NOT_FOUND</code>, <code>AD_NOT_FOUND</code>, <code>REPORT_NOT_FOUND</code>, <code>APPEAL_NOT_FOUND</code> |
| 관리자 <code>AdminErrorCode</code> | 409 | <code>PLACE_MERGE_NOT_ALLOWED</code>, <code>PLACE_MERGE_ALREADY_RESTORED</code>, <code>PLACE_MERGE_RESTORE_NOT_ALLOWED</code>, <code>PLACE_KAKAO_PLACE_ID_CONFLICT</code>, <code>REPORT_ALREADY_PROCESSED</code>, <code>APPEAL_ALREADY_PROCESSED</code>, <code>USER_NOT_BANNED</code>, <code>PENDING_REPORT_NOT_FOUND</code> |
| 관리자 <code>AdminErrorCode</code> | 500 | <code>AUDIT_LOG_WRITE_FAILED</code>, <code>RECOMMENDATION_POLICY_HISTORY_WRITE_FAILED</code>, <code>POST_DELETE_FAILED</code>, <code>S3_NOT_CONFIGURED</code>, <code>S3_CONNECTION_ERROR</code>, <code>S3_REPORT_FAILED</code> |
| 요청 제한 | 429 | <code>RATE_LIMIT_EXCEEDED</code> |
| 요청 제한 저장소 장애 | 503 | <code>RATE_LIMIT_UNAVAILABLE</code> |

<code>UsersException</code>은 현재 전용 Controller Advice handler가 없다. 따라서
<code>UsersErrorCode</code>은 선언된 도메인 코드이지만, 공통 JSON 응답으로 항상 노출되는
공개 API 계약으로 취급하지 않는다. 전체 오류 응답 표준화는 Controller Advice와 OpenAPI
baseline에 영향을 주므로 별도 구현 이슈에서 결정한다.

DataIntegrityViolation 중 사용자명 중복, OAuth 계정 연결 중복, 지도 북마크 중복, 장소별
게시글 중복은 409과 대응 코드로 변환된다. 그 외의 무결성 오류는 500과 메시지만 반환한다.

## HTTP 클라이언트 재시도 기준

서버는 일반 HTTP 요청의 재시도 횟수, backoff, 멱등성 키를 제공하지 않는다. 아래 표는
현재 응답을 받은 클라이언트의 안전한 기본 판단이며, 새로운 자동 재시도 계약을 추가하는
정책은 아니다.

| 응답 | 기본 처리 | 재시도 판단 |
| --- | --- | --- |
| 400, 404, 409 또는 validation 오류 | 요청 값·현재 리소스 상태를 수정한다. | 자동 재시도하지 않는다. |
| 401 <code>INVALID_TOKEN</code>, <code>EXPIRED_TOKEN</code> | 인증 정보를 갱신하거나 로그인 흐름을 수행한다. | 인증 복구 후에도 원래 요청이 조회 또는 멱등한 요청일 때만 다시 보낸다. |
| 403 | 권한·사용자 상태를 확인한다. | 자동 재시도하지 않는다. |
| 429 <code>RATE_LIMIT_EXCEEDED</code> | 요청 빈도를 낮춘다. | 현재 <code>Retry-After</code> 헤더가 없으므로 서버가 대기 시간을 약속하지 않는다. 조회 또는 멱등한 요청에 한해 제품별 제한된 backoff를 적용할 수 있다. |
| 503 <code>RATE_LIMIT_UNAVAILABLE</code> | 제한 저장소·의존성 장애로 본다. | 조회 또는 멱등한 요청에 한해 횟수가 제한된 backoff 재시도를 검토한다. 상태 변경 요청은 중복 효과를 먼저 검토한다. |
| 500 | 서버·외부 의존성 오류로 분류하고 요청 ID와 오류 코드를 보존한다. | 일반적인 자동 재시도 계약이 아니다. 같은 상태 변경 요청을 즉시 반복하지 않는다. |

## Outbox 및 notification delivery 재시도

### Outbox 상태 전이와 일정

Outbox worker는 5초 주기로 준비된 event를 선점한다. 설정은
<code>outbox.max-attempts=5</code>, <code>base-backoff=PT10S</code>,
<code>max-backoff=PT10M</code>, <code>processing-timeout=PT5M</code>이다.

~~~text
PENDING 또는 RETRY
        │ worker가 선점
        ▼
   PROCESSING ── 성공 ──► SUCCEEDED
        │
        ├─ 실패 1~4회: 10초, 20초, 40초, 80초 지연 후 RETRY
        └─ 실패 5회: FAILED
~~~

- 5분을 넘긴 <code>PROCESSING</code> event는 stale recovery 과정에서 같은 실패·backoff
  규칙을 적용한다.
- <code>FAILED</code> event는 코드상 <code>retryFailedEvent</code>로 시도 횟수를
  초기화해 <code>RETRY</code>로 돌릴 수 있지만, 현재 공개 운영 API는 제공하지 않는다.
  운영자는 DB를 직접 수정하지 않고 원인·멱등성·보정 범위를 먼저 검토한다.
- <code>deduplication_key</code>는 중복 event 저장을 막을 뿐 외부 공급자 호출의
  exactly-once 처리를 보장하지 않는다.

### 이메일과 FCM의 현재 차이

| 처리 | 실패 시 동작 | delivery 기록과 Outbox의 관계 |
| --- | --- | --- |
| 이메일 인증·비밀번호 재설정 | handler가 예외를 기록한 뒤 다시 던진다. worker가 Outbox를 <code>RETRY</code> 또는 <code>FAILED</code>로 전이한다. | <code>RETRY_SCHEDULED</code> delivery 기록은 최대 시도 횟수에서 <code>FINAL_FAILED</code>가 될 수 있다. |
| 좋아요 FCM의 무효 토큰 | 토큰을 삭제하고 실패를 기록한다. | handler 예외를 전파하지 않으므로 해당 Outbox event는 성공 처리될 수 있다. |
| 좋아요 FCM의 일시 실패 | <code>retryable=true</code>으로 실패를 기록한다. | 현재 FCM service가 예외를 잡아 전파하지 않으므로 <code>retryable=true</code>은 Outbox 재시도 예약을 뜻하지 않는다. |
| payload 역직렬화 실패 | handler가 예외를 던진다. | event는 일반 Outbox 실패 정책을 따른다. |

notification delivery의 <code>retryable</code>, <code>attempt_count</code>,
<code>RETRY_SCHEDULED</code>, <code>FINAL_FAILED</code>는 발송 결과 관찰용 모델이다.
이 값은 Outbox worker의 상태 전이를 직접 제어하지 않는다. FCM 일시 실패도 실제 Outbox
재시도로 처리해야 한다면 중복 전송, 트랜잭션 경계, notification 생성 중복을 검토하는
별도 구현 이슈가 필요하다.

## API·Flyway·OpenAPI·운영 영향

| 대상 | 현재 기준 | 이 문서 변경의 영향 |
| --- | --- | --- |
| HTTP API·OpenAPI | 오류 본문과 Security 응답은 기존 구현을 따른다. | Controller, DTO, annotation을 변경하지 않으므로 OpenAPI baseline을 갱신하거나 export하지 않는다. |
| Flyway | <code>V5__create_outbox_event.sql</code>이 Outbox 상태·시도 횟수·다음 시각을, <code>V20__create_notification_delivery.sql</code>이 delivery 결과를 저장한다. | 스키마·migration을 변경하지 않는다. enum·column·보관 정책 변경은 새 migration과 기존 대기 row 호환성을 별도 검토한다. |
| 운영 | Outbox metric과 handler 로그, notification delivery 조회가 실패 분석의 근거다. | 배포 전 문서 링크와 구현 대조만 수행한다. 코드·설정 변경이 없으므로 별도 배포 절차는 없다. |

## 운영 확인과 장애 대응

1. HTTP 오류는 상태 코드, <code>code</code>이 있으면 해당 코드, <code>X-Request-Id</code>를
   함께 보존한다. validation 오류는 <code>errors</code> 필드를 함께 기록한다.
2. Outbox 실패는 event ID, event type, aggregate, attempt count, last error를 보존한다.
3. <code>pingdom.outbox.max_attempts_exceeded</code> 증가 또는
   <code>pingdom.outbox.events{status="FAILED"}</code>가 0보다 큰 경우 즉시 원인을
   확인한다.
4. 이메일·FCM 문제는 provider 오류 코드, 내부 delivery 오류 코드, delivery 상태를
   Outbox 상태와 혼동하지 않고 대조한다.
5. 원인이 해결돼도 동일 event의 중복 외부 효과가 안전한지 확인하기 전에는 재처리하거나
   같은 상태 변경 요청을 다시 보내지 않는다.

## 변경 이력

| 일자 | 이슈 | 내용 | 상태 |
| --- | --- | --- | --- |
| 2026-07-10 | #839, #840, #841 | HTTP 오류 코드, Outbox·delivery 재시도 책임, API·Flyway·운영 연결 기준을 문서화 | 완료 |
