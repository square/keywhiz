/* Arbitrary text field, optional. */
ALTER TABLE clients ADD COLUMN description text;

/* Required text field, username of creator.
     Skipping not null because of existing records. */
ALTER TABLE clients ADD COLUMN createdBy text;

/* Required text field, username of updater.
     Skipping not null because of existing records. */
ALTER TABLE clients ADD COLUMN updatedBy text;

ALTER TABLE clients ADD COLUMN enabled boolean NOT NULL DEFAULT FALSE;

/* Required text field, base64 certificate of the client.
     Skipping not null because of existing records.
     Need to import certs later.
*/
ALTER TABLE clients ADD COLUMN certificate text;

/*
Risk:
  This migration should not pose any risk. It is only additive.
  The only added constraint is a NOT NULL on a new column where a default IS specified.
*/
