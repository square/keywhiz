/* Removing owner, mode in favor of generic metadata mapping. */
ALTER TABLE secrets DROP COLUMN IF EXISTS owner;

ALTER TABLE secrets DROP COLUMN IF EXISTS mode;

ALTER TABLE secrets ADD COLUMN metadata text NOT NULL;