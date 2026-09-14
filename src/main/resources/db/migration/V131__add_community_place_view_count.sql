ALTER TABLE map_place
    ADD COLUMN community_view_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE community_place_daily_view (
    community_place_daily_view_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    map_place_id BIGINT NOT NULL,
    viewed_on DATE NOT NULL,
    CONSTRAINT fk_community_place_daily_view_place
        FOREIGN KEY (map_place_id)
        REFERENCES map_place (map_place_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_community_place_daily_view_user_place_date
        UNIQUE (user_id, map_place_id, viewed_on)
);
