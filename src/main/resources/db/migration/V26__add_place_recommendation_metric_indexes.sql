CREATE INDEX IF NOT EXISTS idx_place_recommendation_exposure_metric_aggregation
    ON place_recommendation_exposure (created_at, recommendation_version, place_id);

CREATE INDEX IF NOT EXISTS idx_place_recommendation_click_metric_aggregation
    ON place_recommendation_click (created_at, recommendation_version, place_id);

CREATE INDEX IF NOT EXISTS idx_place_recommendation_conversion_metric_aggregation
    ON place_recommendation_conversion (created_at, recommendation_version, place_id);
