#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=${1:-$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)}
gson="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
[[ -f $gson ]]
classpath="$gson:zrlog-updater-common/target/test-classes"
for module in spi common zip native war faas docker deb; do
  artifact="zrlog-updater-$module/target/zrlog-updater-$module-${version}.jar"
  [[ -f $artifact ]]
  classpath="$classpath:$artifact"
  if jar tf "$artifact" | rg -q '^com/zrlog/(admin|install|blog|model|business)/'; then
    printf 'Application business classes found in updater artifact %s\n' "$artifact" >&2
    exit 1
  fi
done
java -cp "$classpath" com.zrlog.updater.common.CatalogSmoke
if [[ -f target/zrlog-updater.jar ]] && jar tf target/zrlog-updater.jar | rg -q '^com/zrlog/(common|updater/(spi|common|zip|nativeimage|war|faas|docker|deb))/'; then
  printf 'Application update modules leaked into the Docker service\n' >&2
  exit 1
fi
if [[ ${2:-} == --native ]]; then
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  native-image --no-fallback -cp "$classpath" com.zrlog.updater.common.CatalogSmoke "$work/catalog-smoke"
  "$work/catalog-smoke"
fi
