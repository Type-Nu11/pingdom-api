ALTER TABLE privacy_processing_history
    ADD COLUMN outbox_event_id VARCHAR(36);

ALTER TABLE privacy_processing_history
    ADD CONSTRAINT uk_privacy_processing_history_outbox_subject
        UNIQUE (outbox_event_id, subject_user_id);
