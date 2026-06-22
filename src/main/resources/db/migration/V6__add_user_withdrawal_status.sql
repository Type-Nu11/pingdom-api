ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMP(6);

CREATE INDEX IF NOT EXISTS idx_users_status_withdrawn_at
    ON users (status, withdrawn_at);

ALTER TABLE map_place
    ALTER COLUMN user_id DROP NOT NULL;
