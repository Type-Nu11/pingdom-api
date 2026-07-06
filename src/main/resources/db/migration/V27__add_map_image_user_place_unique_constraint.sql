DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM map_image
        WHERE user_id IS NOT NULL
          AND map_place_id IS NOT NULL
        GROUP BY user_id, map_place_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add uk_map_image_user_place: duplicate map_image rows exist for the same user_id and map_place_id.';
    END IF;
END $$;

ALTER TABLE map_image
    ADD CONSTRAINT uk_map_image_user_place UNIQUE (user_id, map_place_id);
