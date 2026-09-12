CREATE TABLE community_post_comment (
    community_post_comment_id BIGSERIAL PRIMARY KEY,
    community_post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_post_comment_post
        FOREIGN KEY (community_post_id)
        REFERENCES community_post (community_post_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_community_post_comment_post_created_at
    ON community_post_comment (community_post_id, created_at DESC, community_post_comment_id DESC);
