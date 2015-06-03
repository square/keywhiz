v0.7.4:

  * Added support for H2 and MySQL. H2 should make development easier since it's
    an embedded database. This was achieved by switching the SQL layer from
    JDBI to jOOQ.

  * Lots of improvements to the cli tool:
    - Fixed a bug which could cause the tool to be unusable for some users due
      to the way the session data was being stored and read from the file.
    - Changed the default url to `https://localhost:4444/` (users can override
      with `--url`). Generated new default certificates (in PKCS12 format) which
      contain localhost.
    - Embedded the development trust store (`--devTrustStore`) making it easier
      to get started without having to worry about file paths or having to
      generate certs.
    - Added the ability to specify the username on the command line (`--user`).
    - Removed a bunch of old & unused openssl files.

v0.7.3:

  * First open source release
