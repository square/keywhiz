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
    mvn install -P h2

    # Build keywhiz for MySQL
    mvn install -P mysql

Run Keywhiz:

    java -jar server/target/keywhiz-server-*-shaded.jar [COMMAND] [OPTIONS] 

Useful commands to get started are `migrate`, `db-seed` and `server`. Use with
`--help` for a list of all available commands. Use with `[COMMAND] --help` to
get help on a particular command.

For example, to run Keywhiz with an H2 database in development mode:

    export SERVER_JAR=server/target/keywhiz-server-*-shaded.jar
    export KEYWHIZ_CONFIG=server/target/classes/keywhiz-development.yaml.h2

    # Initialize dev database (H2)
    java -jar $SERVER_JAR migrate $KEYWHIZ_CONFIG

    # Seed database with development data
    java -jar $SERVER_JAR db-seed $KEYWHIZ_CONFIG

    # Run server
    java -jar $SERVER_JAR server $KEYWHIZ_CONFIG

Keywhiz uses [jOOQ](http://www.jooq.org/) to talk to its database.

If you made changes to the database model and want to regenerate sources:

    mvn install -pl model/ -Pgenerate-jooq-sources

We recommend [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development. 

## Docker

We ship a [Dockerfile](Dockerfile) for building a Docker container for keywhiz.
Please see the Dockerfile for extra instructions.

## License

Keywhiz is under the Apache 2.0 license. See the [LICENSE](LICENSE) file for details.

[1]: https://square.github.io/keywhiz
[2]: https://corner.squareup.com/2015/04/keywhiz.html
