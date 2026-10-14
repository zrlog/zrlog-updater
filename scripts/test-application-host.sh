#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# != 1 || -z ${1:-} ]]; then
  printf 'Usage: %s <application test classpath>\n' "$0" >&2
  exit 2
fi
classpath=$1
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mapfile -t sources < <(rg --files tests/application-host/src/test/java -g '*.java' | sort)
javac --release 11 -encoding UTF-8 -cp "$classpath" -d "$work" "${sources[@]}"
java -cp "$work:$classpath" org.junit.runner.JUnitCore \
  com.zrlog.web.ApplicationUpdaterFactoryTest \
  com.zrlog.web.ApplicationUpgradeRunnerTest \
  com.zrlog.web.filter.ServletUpdaterAssemblyTest
