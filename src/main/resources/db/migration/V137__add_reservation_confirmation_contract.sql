-- 기존 슬롯은 미설정 상태를 보존하며 무료나 취소 불가로 추정하지 않는다.
ALTER TABLE place_availability
    ADD COLUMN reservation_terms JSONB,
    ADD COLUMN conditions_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE reservation_quote (
    id VARCHAR(36) PRIMARY KEY,
    tourist_user_id BIGINT NOT NULL,
    confirmation JSONB NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(100),
    request_fingerprint VARCHAR(64),
    rejection_code VARCHAR(64),
    CONSTRAINT uq_reservation_quote_user_key UNIQUE (tourist_user_id, idempotency_key),
    CONSTRAINT ck_reservation_quote_binding CHECK (
        (idempotency_key IS NULL AND request_fingerprint IS NULL AND rejection_code IS NULL)
        OR (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
    )
);

ALTER TABLE reservation
    ADD COLUMN confirmation_token VARCHAR(36),
    ADD COLUMN confirmation JSONB,
    ADD CONSTRAINT fk_reservation_confirmation_token FOREIGN KEY (confirmation_token) REFERENCES reservation_quote(id),
    ADD CONSTRAINT uq_reservation_confirmation_token UNIQUE (confirmation_token),
    ADD CONSTRAINT ck_reservation_confirmation_pair CHECK ((confirmation_token IS NULL) = (confirmation IS NULL));

CREATE INDEX idx_reservation_quote_created_at ON reservation_quote(created_at);
CREATE INDEX idx_reservation_quote_user_created_at ON reservation_quote(tourist_user_id, created_at);
CREATE INDEX idx_reservation_quote_unused_user ON reservation_quote(tourist_user_id) WHERE idempotency_key IS NULL;
