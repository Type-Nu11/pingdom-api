CREATE INDEX idx_place_review_user_visibility_created_at
    ON place_review (user_id, visibility_status, created_at DESC, id DESC);
