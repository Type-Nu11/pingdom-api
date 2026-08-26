INSERT INTO outbox_event (
    event_id,
    deduplication_key,
    event_type,
    payload,
    aggregate_type,
    aggregate_id,
    status,
    attempt_count,
    next_attempt_at,
    created_at,
    updated_at,
    version
)
SELECT
    md5('legacy-merchant-place-claim:' || attachment.id),
    'S3_OBJECT_DELETE:' || md5(attachment.storage_key),
    'S3_OBJECT_DELETE_REQUESTED',
    json_build_object(
        's3Key', attachment.storage_key,
        'reason', 'LEGACY_MERCHANT_REVIEW_CLEANUP'
    )::text,
    'MERCHANT_PLACE_CLAIM_ATTACHMENT',
    attachment.claim_id::text,
    'PENDING',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM merchant_place_claim_attachment attachment
ON CONFLICT (deduplication_key) DO NOTHING;

INSERT INTO outbox_event (
    event_id,
    deduplication_key,
    event_type,
    payload,
    aggregate_type,
    aggregate_id,
    status,
    attempt_count,
    next_attempt_at,
    created_at,
    updated_at,
    version
)
SELECT
    md5('legacy-place-registration:' || attachment.id),
    'S3_OBJECT_DELETE:' || md5(attachment.storage_key),
    'S3_OBJECT_DELETE_REQUESTED',
    json_build_object(
        's3Key', attachment.storage_key,
        'reason', 'LEGACY_MERCHANT_REVIEW_CLEANUP'
    )::text,
    'PLACE_REGISTRATION_ATTACHMENT',
    attachment.application_id::text,
    'PENDING',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM place_registration_application_attachment attachment
JOIN place_registration_application application
    ON application.id = attachment.application_id
WHERE application.application_type = 'LEGACY'
ON CONFLICT (deduplication_key) DO NOTHING;

DELETE FROM merchant_place_application_review_history
WHERE application_id IN (
    SELECT id
    FROM place_registration_application
    WHERE application_type = 'LEGACY'
);

DELETE FROM place_registration_application
WHERE application_type = 'LEGACY';

DROP TABLE merchant_place_claim_review_history;
DROP TABLE merchant_place_claim_attachment;
DROP TABLE merchant_place_claim;

ALTER TABLE place_registration_application
    DROP COLUMN business_registration_file_id,
    DROP COLUMN identity_document_file_id,
    DROP COLUMN representative_image_file_ids,
    ALTER COLUMN application_type SET DEFAULT 'NEW_PLACE',
    DROP CONSTRAINT ck_place_registration_application_type,
    ADD CONSTRAINT ck_place_registration_application_type
        CHECK (application_type IN ('NEW_PLACE', 'EXISTING_PLACE_CLAIM'));
