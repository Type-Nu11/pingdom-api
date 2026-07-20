ALTER TABLE map_place
    ADD COLUMN discovery_status VARCHAR(20) DEFAULT 'VISIBLE';

UPDATE map_place
SET discovery_status = 'VISIBLE'
WHERE discovery_status IS NULL;

ALTER TABLE map_place
    ADD CONSTRAINT ck_map_place_discovery_status_not_null
        CHECK (discovery_status IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_map_place_discovery_status
        CHECK (discovery_status IN ('VISIBLE', 'HIDDEN')) NOT VALID;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_discovery_status_not_null;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_discovery_status;

ALTER TABLE map_place
    ALTER COLUMN discovery_status SET NOT NULL;

ALTER TABLE map_place
    DROP CONSTRAINT ck_map_place_discovery_status_not_null;

CREATE INDEX IF NOT EXISTS idx_map_place_public_latest
    ON map_place (discovery_status, operating_status, map_place_id DESC)
    WHERE latitude BETWEEN -90.0 AND 90.0
      AND longitude BETWEEN -180.0 AND 180.0;

CREATE INDEX IF NOT EXISTS idx_map_place_public_popular
    ON map_place (discovery_status, operating_status, photo_count DESC, map_place_id DESC)
    WHERE latitude BETWEEN -90.0 AND 90.0
      AND longitude BETWEEN -180.0 AND 180.0;

CREATE INDEX IF NOT EXISTS idx_map_place_tourist_category_filter
    ON map_place_tourist_category (tourist_category, map_place_id);

