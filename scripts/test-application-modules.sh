#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=${1:-$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)}
spi="updater-spi/target/zrlog-updater-spi-${version}.jar"
core="updater-core/target/zrlog-updater-core-${version}.jar"
gson="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
[[ -f $spi && -f $core && -f $gson ]]
classpath="$spi:$core:updater-core/target/test-classes:$gson"
java -cp "$classpath" com.zrlog.updater.core.CatalogSmoke
for artifact in "$spi" "$core"; do
  if jar tf "$artifact" | rg -q '^com/zrlog/(admin|install|blog|model|business)/'; then
    printf 'Application business classes found in updater artifact %s\n' "$artifact" >&2
    exit 1
  fi
done
if jar tf target/zrlog-updater.jar | rg -q '^com/zrlog/(common|updater/(spi|core))/'; then
  printf 'Application update modules leaked into the Docker service\n' >&2
  exit 1
fi
if [[ ${2:-} == --native ]]; then
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  native-image --no-fallback -cp "$classpath" com.zrlog.updater.core.CatalogSmoke "$work/catalog-smoke"
  "$work/catalog-smoke"
fi
