ALTER TABLE place_review
    ADD COLUMN visibility_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN hidden_at TIMESTAMP,
    ADD COLUMN deleted_at TIMESTAMP;

CREATE TABLE place_review_deletion_request (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES place_review(id),
    requester_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_reason VARCHAR(500) NOT NULL,
    reviewer_admin_user_id BIGINT,
    review_note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_place_review_deletion_request_pending
    ON place_review_deletion_request (review_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_place_review_deletion_request_status_created_at
    ON place_review_deletion_request (status, created_at DESC, id DESC);
