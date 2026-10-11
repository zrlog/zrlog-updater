#!/usr/bin/env bash
# Regression: drafts must be resolved through list/create results, never by tag.
set -euo pipefail
cd "$(dirname "$0")/.."
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
mkdir "$root/bin"
export RELEASE_TEST_ROOT="$root"
cat > "$root/bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ $* == 'api --paginate repos/zrlog/zrlog-updater/releases?per_page=100 --slurp' ]]; then
  cat "$RELEASE_TEST_ROOT/list.json"
elif [[ $1 == api && $2 == --method && $3 == POST && $4 == repos/zrlog/zrlog-updater/releases && $5 == --input ]]; then
  jq -e '.draft == true and .prerelease == true and .target_commitish == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"' "$6" >/dev/null
  jq '. + {id:123,assets:[]}' "$6"
  touch "$RELEASE_TEST_ROOT/created"
else
  printf 'Unexpected GitHub request: %s\n' "$*" >&2
  exit 1
fi
MOCK
chmod +x "$root/bin/gh"
export PATH="$root/bin:$PATH"
printf '[[{"id":123,"tag_name":"v1.2.3-preview.test","draft":true,"prerelease":true,"assets":[]}]]\n' > "$root/list.json"
bash scripts/github-release.sh zrlog/zrlog-updater v1.2.3-preview.test aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa preview > "$root/release.json"
jq -e '.id == 123 and .draft == true' "$root/release.json" >/dev/null
[[ ! -e $root/created ]]
printf '[[]]\n' > "$root/list.json"
bash scripts/github-release.sh zrlog/zrlog-updater v1.2.3-preview.test aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa preview > "$root/release.json"
jq -e '.id == 123 and .tag_name == "v1.2.3-preview.test" and .draft == true' "$root/release.json" >/dev/null
[[ -e $root/created ]]
printf 'Draft release lookup and creation ID regression checks passed\n'
