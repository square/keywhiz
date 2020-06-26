# Keywhiz does not currently support an entity acting on behalf of other clients without being
# a full-fledged automation client. This would be the primary use case for sharing SPIFFE IDs
# between clients. Since this would be a significant change to Keywhiz' security model,
# require SPIFFE IDs to be unique for now.
# SPIFFE IDs, like all URIs, are ASCII because RFC 3986 permits only ASCII characters.
# Using the ASCII character set allows indexing on this value (utf8-mb4 is too large).
ALTER TABLE clients MODIFY spiffe_id varchar(2048) character set ascii default null;
CREATE UNIQUE INDEX spiffe_id_unique on clients (`spiffe_id`);