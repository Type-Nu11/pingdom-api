ALTER TABLE map_place
    ADD COLUMN road_address VARCHAR(255),
    ADD COLUMN jibun_address VARCHAR(255),
    ADD COLUMN postal_code VARCHAR(20),
    ADD COLUMN geocoding_source VARCHAR(20) DEFAULT 'LEGACY';

UPDATE map_place
SET geocoding_source = 'LEGACY'
WHERE geocoding_source IS NULL;

ALTER TABLE map_place
    ADD CONSTRAINT ck_map_place_geocoding_source_not_null
        CHECK (geocoding_source IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_map_place_geocoding_source
        CHECK (geocoding_source IN ('KAKAO', 'USER_PIN', 'ADMIN', 'LEGACY')) NOT VALID;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_geocoding_source_not_null;

ALTER TABLE map_place
    VALIDATE CONSTRAINT ck_map_place_geocoding_source;

ALTER TABLE map_place
    ALTER COLUMN geocoding_source SET NOT NULL;

ALTER TABLE map_place
    DROP CONSTRAINT ck_map_place_geocoding_source_not_null;
