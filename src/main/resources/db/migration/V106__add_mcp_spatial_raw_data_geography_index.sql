CREATE INDEX idx_mcp_spatial_raw_data_geography_gist
    ON mcp_spatial_raw_data USING GIST ((geom::geography));
