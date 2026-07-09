# 운영 관측성

리팩터링 배포에서 이 문서의 health·metric·alert를 확인하는 순서는
[리팩터링 적용·복구 Runbook](refactoring-rollout-runbook.md)을 따른다.
추천 노출·클릭·행동 전환의 원천 로그와 snapshot 대조 절차는
[장소 추천 행동 전환 도메인 기준](architecture/place-recommendation-conversion.md)을 따른다.
Spring 이벤트와 Outbox의 전달 보장·재처리 기준은
[Pingdom 2.0 목표 아키텍처와 도메인 이벤트](architecture/pingdom-2.0-domain-events.md)를
따른다.

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
| `pingdom.auth.failures` | `code`, `source`, `status` | Authentication failure count |
| `pingdom.auth.refresh_token` | `result`, `reason` | Refresh token success/failure count |
| `pingdom.recommendation.requests` | `recommendation_version` | Recommendation request count by version |
| `pingdom.recommendation.result_count` | `recommendation_version` | Recommended item count distribution |
| `pingdom.recommendation.snapshot_resync` | `result`, `reason` | Snapshot resync success/failure count |
| `pingdom.recommendation.snapshot_resync.items` | `item` | Snapshot resync affected item count |

Outbox 외 Spring 이벤트에는 현재 공통 처리 metric이 없다. 개인정보 이력과 추천 노출의
커밋 후 처리 실패는 listener 로그와 원래 요청의 `X-Request-Id`로 추적한다. 동기 신고
이벤트 실패는 요청 오류와 트랜잭션 결과를 함께 확인한다.

## Alert Criteria

- Page immediately when `pingdom.outbox.max_attempts_exceeded` increases.
- Investigate when `pingdom.outbox.events{status="FAILED"}` is greater than `0`.
- Investigate retry pressure when `pingdom.outbox.processed{result="retry"}` keeps increasing for more than one poll cycle.
- Investigate worker instability when `pingdom.outbox.stale_recovered` increases.
- Investigate authentication incidents when `pingdom.auth.failures{code="INVALID_TOKEN"}` spikes above the normal baseline.
- Investigate recommendation rollout issues when `pingdom.recommendation.requests` traffic unexpectedly shifts by `recommendation_version`.
- Investigate failed admin maintenance when `pingdom.recommendation.snapshot_resync{result="failure"}` increases.
- Investigate Spring event listener error logs with the originating request ID; do not treat their
  absence from Outbox metrics as successful delivery.
