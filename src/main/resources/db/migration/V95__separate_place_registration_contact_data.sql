ALTER TABLE place_registration_application
    ADD COLUMN business_contact_phone VARCHAR(20),
    ADD COLUMN encrypted_applicant_contact_phone VARCHAR(512);

ALTER TABLE place_registration_application
    ADD CONSTRAINT ck_place_registration_business_contact_phone
    CHECK (business_contact_phone IS NULL OR business_contact_phone ~ '^\\+[1-9][0-9]{7,14}$');
