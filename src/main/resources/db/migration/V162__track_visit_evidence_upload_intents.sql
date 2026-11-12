CREATE TABLE visit_evidence_upload_intent (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_in_id BIGINT NOT NULL,
    s3_key VARCHAR(512) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    upload_deadline TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    orphaned_at TIMESTAMP(6) WITH TIME ZONE,
    next_check_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    delete_attempt VARCHAR(36),
    CONSTRAINT ck_visit_evidence_upload_intent_state CHECK (state IN ('UPLOADING', 'ATTACHED', 'ORPHAN')),
    CONSTRAINT ck_visit_evidence_upload_intent_orphan CHECK ((state = 'ORPHAN') = (orphaned_at IS NOT NULL)),
    CONSTRAINT ck_visit_evidence_upload_intent_owner CHECK (user_id > 0 AND check_in_id > 0)
);
-- 사용자/체크인 삭제 이후에도 orphan key를 정리하므로 부모 FK의 cascade로 이 원장을 제거하지 않습니다.
CREATE INDEX idx_visit_evidence_upload_intent_due ON visit_evidence_upload_intent (next_check_at, id);
CREATE INDEX idx_visit_evidence_s3_key ON visit_evidence (s3_key);
