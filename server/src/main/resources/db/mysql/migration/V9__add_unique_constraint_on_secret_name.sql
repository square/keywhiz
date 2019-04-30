DROP INDEX name ON secrets;
CREATE UNIQUE INDEX name ON secrets (name);
-- while not incorrect, this index is redundant with UNIQUE indices on both id and name
DROP INDEX name_id_idx ON secrets;