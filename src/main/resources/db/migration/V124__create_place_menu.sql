CREATE TABLE place_menu (
    place_menu_id BIGSERIAL PRIMARY KEY,
    place_id BIGINT NOT NULL,
    merchant_owner_user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price_amount BIGINT NOT NULL CHECK (price_amount > 0 AND price_amount <= 1000000000),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('KRW', 'USD', 'JPY', 'CNY', 'EUR')),
    image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'SOLD_OUT', 'HIDDEN', 'INACTIVE')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_place_menu_place FOREIGN KEY (place_id) REFERENCES map_place (map_place_id) ON DELETE CASCADE,
    CONSTRAINT fk_place_menu_owner FOREIGN KEY (merchant_owner_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_place_menu_public_order ON place_menu (place_id, status, display_order, place_menu_id);
CREATE INDEX idx_place_menu_owner_place ON place_menu (merchant_owner_user_id, place_id);
