CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE map_place
    ADD COLUMN IF NOT EXISTS location geometry(Point, 4326);

ALTER TABLE map_place
    ADD COLUMN IF NOT EXISTS registrant VARCHAR(255);

UPDATE map_place
SET registrant = 'unknown'
WHERE registrant IS NULL OR BTRIM(registrant) = '';

ALTER TABLE map_place
    ALTER COLUMN registrant SET NOT NULL;

ALTER TABLE map_place
    ALTER COLUMN registrant DROP DEFAULT;
