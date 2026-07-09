# 운영 관측성

리팩터링 배포에서 이 문서의 health·metric·alert를 확인하는 순서는
[리팩터링 적용·복구 Runbook](refactoring-rollout-runbook.md)을 따른다.
추천 노출·클릭·행동 전환의 원천 로그와 snapshot 대조 절차는
[장소 추천 행동 전환 도메인 기준](architecture/place-recommendation-conversion.md)을 따른다.

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

## Alert Criteria

- Page immediately when `pingdom.outbox.max_attempts_exceeded` increases.
- Investigate when `pingdom.outbox.events{status="FAILED"}` is greater than `0`.
- Investigate retry pressure when `pingdom.outbox.processed{result="retry"}` keeps increasing for more than one poll cycle.
- Investigate worker instability when `pingdom.outbox.stale_recovered` increases.
- Investigate authentication incidents when `pingdom.auth.failures{code="INVALID_TOKEN"}` spikes above the normal baseline.
- Investigate recommendation rollout issues when `pingdom.recommendation.requests` traffic unexpectedly shifts by `recommendation_version`.
- Investigate failed admin maintenance when `pingdom.recommendation.snapshot_resync{result="failure"}` increases.
