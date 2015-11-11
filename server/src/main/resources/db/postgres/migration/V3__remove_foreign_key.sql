ALTER TABLE ONLY accessgrants
    DROP CONSTRAINT accessgrants_groupid_fkey;
ALTER TABLE ONLY accessgrants
    DROP CONSTRAINT accessgrants_secretid_fkey;
ALTER TABLE ONLY memberships
    DROP CONSTRAINT memberships_clientid_fkey;
ALTER TABLE ONLY memberships
    DROP CONSTRAINT memberships_groupid_fkey;
ALTER TABLE ONLY secrets_content
    DROP CONSTRAINT secrets_content_secretid_fkey;
