ALTER TABLE place_recommendation_feature_log
    ADD COLUMN trust_score DOUBLE PRECISION NOT NULL DEFAULT 0.5;

ALTER TABLE place_recommendation_feature_log
    ADD CONSTRAINT ck_recommendation_feature_log_trust_score
        CHECK (trust_score BETWEEN 0.0 AND 1.0);

CREATE INDEX idx_visitor_verification_report_accepted_place_reporter
    ON visitor_verification_report (place_id, reporter_user_id)
    WHERE status = 'ACCEPTED';
