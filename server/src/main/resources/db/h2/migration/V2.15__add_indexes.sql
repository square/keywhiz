CREATE UNIQUE INDEX accessgrants_groupid_secretid_idx ON accessgrants (groupid, secretid);
CREATE UNIQUE INDEX memberships_clientid_groupid_idx ON memberships (clientid, groupid);
