ALTER TABLE place_recommendation_traffic_policy
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS fallback_version VARCHAR(100);
