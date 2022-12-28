#!/bin/sh -

mvn \
validate \
org.codehaus.mojo:sql-maven-plugin:execute \
org.flywaydb:flyway-maven-plugin:migrate \
org.jooq:jooq-codegen-maven:generate
