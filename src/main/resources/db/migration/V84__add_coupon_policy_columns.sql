ALTER TABLE tourist_offer
    ADD COLUMN eligibility_policy VARCHAR(30) NOT NULL DEFAULT 'ACTIVE_TRAVEL_SCHEDULE',
    ADD COLUMN inventory_policy VARCHAR(20) NOT NULL DEFAULT 'LIMITED',
    ADD COLUMN expiry_policy VARCHAR(50) NOT NULL DEFAULT 'ISSUE_PLUS_DAYS_CAPPED_BY_OFFER_END';

ALTER TABLE tourist_offer
    ALTER COLUMN total_quantity DROP NOT NULL;

ALTER TABLE tourist_offer
    DROP CONSTRAINT ck_tourist_offer_quantity;

ALTER TABLE tourist_offer
    ADD CONSTRAINT ck_tourist_offer_quantity
    CHECK (
        issued_quantity >= 0
        AND (
            (inventory_policy = 'UNLIMITED' AND total_quantity IS NULL)
            OR (inventory_policy = 'LIMITED' AND total_quantity > 0 AND issued_quantity <= total_quantity)
        )
    ),
    ADD CONSTRAINT ck_tourist_offer_eligibility_policy
    CHECK (eligibility_policy IN ('ACTIVE_TRAVEL_SCHEDULE', 'PUBLIC')),
    ADD CONSTRAINT ck_tourist_offer_inventory_policy
    CHECK (inventory_policy IN ('LIMITED', 'UNLIMITED')),
    ADD CONSTRAINT ck_tourist_offer_expiry_policy
    CHECK (expiry_policy IN (
        'ISSUE_PLUS_DAYS_CAPPED_BY_OFFER_END', 'ISSUE_PLUS_DAYS', 'OFFER_END'
    ));
