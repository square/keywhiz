ALTER TABLE secrets_content ADD COLUMN expiry bigint NOT NULL DEFAULT 0;
CREATE INDEX secrets_content_expiry ON secrets_content (expiry);
