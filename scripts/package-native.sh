#!/usr/bin/env bash
# Package an already built binary using this repository's identity.
set -euo pipefail
cd "$(dirname "$0")/.."
binary=$(realpath "${1:?binary required}")
output=$(realpath -m "${2:?output directory required}")
version=${3:?version required}
channel=${4:-preview}
[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]
[[ $channel == preview || $channel == release ]]
[[ $channel != release || $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
[[ $("$binary" --version) == "zrlog-updater $version" ]]
commit=$(git rev-parse HEAD)
build_id=${commit:0:12}
[[ $(uname -s) == Linux ]]
case $(uname -m) in x86_64) arch=amd64 ;; aarch64|arm64) arch=arm64 ;; *) exit 1 ;; esac
name="zrlog-${version}-${build_id}-${channel}-updater-Linux-${arch}.zip"
staging=$(mktemp -d)
trap 'rm -rf "$staging"' EXIT
install -m 755 "$binary" "$staging/zrlog-updater"
install -m 644 README.md docker-compose.updater.yml "$staging/"
touch -t 202601010000 "$staging/"*
(cd "$staging" && zip -X -9 -q "$name" zrlog-updater README.md docker-compose.updater.yml)
mkdir -p "$output"
if [[ -e $output/$name ]]; then
  cmp "$staging/$name" "$output/$name"
else
  mv "$staging/$name" "$output/$name"
fi
sha=$(sha256sum "$output/$name" | cut -d ' ' -f 1)
size=$(stat -c %s "$output/$name")
date=$(git show -s --format=%cI HEAD)
workflow=${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-zrlog/zrlog-updater}/actions/runs/${GITHUB_RUN_ID:-local}
jq -n --arg version "$version" --arg buildId "$build_id" --arg commit "$commit" \
  --arg date "$date" --arg channel "$channel" --arg sha "$sha" --argjson size "$size" \
  --arg url "https://dl.zrlog.com/updater/$channel/$name" --arg workflow "$workflow" \
  '{packageType:"updater",version:$version,buildId:$buildId,sourceCommit:$commit,
    releaseDate:$date,type:$channel,zipDownloadUrl:$url,checksumAlgorithm:"sha256",
    zipSha256:$sha,zipFileSize:$size,zipSources:[],
    sourceRepository:"https://github.com/zrlog/zrlog-updater",buildWorkflow:$workflow}' \
  > "$output/last.updater.Linux-${arch}.version.json"
printf '%s\n' "$output/$name"
