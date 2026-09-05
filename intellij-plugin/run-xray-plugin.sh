#!/bin/sh
set -eu
if [ -z "${PROJECT_XRAY_CLI:-}" ]; then
  echo "Set PROJECT_XRAY_CLI to the absolute X-Ray core JAR path."
  exit 1
fi
./gradlew runIde
