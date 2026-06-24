ALTER TABLE place_recommendation_traffic_policy
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN fallback_version VARCHAR(100);
