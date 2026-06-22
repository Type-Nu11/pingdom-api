DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM map_place
        WHERE kakao_place_id IS NOT NULL
        GROUP BY kakao_place_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot add uk_map_place_kakao_place_id: duplicate non-null kakao_place_id values exist';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'map_place'::regclass
          AND conname = 'uk_map_place_kakao_place_id'
    ) THEN
        ALTER TABLE map_place
            ADD CONSTRAINT uk_map_place_kakao_place_id UNIQUE (kakao_place_id);
    END IF;
END
$$;
