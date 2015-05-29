/* Add Metadata column to secret contents, to be filled later */
ALTER TABLE secrets_content ADD COLUMN metadata text NOT NULL DEFAULT '';