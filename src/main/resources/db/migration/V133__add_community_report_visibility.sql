ALTER TABLE community_post
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_by_admin_user_id BIGINT,
    ADD COLUMN hidden_at TIMESTAMP;

ALTER TABLE community_post_comment
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_by_admin_user_id BIGINT,
    ADD COLUMN hidden_at TIMESTAMP;

CREATE INDEX idx_community_post_visible_category_created_at
    ON community_post (category_id, created_at DESC, community_post_id DESC)
    WHERE hidden = FALSE;

CREATE INDEX idx_community_post_comment_visible_post_created_at
    ON community_post_comment (community_post_id, created_at DESC, community_post_comment_id DESC)
    WHERE hidden = FALSE;
