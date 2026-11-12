-- Worker 선점을 중지하고 기존 실행이 종료된 상태에서 전체 버전을 전환한다.
-- 구버전 PROCESSING 행의 NULL lease는 재개 후 복구 대상으로 처리한다.
ALTER TABLE outbox_event
    ADD COLUMN claim_token VARCHAR(36),
    ADD COLUMN lease_until TIMESTAMP;

CREATE INDEX idx_outbox_event_lease
    ON outbox_event (status, lease_until, event_id);
