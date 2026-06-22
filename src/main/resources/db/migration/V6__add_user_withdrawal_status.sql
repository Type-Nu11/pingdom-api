ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE users
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE users
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMP(6);

CREATE INDEX IF NOT EXISTS idx_users_status_withdrawn_at
    ON users (status, withdrawn_at);
