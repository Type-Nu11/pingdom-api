ALTER TABLE place_registration_application
    DROP CONSTRAINT ck_place_registration_application_timestamps,
    DROP CONSTRAINT ck_place_registration_status;

UPDATE place_registration_application
SET status = 'COMPLETED',
    completed_place_id = registered_place_id,
    completed_at = registered_at
WHERE status = 'REGISTERED';

ALTER TABLE place_registration_application
    DROP COLUMN registered_place_id,
    DROP COLUMN registered_at;

ALTER TABLE place_registration_application
    ADD CONSTRAINT ck_place_registration_status
        CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','COMPLETED','CANCELED')),
    ADD CONSTRAINT ck_place_registration_application_timestamps CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'PENDING' AND submitted_at IS NOT NULL AND reviewed_at IS NULL
            AND reviewer_user_id IS NULL AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NULL)
        OR (status = 'COMPLETED' AND submitted_at IS NOT NULL AND reviewed_at IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND completed_place_id IS NOT NULL AND completed_at IS NOT NULL AND canceled_at IS NULL)
        OR (status = 'CANCELED' AND completed_place_id IS NULL AND completed_at IS NULL AND canceled_at IS NOT NULL)
    );
