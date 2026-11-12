-- 진단과 인덱스 생성 사이에 새 중복이 들어오지 않도록 동일 트랜잭션에서 쓰기를 막습니다.
-- 중복 이력을 임의 삭제하거나 심사 완료로 바꾸지 않습니다.
LOCK TABLE report_appeal IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM report_appeal
        WHERE status = 'SUBMITTED'
        GROUP BY report_id, appellant_user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V177: duplicate SUBMITTED report appeals require an explicit preservation policy'
            USING HINT = 'Inspect SELECT report_id, appellant_user_id, array_agg(id ORDER BY id) FROM report_appeal WHERE status = ''SUBMITTED'' GROUP BY report_id, appellant_user_id HAVING COUNT(*) > 1. Resolve duplicates without discarding their history before retrying.';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_report_appeal_submitted
    ON report_appeal (report_id, appellant_user_id)
    WHERE status = 'SUBMITTED';
