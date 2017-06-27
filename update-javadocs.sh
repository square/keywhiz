#!/bin/bash

# For whatever reason, javadocs only works for me if I've got a release checked
# out, like `git checkout v0.8.0`

mvn javadocs:javadocs

for dir in api cli client hkdf log model server testing; do
  rm -r docs/javadocs/$dir
  cp -r $dir/target/site/apidocs/ docs/javadocs/$dir
done
