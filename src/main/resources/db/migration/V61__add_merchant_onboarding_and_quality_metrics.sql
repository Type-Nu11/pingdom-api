ALTER TABLE merchant_owner_profile
    ADD COLUMN onboarding_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN onboarding_completion_rate INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN onboarding_completed_at TIMESTAMP(6);

ALTER TABLE merchant_owner_profile
    ADD CONSTRAINT ck_merchant_owner_profile_onboarding_status
        CHECK (onboarding_status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    ADD CONSTRAINT ck_merchant_owner_profile_onboarding_completion_rate
        CHECK (onboarding_completion_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_merchant_owner_profile_onboarding_completed_at
        CHECK (
            (onboarding_status = 'COMPLETED' AND onboarding_completed_at IS NOT NULL)
            OR (onboarding_status <> 'COMPLETED' AND onboarding_completed_at IS NULL)
        );

CREATE INDEX idx_merchant_owner_profile_onboarding
    ON merchant_owner_profile (onboarding_status, onboarding_completion_rate DESC, updated_at DESC, user_id DESC);

ALTER TABLE merchant_owner_place
    ADD COLUMN operational_quality_status VARCHAR(20) NOT NULL DEFAULT 'UNMEASURED',
    ADD COLUMN reservation_response_rate INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reservation_cancellation_rate INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN no_show_rate INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN quality_evaluated_at TIMESTAMP(6);

ALTER TABLE merchant_owner_place
    ADD CONSTRAINT ck_merchant_owner_place_operational_quality_status
        CHECK (operational_quality_status IN ('UNMEASURED', 'HEALTHY', 'NEEDS_ATTENTION', 'AT_RISK')),
    ADD CONSTRAINT ck_merchant_owner_place_reservation_response_rate
        CHECK (reservation_response_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_merchant_owner_place_reservation_cancellation_rate
        CHECK (reservation_cancellation_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_merchant_owner_place_no_show_rate
        CHECK (no_show_rate BETWEEN 0 AND 100);

CREATE INDEX idx_merchant_owner_place_quality
    ON merchant_owner_place (
        operational_quality_status,
        reservation_response_rate DESC,
        reservation_cancellation_rate ASC,
        no_show_rate ASC,
        place_id
    );
