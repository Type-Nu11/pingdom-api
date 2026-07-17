ALTER TABLE merchant_place_claim
    VALIDATE CONSTRAINT fk_merchant_place_claim_previous_owner;

ALTER TABLE merchant_place_claim
    VALIDATE CONSTRAINT ck_merchant_place_claim_type;

ALTER TABLE merchant_place_claim
    VALIDATE CONSTRAINT ck_merchant_place_claim_transfer_owner;
