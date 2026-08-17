ALTER TABLE place_registration_application
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    ADD COLUMN operating_schedule_json TEXT NOT NULL DEFAULT '[]';
