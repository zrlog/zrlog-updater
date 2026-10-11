#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output_dir=${1:-dist}
version=${2:?updater version is required}
channel=${3:-preview}
./mvnw -B -Pnative package "-Drevision=${version}" \
  "-DmirrorWebSite=${ZRLOG_MIRROR_WEBSITE:-https://dl.zrlog.com/}" \
  "-DupdateVersionJsonFilename=${ZRLOG_DOCKER_UPDATE_VERSION_JSON_FILENAME:-last.docker-image.version.json}"
bash scripts/smoke-native.sh target/zrlog-updater
bash scripts/package-native.sh target/zrlog-updater "$output_dir" "$version" "$channel"
