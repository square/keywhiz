CREATE INDEX secretid_idx ON secrets_content (secretid);
DROP INDEX secrets_content_secretid_version_idx;
ALTER TABLE secrets_content DROP COLUMN version;
