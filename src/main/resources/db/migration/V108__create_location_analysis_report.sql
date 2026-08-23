CREATE TABLE location_analysis_report (
    report_id VARCHAR(36) PRIMARY KEY,
    report_name VARCHAR(200) NOT NULL,
    category VARCHAR(200) NOT NULL,
    region VARCHAR(300) NOT NULL,
    target_customer_group VARCHAR(200),
    operating_hours VARCHAR(200),
    email VARCHAR(320) NOT NULL,
    privacy_consent BOOLEAN NOT NULL,
    published_date DATE NOT NULL,
    analysis_basis_date DATE NOT NULL,
    html_content TEXT NOT NULL,
    pdf_content BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_location_analysis_report_email_created_at
    ON location_analysis_report (email, created_at DESC);
