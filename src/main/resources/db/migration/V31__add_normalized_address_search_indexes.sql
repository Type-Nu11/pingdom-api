-- flyway:executeInTransaction=false
-- Concurrent creation prevents normalized-address indexes from blocking map_place writes.
-- Failed concurrent builds can leave invalid indexes, so retries remove them first.
DROP INDEX CONCURRENTLY IF EXISTS idx_map_place_road_address_trgm;
DROP INDEX CONCURRENTLY IF EXISTS idx_map_place_jibun_address_trgm;

CREATE INDEX CONCURRENTLY idx_map_place_road_address_trgm
    ON map_place USING gin ((LOWER(road_address)) gin_trgm_ops)
    WHERE road_address IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_map_place_jibun_address_trgm
    ON map_place USING gin ((LOWER(jibun_address)) gin_trgm_ops)
    WHERE jibun_address IS NOT NULL;
