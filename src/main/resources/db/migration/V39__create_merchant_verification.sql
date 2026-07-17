CREATE TABLE merchant_verification (
    user_id BIGINT PRIMARY KEY,
    legal_name VARCHAR(100) NOT NULL,
    business_name VARCHAR(100) NOT NULL,
    business_registration_number VARCHAR(255) NOT NULL,
    identity_status VARCHAR(20) NOT NULL,
    business_status VARCHAR(20) NOT NULL,
    review_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_merchant_verification_profile
        FOREIGN KEY (user_id) REFERENCES merchant_owner_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_verification_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_merchant_verification_identity_status
        CHECK (identity_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_merchant_verification_business_status
        CHECK (business_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_merchant_verification_status_updated
    ON merchant_verification (identity_status, business_status, updated_at DESC, user_id DESC);
