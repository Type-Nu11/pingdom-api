CREATE UNIQUE INDEX uq_place_registration_application_pending_existing_claim
    ON place_registration_application (existing_place_id)
    WHERE application_type = 'EXISTING_PLACE_CLAIM' AND status = 'PENDING';

CREATE TABLE merchant_place_application_review_history (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    before_status VARCHAR(20) NOT NULL,
    after_status VARCHAR(20) NOT NULL,
    reviewed_version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_owner_snapshot TEXT NOT NULL,
    team_snapshot TEXT NOT NULL,
    offer_snapshot TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_merchant_place_application_review_history_application
        FOREIGN KEY (application_id) REFERENCES place_registration_application (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_place_application_review_history_status
        CHECK (before_status IN ('PENDING')
            AND after_status IN ('REJECTED', 'COMPLETED'))
);

CREATE INDEX idx_merchant_place_application_review_history_application_created
    ON merchant_place_application_review_history (application_id, created_at DESC, id DESC);
