DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'pg_trgm'
    ) THEN
        RAISE EXCEPTION
            'pg_trgm extension is required. Install it before running Flyway migrations.';
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_map_place_name_trgm
    ON map_place USING gin ((LOWER(place_name)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_map_place_address_trgm
    ON map_place USING gin ((LOWER(address)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_map_place_category_lower
    ON map_place (LOWER(TRIM(category)))
    WHERE category IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_map_place_latitude_longitude
    ON map_place (latitude, longitude);
