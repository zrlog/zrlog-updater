#!/usr/bin/env bash
# Validate metadata and the actual archive before any publication.
set -euo pipefail
metadata=${1:?metadata required}
version=${2:?version required}
commit=${3:?source commit required}
channel=${4:?channel required}
[[ $commit =~ ^[a-f0-9]{40}$ ]]
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]
[[ $channel == preview || $channel == release ]]
case $(basename "$metadata") in
  last.updater.Linux-amd64.version.json) arch=amd64 ;;
  last.updater.Linux-arm64.version.json) arch=arm64 ;;
  *) exit 1 ;;
esac
name="zrlog-${version}-${commit:0:12}-${channel}-updater-Linux-${arch}.zip"
archive=$(dirname "$metadata")/$name
jq -e --arg version "$version" --arg commit "$commit" --arg channel "$channel" \
  --arg url "https://dl.zrlog.com/updater/$channel/$name" \
  --arg sha "$(sha256sum "$archive" | cut -d ' ' -f 1)" --argjson size "$(stat -c %s "$archive")" '
  .packageType == "updater" and .version == $version and .sourceCommit == $commit and
  .buildId == $commit[0:12] and .type == $channel and .zipDownloadUrl == $url and
  .sourceRepository == "https://github.com/zrlog/zrlog-updater" and
  .checksumAlgorithm == "sha256" and .zipSha256 == $sha and .zipFileSize == $size and
  ($size > 0) and (.zipSources | type == "array")' "$metadata" >/dev/null
[[ $(unzip -Z1 "$archive" | LC_ALL=C sort) == $'README.md\ndocker-compose.updater.yml\nzrlog-updater' ]]
unzip -tq "$archive" >/dev/null
# Exact modes also reject symlinks and unexpected directory entries.
[[ $(zipinfo -l "$archive" | awk '$NF == "zrlog-updater" {print $1}') == '-rwxr-xr-x' ]]
for entry in README.md docker-compose.updater.yml; do
  [[ $(zipinfo -l "$archive" | awk -v name="$entry" '$NF == name {print $1}') == '-rw-r--r--' ]]
done
printf '%s\n' "$archive"
