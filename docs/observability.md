# 운영 관측성

리팩터링 배포에서 이 문서의 health·metric·alert를 확인하는 순서는
[Pingdom 2.0 출시 전환·적용·복구 Runbook](refactoring-rollout-runbook.md)을 따른다.
추천 노출·클릭·행동 전환의 원천 로그와 snapshot 대조 절차는
[장소 추천 행동 전환 도메인 기준](architecture/place-recommendation-conversion.md)을 따른다.
Spring 이벤트와 Outbox의 전달 보장·재처리 기준은
[Pingdom 2.0 목표 아키텍처와 도메인 이벤트](architecture/pingdom-2.0-domain-events.md)를
따른다.
HTTP 오류 코드, Outbox 상태, notification delivery 오류 코드의 구분과 재시도 판단은
[API 오류 코드 및 재시도 정책](api-error-code-retry-policy.md)을 따른다.

## Health

- Public: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- Protected: other `/actuator/**` endpoints require `ADMIN`.
- Readiness includes `readinessState`, `db`, and `redis`.
- Health details are not exposed.

## Request Correlation

- Incoming `X-Request-Id` is reused when it is safe.
- Missing or unsafe values are replaced with a generated UUID.
- The resolved value is returned as `X-Request-Id` and added to MDC as `requestId`.

## 공급자 실패 추적

`POST /routes`와 음성 AI 메시지 실패는 WARN 로그의 `provider`, `outcome`과
응답 헤더 `X-Request-Id`에 대응하는 `[requestId:...]`로 추적한다. 음성 메시지 본문의
`requestId`는 envelope/replay 식별자이며 HTTP 요청 추적 헤더와 별개다.

```sh
docker logs --since 30m pingdom-app-1 2>&1 | grep -F '[requestId:응답의-X-Request-Id]'
```

- 네이버 설정 누락: `outcome=configuration`, 활성화 여부와 각 키의 존재 여부만 기록한다.
- 네이버 호출 실패: 인증(`authentication`), 쿼터(`quota`), timeout, 비정상 응답(`invalid_response`)
  등과 `httpStatus`, 숫자 `providerCode`를 기록한다. `-1`은 아직 확보하지 못한 값이다.
- Gemini 호출 실패: 인증, 쿼터, HTTP 오류(`http_error`), timeout, 전송 오류(`transport_error`)를
  기록한다. 서비스의 `provider_call_failed`는 실패 정규화 기록이며 어댑터의 상세 분류와 함께 확인한다.
- Gemini 호출 시작(`outcome=started`)과 HTTP 응답 수신(`outcome=http_success`)은 INFO로 기록한다.
  호출 결과에는 `model`, `elapsedMs`와 확보한 `httpStatus`를 기록한다. 상태를 확보하지 못한 전송
  실패에서는 HTTP 상태를 추정하지 않는다. 모델 이름은 영숫자·점·밑줄·하이픈 1~128자만 기록하며
  그 밖의 설정값은 `unrecognized`로 대체한다. `elapsedMs`는 공급자 호출과 HTTP 응답 해석에 걸린
  시간이며 DB 대기·replay 조회·최종 envelope 검증을 포함한 전체 API 시간이 아니다.
  HTTP 200 기록 뒤에도 JSON 해석이나 envelope 검증이 실패할 수 있다.
- Gemini 응답 검증 실패: `reason`은 `missing_text`, `invalid_json`, `schema_version`,
  `request_id_mismatch`, `unsupported_kind`, `field_count`, `unexpected_field`, `missing_field`,
  `resource_id`, `date_format`, `time_format` 등 고정 사유다. 앱의 공개 오류 코드는 유지한다.
  공통 envelope와 text의 주요 실패에는 코드에 지정된 `field`와 JSON `valueType`
  (`STRING`, `NUMBER`, `BOOLEAN`, `OBJECT`, `ARRAY`, `NULL`, `MISSING`)을 추가한다.
  숫자의 실제 값이나 공급자가 생성한 알 수 없는 필드 이름은 기록하지 않는다.

키·JWT·좌표·사용자 발화·공급자 원문·예외 메시지와 stack trace는 이 로그에 기록하지 않는다.
따라서 과거 원문 복원은 지원하지 않으며 새 요청의 안전한 메타데이터로 실패를 분류한다.
`json-file` Docker 로그는 CloudWatch 전송을 의미하지 않는다. CloudWatch 조회가 필요하면
해당 배포의 수집 agent·수집 경로·권한을 별도로 확인한다. 수집 설정만으로 애플리케이션이
기록하지 않은 과거 오류가 생성되지는 않는다.

## Metrics

| Metric | Tags | Purpose |
| --- | --- | --- |
| `pingdom.outbox.events` | `status` | Current Outbox event count by status |
| `pingdom.outbox.processed` | `event_type`, `handler`, `result` | Outbox success, retry, final failure count |
| `pingdom.outbox.max_attempts_exceeded` | `event_type`, `handler` | Events that exceeded max attempts |
| `pingdom.outbox.stale_recovered` | none | Stale `PROCESSING` recovery count |
| `pingdom.outbox.manual_retry` | `event_type`, `result` | 관리자 수동 재처리 성공·거절 결과 |
| `pingdom.auth.failures` | `code`, `source`, `status` | Authentication failure count |
| `pingdom.auth.refresh_token` | `result`, `reason` | Refresh token success/failure count |
| `pingdom.recommendation.requests` | `recommendation_version` | Recommendation request count by version |
| `pingdom.recommendation.result_count` | `recommendation_version` | Recommended item count distribution |
| `pingdom.recommendation.snapshot_resync` | `result`, `reason` | Snapshot resync success/failure count |
| `pingdom.recommendation.snapshot_resync.items` | `item` | Snapshot resync affected item count |
| `pingdom.place.information_reverification_requested` | none | 장소 정보 재확인 요청 생성 수 |
| `pingdom.place.information_reverification_reminders` | none | 장소 정보 재확인 리마인드 발행 수 |
| `pingdom.place.information_reverification_status_updates` | `from_status`, `to_status` | 장소 정보 재확인 상태 전이 수 |
| `pingdom.scout.field_report_submitted` | `report_type` | Scout 현장 제보 생성 수 |
| `pingdom.scout.field_report_status_updates` | `from_status`, `to_status` | Scout 현장 제보 상태 전이 수 |
| `pingdom.scout.profile_status_updates` | `from_status`, `to_status` | Scout 프로필 상태 전이 수 |
| `pingdom.scout.activity_eligibility_status_updates` | `from_status`, `to_status` | Scout 활동 자격 상태 전이 수 |

Outbox 외 Spring 이벤트에는 현재 공통 처리 metric이 없다. 개인정보 이력과 추천 노출의
커밋 후 처리 실패는 listener 로그와 원래 요청의 `X-Request-Id`로 추적한다. 동기 신고
이벤트 실패는 요청 오류와 트랜잭션 결과를 함께 확인한다.

## Alert Criteria

- Page immediately when `pingdom.outbox.max_attempts_exceeded` increases.
- Investigate when `pingdom.outbox.events{status="FAILED"}` is greater than `0`.
- Investigate retry pressure when `pingdom.outbox.processed{result="retry"}` keeps increasing for more than one poll cycle.
- Investigate worker instability when `pingdom.outbox.stale_recovered` increases.
- Investigate repeated `pingdom.outbox.manual_retry{result="not_retryable"}` increases as duplicate or stale operator requests.
- Investigate authentication incidents when `pingdom.auth.failures{code="INVALID_TOKEN"}` spikes above the normal baseline.
- Investigate recommendation rollout issues when `pingdom.recommendation.requests` traffic unexpectedly shifts by `recommendation_version`.
- Investigate failed admin maintenance when `pingdom.recommendation.snapshot_resync{result="failure"}` increases.
- Investigate Spring event listener error logs with the originating request ID; do not treat their
  absence from Outbox metrics as successful delivery.

## Failure Investigation Links

- HTTP API 실패는 상태 코드, 응답 본문의 `code`, `X-Request-Id`를 함께 보존하고
  [API 오류 코드 및 재시도 정책](api-error-code-retry-policy.md)의 클라이언트 재시도 기준과 대조한다.
- Outbox 실패는 관리자 `GET /admin/outbox-events`에서 event ID, event type, aggregate,
  attempt count, 마지막 오류를 확인한다. payload와 deduplication key는 운영 API에 노출되지 않는다.
- 원인 제거와 중복 외부 효과 안전성을 확인한 `FAILED` event만
  `POST /admin/outbox-events/{eventId}/retry`로 재처리한다. 요청에는 사유가 필요하며
  `OUTBOX_RECOVERY` 권한, 관리자 감사 이력, `pingdom.outbox.manual_retry` metric이 적용된다.
- 알림 발송 실패는 관리자 `GET /admin/notification-deliveries` 조회 결과의 channel, status,
  notification type, provider error code를 Outbox 상태와 분리해 확인한다.
