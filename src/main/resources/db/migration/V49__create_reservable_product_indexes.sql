-- flyway:executeInTransaction=false
-- Existing availability and reservation tables remain writable while indexes are built.
-- Failed concurrent builds can leave invalid indexes, so retries remove them first.
DROP INDEX CONCURRENTLY IF EXISTS uq_place_availability_legacy_slot;
DROP INDEX CONCURRENTLY IF EXISTS uq_place_availability_product_slot;
DROP INDEX CONCURRENTLY IF EXISTS idx_reservation_product_created;

CREATE UNIQUE INDEX CONCURRENTLY uq_place_availability_legacy_slot
    ON place_availability (merchant_owner_user_id, place_id, starts_at, ends_at)
    WHERE product_id IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY uq_place_availability_product_slot
    ON place_availability (merchant_owner_user_id, place_id, product_id, starts_at, ends_at)
    WHERE product_id IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_reservation_product_created
    ON reservation (product_id, created_at DESC, id DESC)
    WHERE product_id IS NOT NULL;
