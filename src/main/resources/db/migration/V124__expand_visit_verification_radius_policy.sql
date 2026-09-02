ALTER TABLE visit_verification_session
    DROP CONSTRAINT ck_visit_verification_session_radius;

ALTER TABLE visit_verification_session
    ADD CONSTRAINT ck_visit_verification_session_radius
        CHECK (required_radius_meters > 0);
