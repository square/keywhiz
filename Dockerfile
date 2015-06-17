# Dockerfile for square/keywhiz
#
# Note: keep this in the root of the project next to
# the pom.xml to work correctly
#
# Building:
#   docker build --rm --force-rm -t square/keywhiz .
#
# Example usage:
#   docker run square/keywhiz sh -c "java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar server server/src/main/resources/keywhiz-development.yaml"
#
FROM maven:3.3-jdk-8

# mkdir for app
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# add the source
ADD . /usr/src/app

# install
RUN mvn install -P h2
