CREATE TABLE place_administrative_region (
    region_code VARCHAR(5) PRIMARY KEY,
    sido VARCHAR(50) NOT NULL,
    sigungu VARCHAR(50) NOT NULL,
    region_name VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE map_place
    ADD COLUMN region_code VARCHAR(5);

CREATE INDEX idx_map_place_local_hot_ranking
    ON map_place (region_code, discovery_status, operating_status, map_place_id DESC)
    WHERE region_code IS NOT NULL;

CREATE INDEX idx_map_bookmark_place_id
    ON map_bookmark (place_id);
