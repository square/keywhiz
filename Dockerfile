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
# *** Development ***
#   Create a persistent data volume:
#     docker volume create --name keywhiz-db-devel
#
#   Initialize the database, apply migrations, and add seed data:
#     docker run -v keywhiz-db-devel:/data square/keywhiz migrate
#     docker run -v keywhiz-db-devel:/data square/keywhiz db-seed
#
#   Finally, run the server with the default development config:
#     docker run -it -p 4444:4444 -v keywhiz-db-devel:/data square/keywhiz server
#
# *** Production setup wizard ***
#   For production deployments, we have setup wizard that will initialize
#   a Keywhiz container for you and create a config based on a template.
#
#   The wizard can be run using the "wizard" command, like so:
#       docker run -it \
#           -v keywhiz-data:/data \
#           -v keywhiz-secrets:/secrets \
#           square/keywhiz wizard
#
#   Be ready to provide a server certificate/private key for setup.
#
# After keywhiz starts up, you can access the admin console by going
# to https://[DOCKER-MACHINE-IP]/ui in your browser. The default user
# in development is keywhizAdmin and the default password is adminPass.
#
# Note that for a production deployment, you'll probably want to setup
# your own config to make sure you're not using development secrets. 
#
FROM maven:3.3-jdk-8

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install gettext vim-common && \
    mkdir -p /usr/src/app
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
RUN mvn dependency:copy-dependencies -P docker --fail-never

# copy source required for build and install
COPY api /usr/src/app/api/
COPY cli /usr/src/app/cli/
COPY client /usr/src/app/client/
COPY hkdf /usr/src/app/hkdf/
COPY model /usr/src/app/model/
COPY server /usr/src/app/server/
COPY testing /usr/src/app/testing/
RUN mvn install -P docker

# Drop privs inside container
RUN useradd -ms /bin/false keywhiz && \
    mkdir /data && \
    chown keywhiz:keywhiz /data && \
    mkdir /secrets && \
    chown keywhiz:keywhiz /secrets
USER keywhiz

# Expose API port by default. Note that the admin console port
# is NOT exposed by default, can be exposed manually if desired. 
EXPOSE 4444

VOLUME ["/data", "/secrets"]

COPY docker/entry.sh /usr/src/app
COPY docker/wizard.sh /usr/src/app
COPY docker/keywhiz-config.tpl /usr/src/app
ENTRYPOINT ["/usr/src/app/entry.sh"]
