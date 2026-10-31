#!/usr/bin/env sh
set -eu
mvn clean package
printf '\nPlugin built: target/PrefixTitles-Paper-1.2.0.jar\n'
