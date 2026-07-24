UPDATE map_place
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE location IS NULL
  AND latitude BETWEEN -90.0 AND 90.0
  AND longitude BETWEEN -180.0 AND 180.0;

CREATE INDEX IF NOT EXISTS idx_map_place_location_gist
    ON map_place USING GIST (location)
    WHERE location IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_map_place_location_geography_gist
    ON map_place USING GIST ((location::geography))
    WHERE location IS NOT NULL;
