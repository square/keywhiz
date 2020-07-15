DROP INDEX name_idx ON secrets;
CREATE INDEX name ON secrets (name);
CREATE UNIQUE INDEX name_id_idx ON secrets (id, name);