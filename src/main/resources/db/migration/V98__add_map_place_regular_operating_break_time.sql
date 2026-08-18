CREATE TABLE map_place_regular_operating_break_time (
    map_place_id BIGINT NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    CONSTRAINT pk_map_place_regular_operating_break_time PRIMARY KEY (map_place_id, day_of_week, opens_at, closes_at),
    CONSTRAINT fk_map_place_regular_operating_break_time_place FOREIGN KEY (map_place_id) REFERENCES map_place (map_place_id) ON DELETE NO ACTION,
    CONSTRAINT ck_map_place_regular_operating_break_time_range CHECK (opens_at <> closes_at)
);
