/* Arbitrary text field, optional. */
ALTER TABLE secrets ADD COLUMN description text;

/* Required text field, username of creator.
     Skipping not null because of existing records. */
ALTER TABLE secrets ADD COLUMN createdBy text;

/* Required update timestamp. Defaults to creation time. */
ALTER TABLE secrets ADD COLUMN updatedDate timestamp NOT NULL DEFAULT NOW();

/* Required text field, username of updater.
     Skipping not null because of existing records. */
ALTER TABLE secrets ADD COLUMN updatedBy text;

/*
Risk:
  This migration should not pose any risk. It is only additive.
  The only added constraint is a NOT NULL on a new column where a default IS specified.
*/

