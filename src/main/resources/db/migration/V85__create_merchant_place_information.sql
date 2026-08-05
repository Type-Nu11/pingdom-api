CREATE TABLE merchant_place_information (
    place_id BIGINT PRIMARY KEY,
    description VARCHAR(1000),
    contact_phone VARCHAR(30),
    website_url VARCHAR(500),
    reservation_url VARCHAR(500),
    updated_by_user_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_merchant_place_information_place
        FOREIGN KEY (place_id) REFERENCES map_place (map_place_id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_place_information_updater
        FOREIGN KEY (updated_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_merchant_place_information_place
        CHECK (place_id > 0),
    CONSTRAINT ck_merchant_place_information_updater
        CHECK (updated_by_user_id IS NULL OR updated_by_user_id > 0),
    CONSTRAINT ck_merchant_place_information_version
        CHECK (version >= 0),
    CONSTRAINT ck_merchant_place_information_description
        CHECK (description IS NULL OR char_length(btrim(description)) > 0),
    CONSTRAINT ck_merchant_place_information_contact_phone
        CHECK (contact_phone IS NULL OR char_length(btrim(contact_phone)) > 0),
    CONSTRAINT ck_merchant_place_information_website_url
        CHECK (website_url IS NULL OR website_url ~* '^https?://'),
    CONSTRAINT ck_merchant_place_information_reservation_url
        CHECK (reservation_url IS NULL OR reservation_url ~* '^https?://')
);

CREATE INDEX idx_merchant_place_information_updated
    ON merchant_place_information (updated_at DESC, place_id DESC);
