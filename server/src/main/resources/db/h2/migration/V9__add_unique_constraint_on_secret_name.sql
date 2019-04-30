ALTER TABLE secrets DROP INDEX secrets_name_idx;
CREATE UNIQUE INDEX secrets_name_idx ON secrets (name);
-- while not incorrect, this index is redundant with UNIQUE indices on both id and name
ALTER TABLE secrets DROP INDEX name_id_idx;