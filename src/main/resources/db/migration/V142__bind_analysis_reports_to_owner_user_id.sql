-- 이메일 일치만으로는 생성자를 입증할 수 없어 기존 보고서를 자동 귀속하지 않습니다.
-- owner_user_id가 null인 기존 행은 사용자용 조회에서 제외하고 별도 검증 후 복구합니다.
ALTER TABLE location_analysis_report
    ADD COLUMN owner_user_id BIGINT;

ALTER TABLE location_analysis_report
    ADD CONSTRAINT ck_location_analysis_report_owner_positive
        CHECK (owner_user_id IS NULL OR owner_user_id > 0);

-- 사용자 최종 삭제 이후에도 보고서 귀속 식별자는 보존하며 새 FK 삭제 정책을 임의 도입하지 않습니다.
CREATE INDEX idx_location_analysis_report_owner_created_id
    ON location_analysis_report (owner_user_id, created_at DESC, report_id DESC)
    WHERE owner_user_id IS NOT NULL;
