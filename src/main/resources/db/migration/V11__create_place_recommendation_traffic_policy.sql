CREATE TABLE place_recommendation_traffic_policy (
    recommendation_version VARCHAR(100) PRIMARY KEY,
    traffic_percentage INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
