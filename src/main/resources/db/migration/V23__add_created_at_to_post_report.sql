ALTER TABLE post_report
    ADD COLUMN created_at TIMESTAMP(6);

UPDATE post_report
SET created_at = COALESCE(processed_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE post_report
    ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX idx_post_report_map_image_status
    ON post_report (map_image_id, status);
