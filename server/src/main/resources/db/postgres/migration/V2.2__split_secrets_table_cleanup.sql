DROP INDEX IF EXISTS secrets_name_version_idx;
ALTER TABLE secrets DROP COLUMN IF EXISTS secret;
ALTER TABLE secrets DROP COLUMN IF EXISTS version;

CREATE UNIQUE INDEX secrets_name_idx ON secrets (name);
