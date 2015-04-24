/* Backfills have occurred and this column must be present to rely on now. */
ALTER TABLE secrets_content ALTER COLUMN encrypted_content SET NOT NULL;
