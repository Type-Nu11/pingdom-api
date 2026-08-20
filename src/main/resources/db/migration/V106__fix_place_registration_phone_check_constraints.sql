ALTER TABLE place_registration_application
    DROP CONSTRAINT ck_place_registration_business_contact_phone,
    DROP CONSTRAINT ck_place_registration_merchant_contact_phone;

ALTER TABLE place_registration_application
    ADD CONSTRAINT ck_place_registration_business_contact_phone
        CHECK (business_contact_phone IS NULL OR business_contact_phone ~ '^[+][1-9][0-9]{7,14}$'),
    ADD CONSTRAINT ck_place_registration_merchant_contact_phone
        CHECK (merchant_contact_phone IS NULL OR merchant_contact_phone ~ '^[+][1-9][0-9]{7,14}$');
