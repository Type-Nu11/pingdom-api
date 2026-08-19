ALTER TABLE admin_ad ADD COLUMN updated_at TIMESTAMP(6);
UPDATE admin_ad SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE admin_ad ALTER COLUMN updated_at SET NOT NULL;
