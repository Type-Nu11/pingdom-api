ALTER TABLE community_post
    ADD COLUMN updated_at timestamp,
    ADD COLUMN deleted_at timestamp;

CREATE INDEX idx_community_post_owner_created
    ON community_post (user_id, created_at DESC, community_post_id DESC)
    WHERE deleted_at IS NULL;
