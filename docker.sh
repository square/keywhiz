#!/bin/sh

RESET=$(tput -T xterm sgr0)
RED=$(tput -T xterm setaf 1) 

if [ -z "$KEYWHIZ_CONFIG" ]; then
    KEYWHIZ_CONFIG=server/src/main/resources/keywhiz-development.yaml
    echo -n "${RED}"
    echo "------------------------------------------------------------------------"
    echo "---    No configuration file specified, using development config!    ---"
    echo "--- Use the KEYWHIZ_CONFIG environment variable to set a config file ---" 
    echo "------------------------------------------------------------------------"
    echo -n "${RESET}"
fi

java -jar server/target/keywhiz-server-*-SNAPSHOT-shaded.jar $1 $KEYWHIZ_CONFIG
