# Dockerfile for square/keywhiz
#
# Note: keep this in the root of the project next to
# the pom.xml to work correctly.
#
# Building:
#   docker build --rm --force-rm -t square/keywhiz .
#
# Basic usage:
#   docker run -e KEYWHIZ_CONFIG=/path/to/config square/keywhiz [COMMAND]
#
# If the KEYWHIZ_CONFIG environment variable is omitted, keywhiz
# will run with the default development config. If COMMAND is
# omitted, keywhiz will print a help message.
#
# *** Development ***
#   Create a persistent data volume:
#     docker volume create --name keywhiz-db-devel
#
#   Initialize the database, apply migrations, and add administrative user:
#     docker run --rm -v keywhiz-db-devel:/data square/keywhiz migrate
#     docker run --rm -it -v keywhiz-db-devel:/data square/keywhiz add-user
#
#   Finally, run the server with the default development config:
#     docker run --rm -it -p 4444:4444 -v keywhiz-db-devel:/data square/keywhiz server
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
# After keywhiz starts up, you can access it by running keywhiz-cli and
# using the --url option to point it at the server you set up. The default user
# in development is 'keywhizAdmin' and the default password is 'adminPass'.
#
# Note that for a production deployment, you'll probably want to setup
# your own config to make sure you're not using development secrets.
#
FROM alpine:3.12 AS permissions-giver
WORKDIR /out
COPY docker/*.sh ./

# Make sure these files are executable, regardless of the build host
RUN chmod +x *.sh

FROM alpine:3.12 AS organizer
WORKDIR /out/before-dep
COPY *.xml ./
COPY api/pom.xml api/
COPY cli/pom.xml cli/
COPY client/pom.xml client/
COPY hkdf/pom.xml hkdf/
COPY model/pom.xml model/
COPY server/pom.xml server/
COPY testing/pom.xml testing/
COPY log/pom.xml log/

WORKDIR /out/after-dep-before-install
COPY api api/
COPY cli cli/
COPY client client/
COPY hkdf hkdf/
COPY model model/
COPY server server/
COPY testing testing/
COPY log log/

WORKDIR /out/after-install
COPY --from=permissions-giver /out .
COPY docker/keywhiz-config.tpl .

FROM maven:3.6-jdk-11 AS runner

# Drop privs inside container
RUN apt-get update && \
  apt-get install -y --no-install-recommends --no-upgrade \
  gettext vim-common && \
  useradd -ms /bin/false keywhiz && \
  mkdir /data && \
  chown keywhiz:keywhiz /data && \
  mkdir /secrets && \
  chown keywhiz:keywhiz /secrets
WORKDIR /usr/src/app

# caching trick to speed up build; see:
# https://keyholesoftware.com/2015/01/05/caching-for-maven-docker-builds
# this should allow non-dynamic dependencies to be cached
COPY --from=organizer /out/before-dep .
RUN mvn dependency:copy-dependencies --fail-never

# copy source required for build and install
COPY --from=organizer /out/after-dep-before-install .
RUN mvn install -DskipTests=true -Dmaven.javadoc.skip=true -B -V -q

USER keywhiz

# Expose API port by default. Note that the admin console port
# is NOT exposed by default, can be exposed manually if desired.
EXPOSE 4444

VOLUME ["/data", "/secrets"]

COPY --from=organizer /out/after-install .

ENTRYPOINT ["/usr/src/app/entry.sh"]
