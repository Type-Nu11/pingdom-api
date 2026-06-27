ALTER TABLE map_image
    ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);

ALTER TABLE map_image
    ADD COLUMN IF NOT EXISTS thumbnail_s3_key VARCHAR(500);
