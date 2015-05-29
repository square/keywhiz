/* Drop column certificate. It's never been used and has been removed from DAOs for some time. */
ALTER TABLE clients DROP COLUMN IF EXISTS certificate;
