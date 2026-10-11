#!/usr/bin/env bash
# Publish immutable updater assets, then verified download locations and channel pointers.
set -euo pipefail
cd "$(dirname "$0")/.."
dir=$(realpath "${1:?package directory required}")
version=${2:?version required}
commit=${3:?commit required}
channel=${4:?channel required}
: "${ZRLOG_S3_BUCKET:?}" "${ZRLOG_S3_ENDPOINT:?}" "${AWS_ACCESS_KEY_ID:?}" "${AWS_SECRET_ACCESS_KEY:?}" "${GH_TOKEN:?}"
[[ $ZRLOG_S3_ENDPOINT == https://* ]]
export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-auto} AWS_PAGER=''
repo=zrlog/zrlog-updater
tag="v$version"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
s3() { aws --endpoint-url "$ZRLOG_S3_ENDPOINT" s3api "$@"; }
# Missing objects are distinct from access/network failures.
get_object() {
  local key=$1 path=$2
  if s3 get-object --bucket "$ZRLOG_S3_BUCKET" --key "$key" "$path" > "$path.response" 2> "$tmp/s3.error"; then return 0; fi
  if grep -Eq '\((NoSuchKey|404|NotFound)\)' "$tmp/s3.error"; then return 4; fi
  cat "$tmp/s3.error" >&2
  return 1
}
immutable_put() {
  local key=$1 file=$2 type=$3 status=0
  get_object "$key" "$tmp/existing" || status=$?
  if [[ $status == 0 ]]; then cmp "$file" "$tmp/existing"; return; fi
  [[ $status == 4 ]] || return 1
  s3 put-object --bucket "$ZRLOG_S3_BUCKET" --key "$key" --body "$file" \
    --content-type "$type" --cache-control 'public, max-age=31536000, immutable' \
    --if-none-match '*' >/dev/null
  get_object "$key" "$tmp/readback"
  cmp "$file" "$tmp/readback"
}
# Check the complete pair before changing any external state.
assets=()
for arch in amd64 arm64; do
  metadata="$dir/last.updater.Linux-$arch.version.json"
  archive=$(bash scripts/validate-package.sh "$metadata" "$version" "$commit" "$channel")
  name=$(basename "$archive")
  (cd "$dir" && sha256sum --check "$name.sha256")
  jq -e --arg name "$name" --arg commit "$commit" \
    --arg sha "$(jq -r .zipSha256 "$metadata")" --argjson size "$(jq -r .zipFileSize "$metadata")" \
    --arg sbom "$(sha256sum "$archive.sbom.cdx.json" | cut -d ' ' -f 1)" '
      .artifact == {name:$name,size:$size,sha256:$sha} and
      .source == {repository:"https://github.com/zrlog/zrlog-updater",commit:$commit} and
      .evidence.sbom.sha256 == $sbom and .evidence.sbom.name == ($name+".sbom.cdx.json") and
      .evidence.signatureBundle == ($name+".sigstore.json")' "$archive.provenance.json" >/dev/null
  cosign verify-blob --bundle "$archive.sigstore.json" \
    --certificate-identity-regexp '^https://github.com/zrlog/zrlog-updater/\.github/workflows/build-publish\.yml@refs/' \
    --certificate-oidc-issuer https://token.actions.githubusercontent.com "$archive"
  assets+=("$archive" "$archive.sha256" "$archive.sbom.cdx.json" "$archive.sigstore.json" "$archive.provenance.json")
  status=0
  get_object "updater/$channel/$(basename "$metadata")" "$tmp/current-$arch" || status=$?
  if [[ $status == 0 ]]; then
    jq -e --slurpfile next "$metadata" '
      .sourceRepository == $next[0].sourceRepository and .type == $next[0].type and
      (.releaseDate <= $next[0].releaseDate)' "$tmp/current-$arch" >/dev/null
    jq -r '.ETag' "$tmp/current-$arch.response" > "$tmp/etag-$arch"
  else
    [[ $status == 4 ]]
    printf '*\n' > "$tmp/etag-$arch"
  fi
done
# Resolve refs through the API; release tags must already exist.
gh api "repos/$repo/git/matching-refs/tags/$tag" > "$tmp/refs.json"
ref_count=$(jq --arg ref "refs/tags/$tag" '[.[] | select(.ref == $ref)] | length' "$tmp/refs.json")
if [[ $ref_count == 0 ]]; then
  [[ $channel == preview ]]
  gh api --method POST "repos/$repo/git/refs" -f "ref=refs/tags/$tag" -f "sha=$commit" >/dev/null
else
  [[ $ref_count == 1 ]]
  [[ $(gh api "repos/$repo/commits/$tag" --jq .sha) == "$commit" ]]
fi
# Drafts are not available through releases/tags; keep the authenticated release ID.
bash scripts/github-release.sh "$repo" "$tag" "$commit" "$channel" > "$tmp/release.json"
release_id=$(jq -er '.id' "$tmp/release.json")
[[ $release_id =~ ^[0-9]+$ ]]
jq -e --argjson preview "$([[ $channel == preview ]] && printf true || printf false)" \
  '.prerelease == $preview' "$tmp/release.json" >/dev/null
for asset in "${assets[@]}"; do
  name=$(basename "$asset")
  existing_id=$(jq -r --arg name "$name" '.assets[] | select(.name == $name) | .id' "$tmp/release.json")
  if [[ -n $existing_id ]]; then
    gh api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$existing_id" > "$tmp/asset"
    cmp "$asset" "$tmp/asset"
  else
    gh api --method POST "https://uploads.github.com/repos/$repo/releases/$release_id/assets?name=$name" \
      -H 'Content-Type: application/octet-stream' --input "$asset" >/dev/null
  fi
  type=application/octet-stream
  [[ $name != *.json ]] || type=application/json
  immutable_put "updater/$channel/$name" "$asset" "$type"
done
if [[ $(jq -r .draft "$tmp/release.json") == true ]]; then
  gh api --method PATCH "repos/$repo/releases/$release_id" -F draft=false >/dev/null
fi
gh api "repos/$repo/releases/$release_id" > "$tmp/release.json"
jq -e '.draft == false' "$tmp/release.json" >/dev/null
# Verify anonymous bytes from both public endpoints before advertising either source.
for arch in amd64 arm64; do
  metadata="$dir/last.updater.Linux-$arch.version.json"
  dl=$(jq -r .zipDownloadUrl "$metadata")
  name=${dl##*/}
  github=$(jq -er --arg name "$name" '.assets[] | select(.name == $name and .state == "uploaded") | .browser_download_url' "$tmp/release.json")
  [[ $github == "https://github.com/$repo/releases/download/$tag/$name" ]]
  for url in "$dl" "$github"; do
    curl --fail --silent --show-error --location --retry 4 --retry-all-errors \
      --proto '=https' --proto-redir '=https' --max-time 300 "$url" -o "$tmp/download"
    cmp "$dir/$name" "$tmp/download"
  done
  jq --arg dl "$dl" --arg github "$github" \
    '.zipSources=[{id:"dl",url:$dl},{id:"github",url:$github}]' "$metadata" > "$tmp/verified-$arch.json"
done
for arch in amd64 arm64; do
  name="last.updater.Linux-$arch.version.json"
  immutable_put "updater/$channel/manifests/$version-${commit:0:12}/$name" "$tmp/verified-$arch.json" application/json
done
for arch in amd64 arm64; do
  name="last.updater.Linux-$arch.version.json"
  etag=$(cat "$tmp/etag-$arch")
  condition=(--if-match "$etag")
  [[ $etag != '*' ]] || condition=(--if-none-match '*')
  s3 put-object --bucket "$ZRLOG_S3_BUCKET" --key "updater/$channel/$name" \
    --body "$tmp/verified-$arch.json" --content-type application/json --cache-control no-cache "${condition[@]}" >/dev/null
  get_object "updater/$channel/$name" "$tmp/final-$arch"
  cmp "$tmp/verified-$arch.json" "$tmp/final-$arch"
  cp "$tmp/verified-$arch.json" "$metadata"
done
printf 'Verified updater %s packages at GitHub and dl.zrlog.com\n' "$version"
