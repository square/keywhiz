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
FROM maven:3.6-jdk-11

RUN export http_proxy=http://child-prc.intel.com:913 && \
    export https_proxy=http://child-prc.intel.com:913 && apt-get update && \
    apt-get install -y --no-install-recommends --no-upgrade \
    gettext vim-common default-mysql-server && \
    #echo [mysqld] >> /etc/mysql/conf.d/mysql.cnf && \
    echo validate_password.policy=LOW >> /etc/mysql/conf.d/mysql.cnf && \
    mkdir -p /usr/src/app && unset http_proxy && unset https_proxy
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
COPY log/pom.xml /usr/src/app/log/
RUN rm /usr/share/maven/conf/settings.xml
COPY s3.xml /usr/share/maven/conf/settings.xml
RUN export http_proxy=http://child-prc.intel.com:913 && \
export https_proxy=http://child-prc.intel.com:913 && \
mvn dependency:copy-dependencies --fail-never

# copy source required for build and install
COPY api /usr/src/app/api/
COPY cli /usr/src/app/cli/
COPY client /usr/src/app/client/
COPY hkdf /usr/src/app/hkdf/
COPY model /usr/src/app/model/
COPY server /usr/src/app/server/
COPY testing /usr/src/app/testing/
COPY log /usr/src/app/log/
RUN service mysql start && mvn -DskipTests=true package

# Drop privs inside container
RUN useradd -ms /bin/false keywhiz && \
    mkdir /data && \
    chown keywhiz:keywhiz /data && \
    mkdir /secrets && \
    chown keywhiz:keywhiz /secrets && \
    echo 'alias keywhiz.cli="/usr/src/app/cli/target/keywhiz-cli-*-SNAPSHOT-shaded.jar --devTrustStore"' >> ~/.bashrc && \
    echo 'alias key.provider="java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar"' >> ~/.bashrc && \
    echo salt > /usr/src/app/salt

#USER keywhiz

# Expose API port by default. Note that the admin console port
# is NOT exposed by default, can be exposed manually if desired.
EXPOSE 4444

VOLUME ["/data", "/secrets"]

COPY docker/entry.sh /usr/src/app
COPY docker/wizard.sh /usr/src/app
COPY docker/keywhiz-config.tpl /usr/src/app
COPY frontend-keywhiz-conf.yaml /usr/src/app
RUN chmod a+x /usr/src/app/entry.sh

ENTRYPOINT ["/usr/src/app/entry.sh"]
