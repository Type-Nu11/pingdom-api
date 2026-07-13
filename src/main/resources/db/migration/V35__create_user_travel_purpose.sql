CREATE TABLE user_travel_purpose (
    user_id BIGINT NOT NULL,
    travel_purpose VARCHAR(30) NOT NULL,
    CONSTRAINT pk_user_travel_purpose PRIMARY KEY (user_id, travel_purpose),
    CONSTRAINT fk_user_travel_purpose_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_travel_purpose_value
        CHECK (travel_purpose IN (
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
