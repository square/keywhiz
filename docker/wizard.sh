#!/bin/bash

set -e

function confirm() {
    echo
    read -p "$1 (y/n) "
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        return 0
    fi
    if [[ $REPLY =~ ^[Nn]$ ]]; then
        return 1
    fi
    echo "Answer must be one of y/n."
    confirm "$1"
}

function assert_file_present() {
    if ! [ -f $1 ]; then
        echo "Missing file: $1. Aborting."
        exit 1
    fi 
}

cat - <<EOF
*** Keywhiz Setup Wizard ***

The Keywhiz Setup Wizard will take you through a step-by-step process to
bootstrap a Keywhiz server container. This includes generating a new content
encryption key, adding an initial admin user, as well as adding certificates
for the Keywhiz server to run. All generated keys and secrets will be stored in
the /secrets directory inside the container, and an empty H2 database will be
created in /data. You should make sure you have persistent volumes mounted in
those places!

Note that if you wish to run Keywhiz with an external database you can do so
by supplying your own config file via the KEYWHIZ_CONFIG environment variable.
The wizard will set up an H2 database by default, which should be sufficent
for most deployments (unless you expect a very high volume of traffic).
EOF

if ! confirm "The wizard will overwrite previously generated data if present. Proceed?"; then
    exit 1
fi

echo
echo -n "Generating a new cookie secret... "
export COOKIE_KEY_PATH="/secrets/cookie.key.base64"
head -c32 /dev/urandom | base64 > $COOKIE_KEY_PATH
echo "done"

echo -n "Generating a new content encryption key... "
export CONTENT_KEYSTORE_PASSWORD=`head -c16 /dev/urandom | xxd -p`
export CONTENT_KEYSTORE_PATH="/secrets/content-encryption-key.jceks"
rm -f $CONTENT_KEYSTORE_PATH
keytool \
    -genseckey -alias basekey -keyalg AES -keysize 128 -storepass "$CONTENT_KEYSTORE_PASSWORD" \
    -keypass "$CONTENT_KEYSTORE_PASSWORD" -storetype jceks -keystore $CONTENT_KEYSTORE_PATH
echo "done"

echo
echo "Keywhiz requires a SSL/TLS server certificate and private key to run."

export KEYSTORE_PATH="/secrets/keywhiz-server.p12"
export KEYSTORE_PASSWORD=`head -c16 /dev/urandom | xxd -p`
export TRUSTSTORE_PATH="/secrets/ca-bundle.p12"
export TRUSTSTORE_PASSWORD=ponies # contains public certificates only
export CRL_PATH="/secrets/ca-crl.pem"

CERT_CHAIN_PEM="/secrets/keywhiz.pem"
PRIVATE_KEY_PEM="/secrets/keywhiz-key.pem"
CA_BUNDLE_PEM="/secrets/ca-bundle.pem"

echo
echo "Please copy the following files into the container:"
echo "  1. Copy your certificate chain to $CERT_CHAIN_PEM"
echo "  2. Copy your private key to $PRIVATE_KEY_PEM"
echo "  3. Copy your CA bundle to $CA_BUNDLE_PEM"
echo "  4. Copy a valid CRL file to $CRL_PATH"
echo 
echo "Files can be copied into a running container with docker cp."
echo

read -p "Hit enter to continue."
echo

assert_file_present $CERT_CHAIN_PEM
assert_file_present $PRIVATE_KEY_PEM
assert_file_present $CA_BUNDLE_PEM
assert_file_present $CRL_PATH

echo -n "Bundling certificate and private key into PKCS#12 keystore... "
rm -f $KEYSTORE_PATH
openssl pkcs12 \
    -export -out $KEYSTORE_PATH -inkey $PRIVATE_KEY_PEM -in $CERT_CHAIN_PEM \
    -password "pass:$KEYSTORE_PASSWORD"
echo "done"

echo "Bundling CA bundle into PKCS#12 trust store... "
rm -f $TRUSTSTORE_PATH
keytool \
    -noprompt -import -file $CA_BUNDLE_PEM -alias ca -storetype pkcs12 \
    -storepass $TRUSTSTORE_PASSWORD -keystore $TRUSTSTORE_PATH -trustcacerts

echo -n "Generating configuration from template..."
export KEYWHIZ_CONFIG=/data/keywhiz-docker.yaml
envsubst < /usr/src/app/keywhiz-config.tpl > $KEYWHIZ_CONFIG
echo "done"

echo
echo "Creating empty database and running migrations... "
java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar migrate $KEYWHIZ_CONFIG

echo
echo "Creating admin user for keywhiz... "
java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar add-user $KEYWHIZ_CONFIG

cat - <<EOF
All done! Config has been written to $KEYWHIZ_CONFIG. Make sure to set the
KEYWHIZ_CONFIG environment variable to tell the container which config to use
on startup.
EOF
