ALTER TABLE clients ADD owner BIGINT;
ALTER TABLE `groups` ADD owner BIGINT;

CREATE INDEX owner_idx on clients (owner);
CREATE INDEX owner_idx on `groups` (owner);
