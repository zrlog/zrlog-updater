#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 1 || ! -f ${1:-} ]]; then
  printf 'Usage: %s <application.zip|application.war>\n' "$0" >&2
  exit 2
fi
archive=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
case "$archive" in
  *.zip) library_root=lib; package_type=ZIP ;;
  *.war) library_root=WEB-INF/lib; package_type=WAR ;;
  *) printf 'Unsupported application package: %s\n' "$archive" >&2; exit 2 ;;
esac
repository_root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -qq "$archive" -d "$work/package"
target=$archive
[[ $package_type != ZIP ]] || target="$work/package/zrlog-starter.jar"
java --source 11 -cp "$work/package/$library_root/*" \
  "$repository_root/tests/application-package/PackageUpdaterContract.java" "$package_type" "$target"
