CREATE TABLE community_report (
    community_report_id BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL CHECK (reporter_user_id > 0),
    community_post_id BIGINT REFERENCES community_post (community_post_id),
    community_post_comment_id BIGINT REFERENCES community_post_comment (community_post_comment_id),
    reason VARCHAR(30) NOT NULL,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_by_admin_user_id BIGINT,
    processed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_community_report_target CHECK (
        (community_post_id IS NOT NULL AND community_post_comment_id IS NULL)
        OR (community_post_id IS NULL AND community_post_comment_id IS NOT NULL)
    ),
    CONSTRAINT ck_community_report_reason CHECK (
        reason IN ('SPAM', 'ABUSE', 'INAPPROPRIATE_CONTENT', 'PERSONAL_INFORMATION', 'OTHER')
    ),
    CONSTRAINT ck_community_report_description CHECK (description ~ '[^[:space:]]'),
    CONSTRAINT ck_community_report_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT ck_community_report_processing CHECK (
        (status = 'PENDING' AND processed_by_admin_user_id IS NULL AND processed_at IS NULL)
        OR (status IN ('ACCEPTED', 'DECLINED') AND processed_by_admin_user_id IS NOT NULL
            AND processed_by_admin_user_id > 0 AND processed_at IS NOT NULL AND processed_at >= created_at)
    ),
    CONSTRAINT uk_community_report_reporter_post UNIQUE (reporter_user_id, community_post_id),
    CONSTRAINT uk_community_report_reporter_comment UNIQUE (reporter_user_id, community_post_comment_id)
);

CREATE INDEX idx_community_report_status_created
    ON community_report (status, created_at DESC, community_report_id DESC);
CREATE INDEX idx_community_report_post_status
    ON community_report (community_post_id, status) WHERE community_post_id IS NOT NULL;
CREATE INDEX idx_community_report_comment_status
    ON community_report (community_post_comment_id, status) WHERE community_post_comment_id IS NOT NULL;
