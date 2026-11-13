# Outbox 선점 토큰과 lease 전환

Outbox 상태 변경은 선점 당시의 eventId와 claimToken을 함께 전달하고 DB 시각에서 lease가 유효한 경우에만 반영한다. 업무 handler의 멱등키는 기존 eventId를 유지한다. 외부 메일·FCM 전송의 exactly-once를 보장하지 않는다.

## 운영 설정

- processing-timeout: PT5M. 최초 lease와 정상 갱신의 최대 연장 폭이다.
- heartbeat-interval: PT30S. 실행 풀과 분리한 heartbeat 주기다.
- max-execution-time: PT30M. 선점 시각 기준 lease 연장 상한이며 정상 처리 시간과 외부 timeout을 측정하여 운영값을 조정한다.
- 모든 기간은 양수이며 heartbeat < lease <= max-execution-time이어야 한다.

실행 슬롯을 확보한 수만큼만 선점한다. 제출 거절은 실패 횟수 증가 없이 토큰을 검사하여 RETRY로 반환한다. heartbeat 오류는 일시 오류로 관측하고 이후 주기에 재시도한다. DB에서 토큰·lease가 거절되거나 최대 실행 시간이 지나면 heartbeat를 해제하고 실행 스레드에 interrupt를 요청한다. interrupt는 협조적 취소이며 실제 Runnable이 종료되기 전까지 슬롯을 반환하지 않는다.

## 적용

1. 모든 인스턴스의 Worker 선점을 중지한다.
2. 기존 실행이 종료되었는지 확인한다. 강제 종료한 실행의 외부 부작용은 eventId 기준으로 별도 확인한다.
3. V141 migration을 적용하고 모든 인스턴스를 새 버전으로 교체한다. 토큰을 검사하지 않는 구버전 Worker와 혼합 실행하지 않는다.
4. Worker를 재개한다. 기존 PROCESSING 행의 NULL lease는 복구 대상이며 기존 실패 횟수·backoff 정책을 사용한다.
5. lease 메트릭의 renewal_error, ownership_lost, max_execution_time, completion_rejected, failure_rejected와 미처리 개수를 확인한다.

## 롤백

Worker 선점을 먼저 중지하고 실행 종료·처리 중 행의 상태를 확인한 뒤 구버전으로 교체한다. 추가 컬럼과 인덱스는 유지한다. 구버전으로 돌아가면 이전 실행의 상태 덮어쓰기를 차단하는 토큰 보호가 사라진다.

긴 JVM 정지·DB 단절·무시된 interrupt 뒤에는 이전 handler와 새 Worker의 실행이 겹칠 수 있다. 토큰은 이전 실행의 Outbox 상태 갱신을 차단하지만 이미 진행한 외부 호출을 되돌리지 않는다.
