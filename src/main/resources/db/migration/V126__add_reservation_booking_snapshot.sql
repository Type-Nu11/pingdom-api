ALTER TABLE reservation
    ADD COLUMN reservation_starts_at TIMESTAMP(6),
    ADD COLUMN reservation_ends_at TIMESTAMP(6),
    ADD COLUMN booker_name VARCHAR(100),
    ADD COLUMN booker_phone VARCHAR(30),
    ADD COLUMN request_note VARCHAR(500);

UPDATE reservation r
SET reservation_starts_at = a.starts_at,
    reservation_ends_at = a.ends_at
FROM place_availability a
WHERE a.id = r.availability_id;

CREATE INDEX idx_reservation_starts_at
    ON reservation (reservation_starts_at, id);
