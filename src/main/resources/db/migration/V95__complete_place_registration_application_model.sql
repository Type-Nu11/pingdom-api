ALTER TABLE place_registration_application
    ADD COLUMN submission_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN submission_content_hash VARCHAR(64),
    ADD COLUMN canceled_at TIMESTAMP(6);

CREATE TABLE place_registration_application_tag (
    application_id BIGINT NOT NULL,
    tag VARCHAR(40) NOT NULL,
    CONSTRAINT pk_place_registration_application_tag PRIMARY KEY (application_id, tag),
    CONSTRAINT fk_place_registration_tag_application
        FOREIGN KEY (application_id) REFERENCES place_registration_application (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_registration_tag
        CHECK (tag IN (
            'ENGLISH_SERVICE_AVAILABLE', 'ENGLISH_MENU_AVAILABLE', 'RESERVATION_AVAILABLE',
            'RESERVATION_COUPON_AVAILABLE', 'GENERAL_COUPON_AVAILABLE', 'GOOD_AMBIENCE'
        ))
);

CREATE INDEX idx_place_registration_tag_tag
    ON place_registration_application_tag (tag, application_id);

CREATE TABLE place_registration_application_attachment (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL,
    file_id VARCHAR(100),
    document_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    retention_expires_at TIMESTAMP(6),
    display_order INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_place_registration_attachment_application
        FOREIGN KEY (application_id) REFERENCES place_registration_application (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_registration_attachment_type
        CHECK (document_type IN ('BUSINESS_REGISTRATION', 'IDENTITY_DOCUMENT', 'REPRESENTATIVE_IMAGE')),
    CONSTRAINT ck_place_registration_attachment_size CHECK (file_size > 0),
    CONSTRAINT ck_place_registration_attachment_order CHECK (display_order >= 0),
    CONSTRAINT ck_place_registration_attachment_storage_key
        CHECK (storage_key NOT LIKE 'http://%' AND storage_key NOT LIKE 'https://%'
            AND storage_key NOT LIKE '/%' AND storage_key NOT LIKE '%..%'),
    CONSTRAINT ck_place_registration_attachment_hash_length
        CHECK (CHAR_LENGTH(file_hash) = 64)
);

CREATE INDEX idx_place_registration_attachment_application
    ON place_registration_application_attachment (application_id, document_type, display_order, id);

CREATE UNIQUE INDEX uq_place_registration_attachment_required_type
    ON place_registration_application_attachment (application_id, document_type)
    WHERE document_type IN ('BUSINESS_REGISTRATION', 'IDENTITY_DOCUMENT');

CREATE UNIQUE INDEX uq_place_registration_attachment_hash
    ON place_registration_application_attachment (application_id, document_type, file_hash);

ALTER TABLE place_registration_application
    ADD CONSTRAINT ck_place_registration_application_timestamps CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND canceled_at IS NULL)
        OR (status = 'PENDING' AND submitted_at IS NOT NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND canceled_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND registered_place_id IS NULL AND registered_at IS NULL
            AND canceled_at IS NULL)
        OR (status = 'REGISTERED' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND registered_place_id IS NOT NULL AND registered_at IS NOT NULL
            AND canceled_at IS NULL)
        OR (status = 'CANCELED' AND registered_place_id IS NULL AND registered_at IS NULL
            AND canceled_at IS NOT NULL)
    );
