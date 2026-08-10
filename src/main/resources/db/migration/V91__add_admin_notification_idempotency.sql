ALTER TABLE notifications
    ADD COLUMN event_key VARCHAR(200);

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_user_event_key UNIQUE (user_id, event_key);

CREATE INDEX idx_notifications_user_read_created
    ON notifications (user_id, is_read, created_at DESC, id DESC);
