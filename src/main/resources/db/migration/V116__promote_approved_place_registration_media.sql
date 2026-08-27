ALTER TABLE map_place
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000);

ALTER TABLE place_media
    ADD COLUMN IF NOT EXISTS source_registration_attachment_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_place_media_registration_attachment'
    ) THEN
        ALTER TABLE place_media
            ADD CONSTRAINT fk_place_media_registration_attachment
            FOREIGN KEY (source_registration_attachment_id)
            REFERENCES place_registration_application_attachment (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_place_media_source_registration_attachment
    ON place_media (source_registration_attachment_id)
    WHERE source_registration_attachment_id IS NOT NULL;

-- 기존 중복 순서는 기존 display_order와 생성 순서를 기준으로 안정적으로 정규화합니다.
WITH ordered_representative_images AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY application_id
               ORDER BY display_order ASC, id ASC
           ) - 1 AS normalized_display_order
    FROM place_registration_application_attachment
    WHERE document_type = 'REPRESENTATIVE_IMAGE'
      AND deleted_at IS NULL
)
UPDATE place_registration_application_attachment attachment
SET display_order = ordered.normalized_display_order
FROM ordered_representative_images ordered
WHERE attachment.id = ordered.id
  AND attachment.display_order <> ordered.normalized_display_order;

CREATE UNIQUE INDEX IF NOT EXISTS uq_registration_attachment_representative_display_order
    ON place_registration_application_attachment (application_id, display_order)
    WHERE document_type = 'REPRESENTATIVE_IMAGE'
      AND deleted_at IS NULL;
