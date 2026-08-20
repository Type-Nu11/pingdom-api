ALTER TABLE place_registration_application
    ADD COLUMN application_type VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN legal_name VARCHAR(100),
    ADD COLUMN business_name VARCHAR(100),
    ADD COLUMN business_registration_number VARCHAR(255),
    ADD COLUMN merchant_display_name VARCHAR(100),
    ADD COLUMN merchant_contact_email VARCHAR(255),
    ADD COLUMN merchant_description VARCHAR(1000),
    ADD COLUMN merchant_contact_phone VARCHAR(30),
    ADD COLUMN existing_place_id BIGINT,
    ADD COLUMN previous_owner_user_id BIGINT,
    ADD COLUMN claim_reason VARCHAR(500),
    ADD COLUMN completed_place_id BIGINT,
    ADD COLUMN completed_at TIMESTAMP(6);

ALTER TABLE place_registration_application
    DROP CONSTRAINT ck_place_registration_status,
    ADD CONSTRAINT ck_place_registration_status
        CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','REGISTERED','COMPLETED','CANCELED')),
    ADD CONSTRAINT ck_place_registration_application_type
        CHECK (application_type IN ('LEGACY','NEW_PLACE','EXISTING_PLACE_CLAIM')),
    ADD CONSTRAINT fk_place_registration_completed_place
        FOREIGN KEY (completed_place_id) REFERENCES map_place (map_place_id) ON DELETE SET NULL;

ALTER TABLE place_registration_application
    DROP CONSTRAINT ck_place_registration_category,
    ADD CONSTRAINT ck_place_registration_category
        CHECK (category IN ('MUSIC','RESTAURANT','POP_UP','FASHION','BEAUTY','EXHIBITION','CAFE','CULTURAL_HERITAGE','OTHER'));

ALTER TABLE place_registration_application
    ADD CONSTRAINT ck_place_registration_merchant_contact_phone
        CHECK (merchant_contact_phone IS NULL OR merchant_contact_phone ~ '^\\+[1-9][0-9]{7,14}$');

ALTER TABLE place_registration_application
    DROP CONSTRAINT ck_place_registration_application_timestamps,
    ADD CONSTRAINT ck_place_registration_application_timestamps CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'PENDING' AND submitted_at IS NOT NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'REGISTERED' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND registered_place_id IS NOT NULL AND registered_at IS NOT NULL
            AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'COMPLETED' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND completed_place_id IS NOT NULL AND completed_at IS NOT NULL AND canceled_at IS NULL)
        OR (status = 'CANCELED' AND registered_place_id IS NULL AND registered_at IS NULL
            AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NOT NULL)
    );

CREATE INDEX idx_place_registration_application_type_status
    ON place_registration_application (application_type, status, updated_at DESC, id DESC);
