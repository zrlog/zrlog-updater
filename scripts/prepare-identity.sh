#!/usr/bin/env bash
set -euo pipefail
commit=$(git rev-parse HEAD)
revision=$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)
base=${revision%-SNAPSHOT}
[[ $base =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
channel=${CHANNEL:-preview}
[[ $channel == preview || $channel == release ]]
version="$base-preview.${commit:0:12}.${GITHUB_RUN_ID:?}.${GITHUB_RUN_ATTEMPT:?}"
if [[ $channel == release ]]; then
  [[ ${SOURCE_REF:?} == "v$base" ]]
  [[ $(git rev-parse "refs/tags/v$base^{commit}") == "$commit" ]]
  version=$base
fi
printf 'commit=%s\nversion=%s\nattempt=%s\nchannel=%s\n' \
  "$commit" "$version" "$GITHUB_RUN_ATTEMPT" "$channel" >> "${GITHUB_OUTPUT:?}"
