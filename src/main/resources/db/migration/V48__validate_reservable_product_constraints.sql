ALTER TABLE place_availability VALIDATE CONSTRAINT fk_place_availability_product;
ALTER TABLE place_availability VALIDATE CONSTRAINT ck_place_availability_product_type;
ALTER TABLE reservation VALIDATE CONSTRAINT fk_reservation_product;
ALTER TABLE reservation VALIDATE CONSTRAINT ck_reservation_product_type;

ALTER TABLE place_availability ALTER COLUMN product_type SET NOT NULL;
ALTER TABLE reservation ALTER COLUMN product_type SET NOT NULL;
