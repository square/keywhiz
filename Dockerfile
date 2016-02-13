# Dockerfile for square/keywhiz
#
# Note: keep this in the root of the project next to
# the pom.xml to work correctly.
#
# Building:
#   docker build --rm --force-rm -t square/keywhiz .
#
# Basic usage:
#   docker run -e KEYWHIZ_CONFIG=/path/to/config [COMMAND]
#
# If the KEYWHIZ_CONFIG environment variable is omitted, keywhiz
# will run with the default development config. If COMMAND is 
# omitted, keywhiz will print a help message.
#
# Development:
#   Create a persistent data volume:
#     docker volume create keywhiz-db-devel
#
#   Initialize the database and apply migrations:
#     docker run -v keywhiz-db-devel:/tmp/h2_data square/keywhiz migrate
#
#   Finally, run the server with the default development config:
#     docker run -it -v keywhiz-db-devel:/tmp/h2_data square/keywhiz server
#
# Note that for a production deployment, you'll probably want to setup
# your own config that talks to a MySQL database instead of using H2.
#
FROM maven:3.3-jdk-8

# mkdir for app
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# caching trick to speed up build; see:
# https://keyholesoftware.com/2015/01/05/caching-for-maven-docker-builds
# this should allow non-dynamic dependencies to be cached
COPY *.xml /usr/src/app/
COPY api/pom.xml /usr/src/app/api/
COPY cli/pom.xml /usr/src/app/cli/
COPY client/pom.xml /usr/src/app/client/
COPY hkdf/pom.xml /usr/src/app/hkdf/
COPY model/pom.xml /usr/src/app/model/
COPY server/pom.xml /usr/src/app/server/
COPY testing/pom.xml /usr/src/app/testing/
RUN mvn verify clean --fail-never

# copy source required for build and install
COPY api /usr/src/app/api/
COPY cli /usr/src/app/cli/
COPY client /usr/src/app/client/
COPY hkdf /usr/src/app/hkdf/
COPY model /usr/src/app/model/
COPY server /usr/src/app/server/
COPY testing /usr/src/app/testing/
RUN mvn install -P h2

COPY docker.sh /usr/src/app
ENTRYPOINT ["/usr/src/app/docker.sh"]
