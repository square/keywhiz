CREATE INDEX secretid_idx ON secrets_content (secretid);
ALTER TABLE secrets_content DROP INDEX secretid;
ALTER TABLE secrets_content DROP COLUMN version;
