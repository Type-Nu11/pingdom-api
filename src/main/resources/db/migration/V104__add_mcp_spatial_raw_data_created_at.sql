ALTER TABLE mcp_spatial_raw_data
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_mcp_spatial_raw_data_created_at
    ON mcp_spatial_raw_data (created_at);
