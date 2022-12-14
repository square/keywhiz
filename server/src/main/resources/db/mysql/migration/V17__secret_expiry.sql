ALTER TABLE secrets ADD expiry BIGINT NULL;

CREATE INDEX expiry_idx ON secrets (expiry);
