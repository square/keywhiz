#!/bin/bash

RESET=$(tput -T xterm sgr0)
RED=$(tput -T xterm setaf 1) 

if [ "$1" == "wizard" ]; then
    exec ./wizard.sh
fi

# bypass keywhiz config so we can report the version
if [ "$1" = "-v" ] || [ "$1" = "--version" ]; then
    java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar $1
    exit $?
fi

if [ -z "$KEYWHIZ_CONFIG" ]; then
    KEYWHIZ_CONFIG=server/src/main/resources/keywhiz-development.yaml
    echo -n "${RED}"
    echo "------------------------------------------------------------------------"
    echo "---    No configuration file specified, using development config!    ---"
    echo "--- Use the KEYWHIZ_CONFIG environment variable to set a config file ---" 
    echo "------------------------------------------------------------------------"
    echo -n "${RESET}"
fi

if [ "$MIGRATE_ON_STARTUP" == "true" ]; then
    java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar migrate $KEYWHIZ_CONFIG
fi

java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar $1 $KEYWHIZ_CONFIG
