ALTER TABLE post_report
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE post_report
SET created_at = processed_at
WHERE processed_at IS NOT NULL;

CREATE INDEX idx_post_report_map_image_status
    ON post_report (map_image_id, status);
