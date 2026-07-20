-- Validated CHECK constraints allow PostgreSQL to skip the table scan for SET NOT NULL.
ALTER TABLE place_availability ALTER COLUMN product_type SET NOT NULL;
ALTER TABLE reservation ALTER COLUMN product_type SET NOT NULL;
