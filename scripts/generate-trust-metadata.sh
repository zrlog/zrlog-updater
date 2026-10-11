#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir=$(realpath "${1:?package directory required}")
version=${2:?version required}
commit=${3:?source commit required}
channel=${4:?channel required}
shopt -s nullglob
manifests=("$dir"/last.updater.*.version.json)
((${#manifests[@]} > 0))
for metadata in "${manifests[@]}"; do
  artifact=$(bash scripts/validate-package.sh "$metadata" "$version" "$commit" "$channel")
  name=$(basename "$artifact")
  (cd "$dir" && sha256sum "$name" > "$name.sha256")
  syft scan "$artifact" -o "cyclonedx-json=$artifact.sbom.cdx.json"
  cosign sign-blob --yes --bundle "$artifact.sigstore.json" "$artifact"
  jq -n --arg name "$name" --argjson size "$(stat -c %s "$artifact")" \
    --arg sha "$(sha256sum "$artifact" | cut -d ' ' -f 1)" --arg commit "$commit" \
    --arg sbomSha "$(sha256sum "$artifact.sbom.cdx.json" | cut -d ' ' -f 1)" \
    --arg workflow "$(jq -r '.buildWorkflow' "$metadata")" --arg timestamp "$(jq -r '.releaseDate' "$metadata")" \
    '{schemaVersion:1,artifact:{name:$name,size:$size,sha256:$sha},
      source:{repository:"https://github.com/zrlog/zrlog-updater",commit:$commit},
      build:{builder:"github-actions",workflow:$workflow,timestamp:$timestamp},
      evidence:{sbom:{name:($name+".sbom.cdx.json"),sha256:$sbomSha,format:"CycloneDX JSON"},
        signatureBundle:($name+".sigstore.json")}}' > "$artifact.provenance.json"
done
