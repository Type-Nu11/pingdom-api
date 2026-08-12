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
- Readiness includes `readinessState` and `db`.
- Health details are not exposed.

## Request Correlation

- Incoming `X-Request-Id` is reused when it is safe.
- Missing or unsafe values are replaced with a generated UUID.
- The resolved value is returned as `X-Request-Id` and added to MDC as `requestId`.

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
| `pingdom.api.legacy.requests` | `method`, `path` | 추천을 제외한 레거시 API의 controller 진입 수 |
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

### Legacy API usage

`pingdom.api.legacy.requests`는 추천 API를 제외한 레거시 경로 13개의 호출 여부를
확인하기 위한 임시 철거 판단 metric이다. `path`는 실제 ID가 아닌 고정 endpoint
template만 사용하며, 애플리케이션 시작 시 모든 조합을 `0`으로 등록한다.

- 전체 경로 확인: `GET /actuator/metrics/pingdom.api.legacy.requests`
- 단일 경로 확인: `GET /actuator/metrics/pingdom.api.legacy.requests?tag=method:GET&tag=path:%2Fplace`
- 보호된 Actuator endpoint이므로 `ADMIN` 권한으로 조회한다.
- 요청 검증·binding을 통과해 controller에 진입한 호출만 집계한다.
- 값은 인스턴스 재시작 시 초기화된다. 단일 인스턴스의 짧은 `0` 관측만으로 삭제를
  결정하지 말고, 운영 중인 모든 인스턴스에서 합의한 관측 기간 동안 확인하거나
  외부 metric 수집기에 누적한 시계열을 기준으로 판단한다.

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
