ALTER TABLE map_place
    ADD COLUMN english_name VARCHAR(150),
    ADD COLUMN tourist_summary VARCHAR(500);

CREATE TABLE map_place_tourist_category (
    map_place_id BIGINT NOT NULL,
    tourist_category VARCHAR(30) NOT NULL,
    CONSTRAINT pk_map_place_tourist_category PRIMARY KEY (map_place_id, tourist_category),
    CONSTRAINT fk_map_place_tourist_category_place
        FOREIGN KEY (map_place_id) REFERENCES map_place (map_place_id) ON DELETE NO ACTION,
    CONSTRAINT ck_map_place_tourist_category_value
        CHECK (tourist_category IN (
            'K_POP',
            'BEAUTY',
            'FASHION',
            'CAFE',
            'FOOD',
            'POP_UP',
            'EXHIBITION',
            'NIGHTLIFE',
            'OTHER'
        ))
);

CREATE TABLE map_place_tourist_guard (
    map_place_id BIGINT NOT NULL,
    guard_key VARCHAR(16) NOT NULL,
    CONSTRAINT pk_map_place_tourist_guard PRIMARY KEY (map_place_id),
    CONSTRAINT fk_map_place_tourist_guard_place
        FOREIGN KEY (map_place_id) REFERENCES map_place (map_place_id) ON DELETE NO ACTION,
    CONSTRAINT ck_map_place_tourist_guard_key CHECK (guard_key = 'ACTIVE')
);
