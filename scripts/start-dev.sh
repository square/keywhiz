#!/bin/bash

if [ -z "${1}" ]; then
  echo "usage: scripts/${1} <migrate|add-user|server>"
  exit 1
fi

if [ ! -f "server/src/main/resources/keywhiz-development.yaml.docker" ]; then
  echo "server/src/main/resources/keywhiz-development.yaml.docker not found."
  echo "maybe you're not in the project root, please run this script with"
  echo "scripts/{$1} <migrate|add-user|server>"
  exit 1
fi

docker run -it \
	-v $(pwd)/server/src/main/resources/keywhiz-development.yaml.docker:/config \
	-e KEYWHIZ_CONFIG=/config \
	-p 127.0.0.1:4444:4444 \
	-v keywhiz-db-devel:/data \
	square/keywhiz \
	$@
