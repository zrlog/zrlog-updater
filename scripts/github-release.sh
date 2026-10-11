#!/usr/bin/env bash
# Return a release JSON document, including drafts invisible to releases/tags/{tag}.
set -euo pipefail
repo=${1:?repository required}
tag=${2:?tag required}
commit=${3:?commit required}
channel=${4:?channel required}
[[ $repo == zrlog/zrlog-updater ]]
[[ $tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]
[[ $commit =~ ^[a-f0-9]{40}$ ]]
[[ $channel == preview || $channel == release ]]
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
gh api --paginate "repos/$repo/releases?per_page=100" --slurp > "$root/releases.json"
jq --arg tag "$tag" '[.[][] | select(.tag_name == $tag)]' "$root/releases.json" > "$root/matches.json"
count=$(jq length "$root/matches.json")
if [[ $count == 1 ]]; then
  jq '.[0]' "$root/matches.json"
else
  [[ $count == 0 ]]
  preview=false
  [[ $channel != preview ]] || preview=true
  jq -n --arg tag "$tag" --arg commit "$commit" --argjson preview "$preview" \
    '{tag_name:$tag,target_commitish:$commit,name:("ZrLog updater "+$tag),draft:true,prerelease:$preview,
      body:"Standalone Docker Compose updater for Linux AMD64 and ARM64. Native ZIPs, SHA-256, SBOM, provenance and Sigstore evidence are attached."}' > "$root/request.json"
  gh api --method POST "repos/$repo/releases" --input "$root/request.json"
fi
