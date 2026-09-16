CREATE TABLE place_review_recommend_reason (
    review_id BIGINT NOT NULL REFERENCES place_review(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    reason VARCHAR(50) NOT NULL,
    PRIMARY KEY (review_id, display_order),
    CONSTRAINT uq_place_review_recommend_reason UNIQUE (review_id, reason)
);

CREATE TABLE place_review_media_upload (
    place_review_media_upload_id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL REFERENCES map_place(map_place_id),
    user_id BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    image_url VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 10485760),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    review_id BIGINT REFERENCES place_review(id),
    display_order INTEGER,
    connected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_place_review_media_upload_status CHECK (status IN ('UPLOADED', 'CONNECTED')),
    CONSTRAINT ck_place_review_media_upload_connection CHECK (
        (status = 'UPLOADED' AND review_id IS NULL AND display_order IS NULL AND connected_at IS NULL)
        OR (status = 'CONNECTED' AND review_id IS NOT NULL AND display_order IS NOT NULL AND connected_at IS NOT NULL)
    ),
    CONSTRAINT uq_place_review_media_upload_order UNIQUE (review_id, display_order)
);

CREATE INDEX idx_place_review_media_upload_expiry
    ON place_review_media_upload(status, expires_at, place_review_media_upload_id);
