ALTER TABLE merchant_place_claim
    ADD COLUMN claim_type VARCHAR(30) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN previous_owner_user_id BIGINT;

ALTER TABLE merchant_place_claim
    ADD CONSTRAINT fk_merchant_place_claim_previous_owner
        FOREIGN KEY (previous_owner_user_id) REFERENCES merchant_owner_profile (user_id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_merchant_place_claim_type
        CHECK (claim_type IN ('INITIAL', 'OWNERSHIP_TRANSFER')),
    ADD CONSTRAINT ck_merchant_place_claim_transfer_owner
        CHECK (
            claim_type = 'OWNERSHIP_TRANSFER'
            OR previous_owner_user_id IS NULL
        );

ALTER TABLE merchant_place_claim
    ALTER COLUMN claim_type DROP DEFAULT;

CREATE INDEX idx_merchant_place_claim_previous_owner_created
    ON merchant_place_claim (previous_owner_user_id, created_at DESC, id DESC)
    WHERE previous_owner_user_id IS NOT NULL;
