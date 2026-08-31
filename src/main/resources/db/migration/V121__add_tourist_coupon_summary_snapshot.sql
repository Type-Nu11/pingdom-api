ALTER TABLE tourist_coupon
    ADD COLUMN offer_title VARCHAR(100),
    ADD COLUMN benefit_description VARCHAR(500),
    ADD COLUMN place_id BIGINT,
    ADD COLUMN place_name VARCHAR(100);

UPDATE tourist_coupon coupon
SET offer_title = offer.title,
    benefit_description = offer.benefit_description,
    place_id = offer.place_id,
    place_name = place.place_name
FROM tourist_offer offer
LEFT JOIN map_place place ON place.map_place_id = offer.place_id
WHERE coupon.offer_id = offer.id;
