/* Boolean field to identify if a client is for automationAllowed purposes. */
ALTER TABLE clients ADD COLUMN automationAllowed boolean default false NOT NULL;

/*
Risk:
  This migration should not pose any risk. It is only additive.
*/
