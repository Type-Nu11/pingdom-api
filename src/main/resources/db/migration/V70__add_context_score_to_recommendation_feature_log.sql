ALTER TABLE place_recommendation_feature_log
    ADD COLUMN context_score DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE place_recommendation_feature_log
    ADD CONSTRAINT ck_recommendation_feature_log_context_score
        CHECK (context_score >= 0 AND context_score <= 1);
