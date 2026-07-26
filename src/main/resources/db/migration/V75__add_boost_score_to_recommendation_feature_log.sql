ALTER TABLE place_recommendation_feature_log
    ADD COLUMN boost_score DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE place_recommendation_feature_log
    ADD CONSTRAINT ck_recommendation_feature_log_boost_score
        CHECK (boost_score >= 0 AND boost_score <= 0.25);
