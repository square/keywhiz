DROP INDEX secrets_content_secretid_version_idx;
ALTER TABLE secrets_content DROP COLUMN version;
