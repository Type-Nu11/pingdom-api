ALTER TABLE community_post ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE community_post ADD CONSTRAINT ck_community_post_view_count CHECK (view_count >= 0);

CREATE TABLE community_post_media (
    community_post_media_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    image_url VARCHAR(2048) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('UPLOADED', 'CONNECTED')),
    expires_at TIMESTAMP NOT NULL,
    community_post_id BIGINT REFERENCES community_post (community_post_id),
    display_order INTEGER,
    connected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_community_post_media_order UNIQUE (community_post_id, display_order),
    CONSTRAINT ck_community_post_media_connection CHECK (
        (status = 'UPLOADED' AND community_post_id IS NULL AND display_order IS NULL AND connected_at IS NULL)
        OR (status = 'CONNECTED' AND community_post_id IS NOT NULL AND display_order IS NOT NULL
            AND display_order BETWEEN 0 AND 9 AND connected_at IS NOT NULL)
    )
);
CREATE INDEX idx_community_post_media_expiration ON community_post_media (status, expires_at);

CREATE TABLE community_post_daily_view (
    community_post_daily_view_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    community_post_id BIGINT NOT NULL REFERENCES community_post (community_post_id) ON DELETE CASCADE,
    viewed_on DATE NOT NULL,
    CONSTRAINT uk_community_post_daily_view UNIQUE (user_id, community_post_id, viewed_on)
);

ALTER TABLE community_post_comment ADD COLUMN parent_comment_id BIGINT;
-- 같은 게시글의 부모만 참조하게 해 경로 ID 혼용에 대한 서비스 검증을 DB에서도 보강한다.
ALTER TABLE community_post_comment ADD CONSTRAINT uk_community_comment_id_post
    UNIQUE (community_post_comment_id, community_post_id);
ALTER TABLE community_post_comment ADD CONSTRAINT fk_community_comment_parent
    FOREIGN KEY (parent_comment_id, community_post_id)
    REFERENCES community_post_comment (community_post_comment_id, community_post_id);
ALTER TABLE community_post_comment ADD CONSTRAINT ck_community_comment_not_self_parent
    CHECK (parent_comment_id IS NULL OR parent_comment_id <> community_post_comment_id);
CREATE INDEX idx_community_comment_parent_created ON community_post_comment
    (community_post_id, parent_comment_id, created_at DESC, community_post_comment_id DESC) WHERE hidden = FALSE;

CREATE TABLE community_comment_like (
    community_comment_like_id BIGSERIAL PRIMARY KEY,
    community_post_comment_id BIGINT NOT NULL REFERENCES community_post_comment (community_post_comment_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    CONSTRAINT uk_community_comment_like_comment_user UNIQUE (community_post_comment_id, user_id)
);
CREATE INDEX idx_community_post_visible_created ON community_post
    (created_at DESC, community_post_id DESC) WHERE hidden = FALSE;
