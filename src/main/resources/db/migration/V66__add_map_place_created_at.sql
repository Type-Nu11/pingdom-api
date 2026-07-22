ALTER TABLE map_place
    ADD COLUMN created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP;

UPDATE map_place
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE map_place
    ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX idx_map_place_created_at
    ON map_place (created_at DESC, map_place_id DESC);
