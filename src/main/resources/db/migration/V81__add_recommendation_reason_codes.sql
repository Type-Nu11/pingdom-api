ALTER TABLE place_recommendation_feature_log
    ADD COLUMN reason_code VARCHAR(40),
    ADD COLUMN limit_reason_code VARCHAR(40);

ALTER TABLE place_recommendation_feature_log
    ADD CONSTRAINT ck_recommendation_feature_log_reason_code CHECK (
        reason_code IS NULL OR reason_code IN (
            'BENEFIT_AND_RESERVABLE', 'ACTIVE_BENEFIT', 'RESERVABLE', 'CONTEXT_MATCH',
            'PERSONAL_SIGNAL', 'FRESH_CONTENT', 'HIGH_ENGAGEMENT', 'HIGH_CONVERSION',
            'EXPLORATION', 'QUALITY_SIGNAL', 'NEARBY'
        )
    ),
    ADD CONSTRAINT ck_recommendation_feature_log_limit_reason_code CHECK (
        limit_reason_code IS NULL OR limit_reason_code IN (
            'REQUEST_LIMIT_CLAMPED', 'RADIUS_EXPANDED', 'OPERATING_STATUS_PRIORITY',
            'INTERACTED_PLACE_EXCLUDED', 'FALLBACK_CANDIDATE_POOL'
        )
    );

CREATE INDEX idx_recommendation_feature_log_reason
    ON place_recommendation_feature_log (reason_code, created_at DESC);

CREATE INDEX idx_recommendation_feature_log_limit_reason
    ON place_recommendation_feature_log (limit_reason_code, created_at DESC)
    WHERE limit_reason_code IS NOT NULL;
