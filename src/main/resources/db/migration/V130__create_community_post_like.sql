CREATE TABLE community_post_like (
    community_post_like_id BIGSERIAL PRIMARY KEY,
    community_post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_post_like_post FOREIGN KEY (community_post_id)
        REFERENCES community_post (community_post_id) ON DELETE CASCADE,
    CONSTRAINT uk_community_post_like_post_user UNIQUE (community_post_id, user_id)
);

CREATE INDEX idx_community_post_like_post_id ON community_post_like (community_post_id);
