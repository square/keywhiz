v0.7.9: 2015-11-10

  * Various fixes related to MySQL.

    Note: We modified a flyway migration file. The checksum was changed
          from 330484744 to -1043629835. Ping us in the unlikely event that you
          are affected by this change and need help to resolve things.

  * Fixed a bug in the cli.

  * Foreign keys were removed and cascading deletes are handled by Keywhiz
    instead of the database engine. This is more flexible.

  * Support for bigint, you can now enjoy 9,223,372,036,854,775,807 secrets!

  * Replaced OffsetDateTime with long (epoch in seconds). We no longer track
    data creation/mutation at the fractional seconds granularity.

v0.7.8: 2015-10-27

  * New automation API (/automation/v2/). Reduces amount of data fetched from
    database and returned in the response based on experience using the older
    API. Uses seconds since epoch to represent createdAt and updatedAt
    timestamps, which should be more cross-language friendly.

  * Ensures consistent date serialization in old API.

  * Replaces postgres JDBC driver with pgjdbc-ng
    (https://github.com/impossibl/pgjdbc-ng).

  * Fixes a privacy bug which would allow unauthorized access to the list of
    clients and secrets associated with a given group. The contents of the
    secrets were never exposed.

v0.7.6: 2015-06-22

  * Updates bcrypt version to fix potential integer overflow issue,
    see CVE-2015-0886.

  * Improves SQL queries by not fetching data which is subsequently not used.

  * `#116`: Early precondition checks in KeywhizClient. Moves base64'ing
    of request from CLI -> KeywhizClient.

  * Switches AES-GCM's IV to be 12 bytes instead of 16. Potentially helps with
    interoperability with other libraries.

v0.7.5: 2015-06-08

  * Fixes a bug which would cause the server to throw an exception in some
    cases instead of returning a secret.

v0.7.4: 2015-06-08

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
    - Fixed bug where assign would mistakenly work multiple times and
      unassign would mistakenly fail thinking no assignment was present.

  * Updated dependencies
    - guice updated to 4.0
    - jooq updated to 3.6.2
    - logback updated to 1.1.3
    - jcommander updated to 1.48
    - jackson updated to 2.5.3
    - auto-value updated to 1.1
    - okhttp updated to 2.4.0
    - dropwizard-java8 modules updated to 0.8.0-2
    - commons-lang3 updated to 3.4
    - assertj-guava updated to 1.3.1
    - easymock updated to 3.3.1
    - flyway updated to 3.2.1
    - powermock updated to 1.6.2
    - mockito updated to 1.10.19
    - slf4j updated to 1.7.12
    - h2 updated to 1.4.187
    - mysql added

v0.7.3: 2015-04-08

  * First open source release
