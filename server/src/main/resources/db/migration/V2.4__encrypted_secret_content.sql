/* Adds a NULL column for encrypted content, to be filled in later. */
ALTER TABLE secrets_content ADD COLUMN encrypted_content TEXT;
