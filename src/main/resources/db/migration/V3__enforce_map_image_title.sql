UPDATE map_image
SET title = '제목 없음'
WHERE title IS NULL OR BTRIM(title) = '';

ALTER TABLE map_image
    ALTER COLUMN title SET NOT NULL;
