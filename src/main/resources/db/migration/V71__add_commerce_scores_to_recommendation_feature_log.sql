ALTER TABLE place_recommendation_feature_log
    ADD COLUMN benefit_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN availability_score DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE place_recommendation_feature_log
    ADD CONSTRAINT ck_recommendation_feature_log_benefit_score
        CHECK (benefit_score >= 0 AND benefit_score <= 1),
    ADD CONSTRAINT ck_recommendation_feature_log_availability_score
        CHECK (availability_score >= 0 AND availability_score <= 1);
