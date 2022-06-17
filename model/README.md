This sub-project contains the database model which comprises Jooq-generated Java classes based off of the current database schema.

In order to facilitate containerized builds, the logic in this sub-project has been updated such that regeneration of the code is done 
manually and the results checked in vs. having it run automatically at compile time. As a result, the `regenerate.sh` script should be run 
whenever Jooq is updated or whenever the database schema changes.
