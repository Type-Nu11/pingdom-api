CREATE TABLE community_post (
    community_post_id BIGSERIAL PRIMARY KEY
);

CREATE TABLE community_post_place (
    community_post_place_id BIGSERIAL PRIMARY KEY,
    community_post_id BIGINT NOT NULL,
    map_place_id BIGINT NOT NULL,
    CONSTRAINT fk_community_post_place_post
        FOREIGN KEY (community_post_id)
        REFERENCES community_post (community_post_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_community_post_place_map_place
        FOREIGN KEY (map_place_id)
        REFERENCES map_place (map_place_id),
    CONSTRAINT uk_community_post_place_post_place
        UNIQUE (community_post_id, map_place_id)
);

CREATE INDEX idx_community_post_place_map_place_id
    ON community_post_place (map_place_id);
