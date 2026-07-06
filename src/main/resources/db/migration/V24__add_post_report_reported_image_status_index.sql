CREATE INDEX IF NOT EXISTS idx_post_report_reported_image_status
    ON post_report (reported_image_id, status);
