-- 원문과 기존 상품 참조는 보존한다. 구버전 서버와 동시 쓰기 없이 예약 트래픽을 중지한 상태에서 적용한다.
ALTER TABLE place_availability
    ADD COLUMN booking_retired BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN legacy_reservation_terms JSONB;

UPDATE place_availability
SET legacy_reservation_terms = reservation_terms,
    booking_retired = product_id IS NOT NULL OR product_type <> 'GENERAL'
        OR COALESCE((reservation_terms->>'unitAmountMinor')::BIGINT, 0) > 0
        OR COALESCE((reservation_terms->>'additionalAmountMinor')::BIGINT, 0) > 0,
    reservation_terms = reservation_terms - ARRAY['unitAmountMinor', 'additionalAmountMinor', 'currency'];

-- 종료된 슬롯의 원래 재고/상태는 유지한다. 동일 시간의 새 슬롯은 별도로 등록할 수 있다.
DROP INDEX uq_place_availability_legacy_slot;
CREATE UNIQUE INDEX uq_place_availability_legacy_slot
    ON place_availability (merchant_owner_user_id, place_id, starts_at, ends_at)
    WHERE product_id IS NULL AND booking_retired = FALSE;

ALTER TABLE reservation ADD COLUMN legacy_confirmation JSONB;
ALTER TABLE reservation_quote
    ADD COLUMN legacy_confirmation JSONB,
    ADD COLUMN contract_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE reservation_quote ALTER COLUMN contract_version SET DEFAULT 2;

UPDATE reservation
SET legacy_confirmation = confirmation,
    confirmation = confirmation - ARRAY[
        'productType', 'productId', 'productName', 'productVersion',
        'unitAmountMinor', 'additionalAmountMinor', 'totalAmountMinor', 'currency',
        'currencyFractionDigits', 'paymentRequired', 'cancellationFeeMinor', 'refundableAmountMinor'
    ];

UPDATE reservation_quote
SET legacy_confirmation = confirmation,
    confirmation = confirmation - ARRAY[
        'productType', 'productId', 'productName', 'productVersion',
        'unitAmountMinor', 'additionalAmountMinor', 'totalAmountMinor', 'currency',
        'currencyFractionDigits', 'paymentRequired', 'cancellationFeeMinor', 'refundableAmountMinor'
    ],
    rejection_code = CASE WHEN rejection_code = 'RESERVATION_PRODUCT_UNAVAILABLE'
        THEN 'QUOTE_CONDITIONS_CHANGED' ELSE rejection_code END;

-- reservable_product 및 product_id/product_type 컬럼과 외래 키는 과거 이력 보존용으로 남긴다.
-- 새 서버는 상품 컬럼을 읽거나 쓰지 않으며 신규 행은 기존 DB 기본값을 사용한다.
