ALTER TABLE merchant_place_claim ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE merchant_place_claim_review_history (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    before_status VARCHAR(20) NOT NULL,
    after_status VARCHAR(20) NOT NULL,
    reviewed_version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_claim_review_history_claim FOREIGN KEY (claim_id)
        REFERENCES merchant_place_claim (id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_review_history_claim_created
    ON merchant_place_claim_review_history (claim_id, created_at DESC, id DESC);
