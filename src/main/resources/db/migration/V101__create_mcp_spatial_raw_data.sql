CREATE TABLE mcp_spatial_raw_data (
    account_id VARCHAR(255) PRIMARY KEY,
    birth_year INTEGER NOT NULL,
    gender VARCHAR(32) NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    geom geometry(Point, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
        ) STORED,
    CONSTRAINT ck_mcp_spatial_raw_data_account_id
        CHECK (BTRIM(account_id) <> ''),
    CONSTRAINT ck_mcp_spatial_raw_data_birth_year
        CHECK (birth_year BETWEEN 0 AND 9999),
    CONSTRAINT ck_mcp_spatial_raw_data_gender
        CHECK (BTRIM(gender) <> ''),
    CONSTRAINT ck_mcp_spatial_raw_data_longitude
        CHECK (longitude BETWEEN -180.0 AND 180.0),
    CONSTRAINT ck_mcp_spatial_raw_data_latitude
        CHECK (latitude BETWEEN -90.0 AND 90.0)
);

CREATE INDEX idx_mcp_spatial_raw_data_geom_gist
    ON mcp_spatial_raw_data USING GIST (geom);
