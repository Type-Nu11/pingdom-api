CREATE TABLE merchant_owner_profile (
    user_id BIGINT PRIMARY KEY,
    business_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_merchant_owner_profile_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_owner_profile_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_merchant_owner_profile_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'REVOKED'))
);

CREATE INDEX idx_merchant_owner_profile_status_updated
    ON merchant_owner_profile (status, updated_at DESC, user_id DESC);

CREATE TABLE merchant_owner_place (
    place_id BIGINT PRIMARY KEY,
    merchant_owner_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_merchant_owner_place_place
        FOREIGN KEY (place_id) REFERENCES map_place (map_place_id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_owner_place_profile
        FOREIGN KEY (merchant_owner_user_id) REFERENCES merchant_owner_profile (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_merchant_owner_place_owner
    ON merchant_owner_place (merchant_owner_user_id, place_id);
