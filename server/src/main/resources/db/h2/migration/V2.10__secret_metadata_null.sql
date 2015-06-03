/*
 * Allow secret metadata column to be NULL for new records, until the column is dropped completely.
 */
ALTER TABLE secrets ALTER COLUMN metadata DROP NOT NULL;