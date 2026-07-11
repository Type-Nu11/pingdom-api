-- Concurrent creation prevents the English-name search index from blocking map_place writes.
-- A failed concurrent build can leave an invalid index, so retries remove it first.
DROP INDEX CONCURRENTLY IF EXISTS idx_map_place_english_name_trgm;

CREATE INDEX CONCURRENTLY idx_map_place_english_name_trgm
    ON map_place USING gin ((LOWER(english_name)) gin_trgm_ops)
    WHERE english_name IS NOT NULL;
