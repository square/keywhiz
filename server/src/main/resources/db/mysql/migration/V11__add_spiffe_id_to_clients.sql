# https://github.com/spiffe/spiffe/blob/master/standards/SPIFFE-ID.md#23-maximum-spiffe-id-length
# maximum SPIFFE ID length is 2048 bytes using (per https://tools.ietf.org/html/rfc3986) US-ASCII
# chars, which in utf-8 encoding occupy one byte each
# Multiple clients may be associated with a given SPIFFE ID, so this is deliberately not UNIQUE.
ALTER TABLE clients ADD spiffe_id varchar(2048);
