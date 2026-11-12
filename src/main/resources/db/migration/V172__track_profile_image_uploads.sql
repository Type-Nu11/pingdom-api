CREATE TABLE profile_image_upload_intent (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    s3_key VARCHAR(512) NOT NULL UNIQUE,
    image_url VARCHAR(2048) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    upload_deadline TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    orphaned_at TIMESTAMP(6) WITH TIME ZONE,
    next_check_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    delete_attempt VARCHAR(36),
    CONSTRAINT ck_profile_image_upload_state CHECK (state IN ('UPLOADING', 'ATTACHED', 'ORPHAN')),
    CONSTRAINT ck_profile_image_upload_orphan CHECK ((state = 'ORPHAN') = (orphaned_at IS NOT NULL)),
    CONSTRAINT ck_profile_image_upload_owner CHECK (user_id > 0)
);
-- 사용자 삭제 뒤에도 소유 key와 늦은 PUT을 재확인하므로 원장은 사용자 FK로 cascade하지 않습니다.
CREATE INDEX idx_profile_image_upload_due ON profile_image_upload_intent (next_check_at, id);
CREATE INDEX idx_profile_image_upload_owner_url ON profile_image_upload_intent (user_id, image_url);
-- 기존 2048자 UTF-8 URL도 유지하며 equality 조회에 필요한 고정 크기 hash만 인덱싱합니다.
CREATE INDEX idx_users_profile_image_url ON users USING HASH (profile_image_url);
