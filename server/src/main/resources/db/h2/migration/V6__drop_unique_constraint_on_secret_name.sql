ALTER TABLE secrets DROP INDEX secrets_name_idx;  /* from V2.2 */
CREATE INDEX secrets_name_idx ON secrets (name);
CREATE UNIQUE INDEX name_id_idx ON secrets (id, name);