/* Arbitrary text field, optional. */
ALTER TABLE groups ADD COLUMN description text;

/* Required text field, username of creator.
     Skipping not null because of existing records. */
ALTER TABLE groups ADD COLUMN createdBy text;

/* Required text field, username of updater.
     Skipping not null because of existing records. */
ALTER TABLE groups ADD COLUMN updatedBy text;

/*
Risk:
  This migration should not pose any risk. It is only additive.
*/
