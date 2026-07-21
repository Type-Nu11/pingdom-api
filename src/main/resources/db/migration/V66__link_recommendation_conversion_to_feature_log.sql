ALTER TABLE place_recommendation_conversion
    ADD COLUMN place_recommendation_feature_log_id BIGINT;

ALTER TABLE place_recommendation_conversion
    ADD CONSTRAINT fk_recommendation_conversion_feature_log
        FOREIGN KEY (place_recommendation_feature_log_id)
        REFERENCES place_recommendation_feature_log (place_recommendation_feature_log_id)
        ON DELETE SET NULL;

CREATE INDEX idx_recommendation_feature_log_attribution
    ON place_recommendation_feature_log (request_id, user_id, place_id, recommendation_version);

CREATE INDEX idx_recommendation_conversion_feature_log
    ON place_recommendation_conversion (place_recommendation_feature_log_id)
    WHERE place_recommendation_feature_log_id IS NOT NULL;
