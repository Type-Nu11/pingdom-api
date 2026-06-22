DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'postgis'
    ) THEN
        RAISE EXCEPTION
            'PostGIS extension is required. Install it before running Flyway migrations.';
    END IF;
END
$$;

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
