/* Drop column updatedDate. It's not used and duplicates updatedAt. */
ALTER TABLE secrets DROP COLUMN updatedDate;

/*
Risk:
  This migration has low risk. It drops an unused column.
*/
