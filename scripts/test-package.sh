#!/usr/bin/env bash
# Real archive roundtrip plus fail-closed identity and corruption cases.
set -euo pipefail
cd "$(dirname "$0")/.."
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
cat > "$root/binary" <<'BIN'
#!/usr/bin/env bash
printf 'zrlog-updater 1.2.3-preview.test\n'
BIN
chmod +x "$root/binary"
version=1.2.3-preview.test
commit=$(git rev-parse HEAD)
bash scripts/package-native.sh "$root/binary" "$root/dist" "$version" preview >/dev/null
metadata=("$root/dist/"*.json)
archive=$(bash scripts/validate-package.sh "${metadata[0]}" "$version" "$commit" preview)
unzip -q "$archive" -d "$root/extracted"
[[ $("$root/extracted/zrlog-updater" --version) == "zrlog-updater $version" ]]
if bash scripts/package-native.sh "$root/binary" "$root/bad" 9.9.9 preview >/dev/null 2>&1; then exit 1; fi
if bash scripts/validate-package.sh "${metadata[0]}" "$version" 0000000000000000000000000000000000000000 preview >/dev/null 2>&1; then exit 1; fi
jq '.sourceRepository="https://github.com/94fzb/zrlog"' "${metadata[0]}" > "$root/bad.json"
cp "${metadata[0]}" "$root/good.json"
cp "$root/bad.json" "${metadata[0]}"
if bash scripts/validate-package.sh "${metadata[0]}" "$version" "$commit" preview >/dev/null 2>&1; then exit 1; fi
cp "$root/good.json" "${metadata[0]}"
printf 'corrupt' >> "$archive"
if bash scripts/validate-package.sh "${metadata[0]}" "$version" "$commit" preview >/dev/null 2>&1; then exit 1; fi
printf 'Package roundtrip, version/source identity and corrupt archive checks passed\n'
