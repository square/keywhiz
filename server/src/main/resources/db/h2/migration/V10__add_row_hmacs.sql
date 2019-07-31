ALTER TABLE accessGrants ADD row_hmac varchar(64) not null default '';
ALTER TABLE memberships ADD row_hmac varchar(64) not null default '';
ALTER TABLE clients ADD row_hmac varchar(64) not null default '';
ALTER TABLE secrets ADD row_hmac varchar(64) not null default '';
ALTER TABLE secrets_content ADD row_hmac varchar(64) not null default '';