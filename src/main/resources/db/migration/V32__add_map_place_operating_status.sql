ALTER TABLE map_place
    ADD COLUMN operating_status VARCHAR(30) DEFAULT 'OPERATING',
    ADD COLUMN operating_status_checked_at TIMESTAMP(6);

UPDATE map_place
SET operating_status = 'OPERATING'
WHERE operating_status IS NULL;

ALTER TABLE map_place
    ADD CONSTRAINT ck_map_place_operating_status_not_null
        CHECK (operating_status IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_map_place_operating_status
        CHECK (operating_status IN ('OPERATING', 'TEMPORARILY_CLOSED', 'PERMANENTLY_CLOSED')) NOT VALID;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_operating_status_not_null;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_operating_status;

ALTER TABLE map_place
    ALTER COLUMN operating_status SET NOT NULL;

ALTER TABLE map_place
    DROP CONSTRAINT ck_map_place_operating_status_not_null;
