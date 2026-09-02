CREATE INDEX idx_place_availability_nearby_reservable
    ON place_availability (status, starts_at, place_id, id)
    WHERE remaining_capacity > 0;
