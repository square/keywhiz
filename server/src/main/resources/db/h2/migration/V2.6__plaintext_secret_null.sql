/*
 * Allow plaintext secret column to be NULL for new records, until the column is dropped completely.
 */
ALTER TABLE secrets_content ALTER COLUMN content DROP NOT NULL;
