ALTER TABLE visitor_verification_report
    ADD COLUMN wait_time_minutes INTEGER,
    ADD COLUMN language_code VARCHAR(10),
    ADD COLUMN coupon_usage_status VARCHAR(20),
    ADD COLUMN crowd_level VARCHAR(20);

ALTER TABLE visitor_verification_report
    DROP CONSTRAINT ck_visitor_verification_report_type,
    ADD CONSTRAINT ck_visitor_verification_report_type CHECK (
        report_type IN (
            'PLACE_INFORMATION', 'OPERATING_HOURS', 'LOCATION', 'CLOSED_PLACE',
            'WAIT_TIME', 'LANGUAGE_SUPPORT', 'COUPON_USAGE', 'CROWD_LEVEL', 'OTHER'
        )
    ) NOT VALID,
    ADD CONSTRAINT ck_visitor_verification_report_wait_time CHECK (
        wait_time_minutes IS NULL OR wait_time_minutes BETWEEN 0 AND 1440
    ) NOT VALID,
    ADD CONSTRAINT ck_visitor_verification_report_language_code CHECK (
        language_code IS NULL OR language_code ~ '^[a-z]{2,3}(-[A-Z]{2})?$'
    ) NOT VALID,
    ADD CONSTRAINT ck_visitor_verification_report_coupon_usage CHECK (
        coupon_usage_status IS NULL OR coupon_usage_status IN ('AVAILABLE', 'UNAVAILABLE', 'UNKNOWN')
    ) NOT VALID,
    ADD CONSTRAINT ck_visitor_verification_report_crowd_level CHECK (
        crowd_level IS NULL OR crowd_level IN ('LOW', 'MODERATE', 'HIGH', 'FULL')
    ) NOT VALID,
    ADD CONSTRAINT ck_visitor_verification_report_structured_value CHECK (
        (report_type = 'WAIT_TIME'
            AND wait_time_minutes IS NOT NULL
            AND language_code IS NULL AND coupon_usage_status IS NULL AND crowd_level IS NULL)
        OR (report_type = 'LANGUAGE_SUPPORT'
            AND wait_time_minutes IS NULL
            AND language_code IS NOT NULL AND coupon_usage_status IS NULL AND crowd_level IS NULL)
        OR (report_type = 'COUPON_USAGE'
            AND wait_time_minutes IS NULL
            AND language_code IS NULL AND coupon_usage_status IS NOT NULL AND crowd_level IS NULL)
        OR (report_type = 'CROWD_LEVEL'
            AND wait_time_minutes IS NULL
            AND language_code IS NULL AND coupon_usage_status IS NULL AND crowd_level IS NOT NULL)
        OR (report_type NOT IN ('WAIT_TIME', 'LANGUAGE_SUPPORT', 'COUPON_USAGE', 'CROWD_LEVEL')
            AND wait_time_minutes IS NULL
            AND language_code IS NULL AND coupon_usage_status IS NULL AND crowd_level IS NULL)
    ) NOT VALID;

ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_type;
ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_wait_time;
ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_language_code;
ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_coupon_usage;
ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_crowd_level;
ALTER TABLE visitor_verification_report
    VALIDATE CONSTRAINT ck_visitor_verification_report_structured_value;
