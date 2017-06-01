# Keywhiz

[![license](https://img.shields.io/badge/license-apache_2.0-red.svg?style=flat)](https://raw.githubusercontent.com/square/keywhiz/master/LICENSE)
[![maven](https://img.shields.io/maven-central/v/com.squareup.keywhiz/keywhiz-server.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.squareup.keywhiz%22)
[![build](https://img.shields.io/travis/square/keywhiz/master.svg?style=flat)](https://travis-ci.org/square/keywhiz)

Keywhiz is a system for distributing and managing secrets.
For more information, see the [website][1].

Our [Protecting infrastructure secrets with Keywhiz][2] blog post is worth
reading, as it provides some useful context.

## Develop

See [CONTRIBUTING](CONTRIBUTING.md) for details on submitting patches.

Build keywhiz: 

    # Build keywhiz for H2
    ./mvnw install -P h2

    # Build keywhiz for MySQL
    ./mvnw install -P mysql

Run Keywhiz:

    java -jar server/target/keywhiz-server-*-shaded.jar [COMMAND] [OPTIONS] 

Useful commands to get started are `migrate`, `add-user` and `server`. Use with
`--help` for a list of all available commands. Use with `[COMMAND] --help` to
get help on a particular command.

For example, to run Keywhiz with an H2 database in development mode:

    SERVER_JAR="server/target/keywhiz-server-*-shaded.jar"
    KEYWHIZ_CONFIG="server/target/classes/keywhiz-development.yaml.h2"

    # Initialize dev database (H2)
    java -jar $SERVER_JAR migrate $KEYWHIZ_CONFIG

    # Add an administrative user
    java -jar $SERVER_JAR add-user $KEYWHIZ_CONFIG

    # Run server
    java -jar $SERVER_JAR server $KEYWHIZ_CONFIG

To connect to a running Keywhiz instance, you will need to use the CLI.

An example helper shell script that wraps the keywhiz-cli and sets some default parameters:

    #!/bin/sh

    # Set the path to a compiled, shaded keywhiz-cli JAR file
    KEYWHIZ_CLI_JAR="/path/to/keywhiz-cli-shaded.jar"
    KEYWHIZ_SERVER_URL="https://$(hostname):4444"

    # Use these flags if you want to specify a non-standard CA trust store
    TRUSTSTORE="-Djavax.net.ssl.trustStore=/path/to/ca-bundle.jceks"
    TRUSTTYPE="-Djavax.net.ssl.trustStoreType=JCEKS"

    java "$TRUSTSTORE" "$TRUSTTYPE" -jar "$KEYWHIZ_CLI_JAR" -U "$KEYWHIZ_SERVER_URL" "$@"

Keywhiz uses [jOOQ](http://www.jooq.org/) to talk to its database.

If you made changes to the database model and want to regenerate sources:

    ./mvnw install -pl model/ -Pgenerate-jooq-sources

We recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development. 

## Docker

We ship a [Dockerfile](Dockerfile) for building a Docker container for keywhiz.
Please see the Dockerfile for extra instructions.

## License

Keywhiz is under the Apache 2.0 license. See the [LICENSE](LICENSE) file for details.

[1]: https://square.github.io/keywhiz
[2]: https://corner.squareup.com/2015/04/keywhiz.html
