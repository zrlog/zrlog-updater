#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
case ${1:-} in
  '')
    # Local contract test: never upload artifacts to a remote repository.
    version=4.0.0-consumer-test-SNAPSHOT
    repository_url="file://$work/repository"
    releases=false
    snapshots=true
    ./mvnw -B -ntp -pl '!zrlog-docker-updater' \
      "-Drevision=$version" "-Dmaven.repo.local=$work/producer-cache" -DskipTests \
      "-DaltSnapshotDeploymentRepository=updater-test::$repository_url" deploy
    ;;
  --central-snapshots)
    version=${2:-$(sed -n 's/.*<revision>\([^<]*\)<\/revision>.*/\1/p' pom.xml)}
    [[ $version == *-SNAPSHOT ]]
    repository_url=https://central.sonatype.com/repository/maven-snapshots/
    releases=false
    snapshots=true
    ;;
  --central-release)
    version=${2:?A published release version is required}
    [[ $version != *SNAPSHOT* ]]
    repository_url=https://repo.maven.apache.org/maven2/
    releases=true
    snapshots=false
    ;;
  *) printf 'Usage: %s [--central-snapshots [version] | --central-release version]\n' "$0" >&2; exit 2 ;;
esac
compare_build=${3:-}
[[ -z $compare_build || $compare_build == --compare-build ]]
printf 'Consumer repository: %s\nConsumer version: %s\n' "$repository_url" "$version"

mkdir -p "$work/consumer/src/main/java"
cat > "$work/consumer/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.zrlog.test</groupId><artifactId>updater-consumer</artifactId><version>1</version>
  <properties><maven.compiler.release>11</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <repositories><repository><id>updater-test</id><url>$repository_url</url>
    <releases><enabled>$releases</enabled><checksumPolicy>fail</checksumPolicy></releases>
    <snapshots><enabled>$snapshots</enabled><checksumPolicy>fail</checksumPolicy></snapshots>
  </repository></repositories>
  <dependencies>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-spi</artifactId><version>$version</version></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-zip</artifactId><version>$version</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-native</artifactId><version>$version</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-war</artifactId><version>$version</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-faas</artifactId><version>$version</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-docker</artifactId><version>$version</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-deb</artifactId><version>$version</version><scope>runtime</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.16.0</version></plugin>
  </plugins></build>
</project>
EOF
cat > "$work/consumer/src/main/java/Consumer.java" <<'EOF'
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateCatalogs;
import com.zrlog.updater.spi.UpdateProviders;

public final class Consumer {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("absent")) {
            if (UpdateCatalogs.load(Consumer.class.getClassLoader()).isPresent()) {
                throw new AssertionError("An unassembled implementation was discovered");
            }
            if (UpdateProviders.find(com.zrlog.common.UpdaterTypeEnum.ZIP).isPresent() || !UpdateProviders.messageResources("zh_CN").isEmpty()) {
                throw new AssertionError("Unassembled providers or messages were discovered");
            }
            return;
        }
        Version legacy = new Version();
        for (com.zrlog.common.UpdaterTypeEnum type : com.zrlog.common.UpdaterTypeEnum.values()) {
            boolean expected = args.length == 0 || type.name().equals(args[0]);
            if (UpdateProviders.find(type).isPresent() != expected) throw new AssertionError("Unexpected provider " + type);
        }
        for (String resource : UpdateProviders.messageResources("zh_CN")) {
            if (Consumer.class.getResource(resource) == null) throw new AssertionError("Missing " + resource);
        }
        legacy.setZipDownloadUrl("https://dl.zrlog.com/release/zrlog.zip");
        String selected = UpdateCatalogs.getInstance().selectPackageSource(legacy, false, "dl");
        if (!selected.equals(legacy.getZipDownloadUrl())) {
            throw new AssertionError("Legacy release manifest compatibility failed");
        }
        System.out.println("Fresh Maven consumer and runtime SPI discovery passed");
    }
}
EOF
./mvnw -B -ntp -U -f "$work/consumer/pom.xml" "-Dmaven.repo.local=$work/consumer-cache" verify
for module in parent spi common zip native war faas docker deb; do
  directory="$work/consumer-cache/com/hibegin/zrlog-updater-$module/$version"
  pom="$directory/zrlog-updater-$module-$version.pom"
  [[ -f $pom ]]
  if rg -q '\$\{revision\}' "$pom"; then
    printf 'Unresolved revision in published POM: %s\n' "$pom" >&2
    exit 1
  fi
  sha256sum "$pom"
  if [[ $compare_build == --compare-build ]]; then
    build_directory="$PWD/zrlog-updater-$module"
    [[ $module != parent ]] || build_directory="$PWD"
    cmp "$pom" "$build_directory/.flattened-pom.xml"
  fi
  if [[ $module != parent ]]; then
    sha256sum "$directory/zrlog-updater-$module-$version.jar"
    if [[ $compare_build == --compare-build ]]; then
      cmp "$directory/zrlog-updater-$module-$version.jar" "$build_directory/target/zrlog-updater-$module-$version.jar"
    fi
  fi
  if [[ $snapshots == true ]]; then
    sed -n '/<snapshot>/,/<\/snapshot>/p' "$directory/maven-metadata-updater-test.xml"
  fi
done
spi="$work/consumer-cache/com/hibegin/zrlog-updater-spi/$version/zrlog-updater-spi-$version.jar"
java -cp "$work/consumer/target/classes:$spi" Consumer absent
gson="$work/consumer-cache/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
common="$work/consumer-cache/com/hibegin/zrlog-updater-common/$version/zrlog-updater-common-$version.jar"
base_classpath="$work/consumer/target/classes:$spi:$common:$gson"
java -cp "$base_classpath" Consumer common
classpath="$base_classpath"
for module in zip native war faas docker deb; do
  artifact="$work/consumer-cache/com/hibegin/zrlog-updater-$module/$version/zrlog-updater-$module-$version.jar"
  type=${module^^}
  [[ $module != native ]] || type=NATIVE_IMAGE
  java -cp "$base_classpath:$artifact" Consumer "$type"
  classpath="$classpath:$artifact"
done
java -cp "$classpath" Consumer

# The construction API must also compile with common alone, without deployment or application artifacts.
mkdir -p "$work/common-consumer/src/main/java"
cat > "$work/common-consumer/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.zrlog.test</groupId><artifactId>common-consumer</artifactId><version>1</version>
  <properties><maven.compiler.release>11</maven.compiler.release></properties>
  <repositories><repository><id>updater-test</id><url>$repository_url</url>
    <releases><enabled>$releases</enabled><checksumPolicy>fail</checksumPolicy></releases>
    <snapshots><enabled>$snapshots</enabled><checksumPolicy>fail</checksumPolicy></snapshots>
  </repository></repositories>
  <dependencies><dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-common</artifactId><version>$version</version></dependency></dependencies>
  <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.16.0</version></plugin></plugins></build>
</project>
EOF
cat > "$work/common-consumer/src/main/java/CommonConsumer.java" <<'EOF'
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.common.UpdaterUtils;
import com.zrlog.updater.common.UpdaterRuntime;
import com.zrlog.updater.spi.UpdateProviders;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateSession;

public final class CommonConsumer {
    public static void main(String[] args) {
        boolean assembled = args.length != 0;
        if (UpdateProviders.find(UpdaterTypeEnum.DEB).isPresent() != assembled) throw new AssertionError("Unexpected deployment provider assembly");
        if (UpdaterUtils.selectType(true, true, "deb", true) != UpdaterTypeEnum.DOCKER) throw new AssertionError("Deployment priority changed");
        java.io.File executable = new java.io.File("zrlog");
        UpdateContext.Builder host = new UpdateContext.Builder();
        host.executable = executable;
        UpdateSession session = UpdaterUtils.create(new UpdaterRuntime(false, false, "deb", false, "", new java.io.File(".")), host.build());
        if (assembled && (session == null || session.updater.getType() != UpdaterTypeEnum.DEB || session.context.executable != executable))
            throw new AssertionError("SPI did not automatically construct the deployment updater");
        if (!assembled && session != null) throw new AssertionError("Common must not include a deployment provider");
        System.out.println("Common-only Maven automatic assembly API consumer passed: " + assembled);
    }
}
EOF
./mvnw -B -ntp -U -f "$work/common-consumer/pom.xml" "-Dmaven.repo.local=$work/consumer-cache" verify
java -cp "$work/common-consumer/target/classes:$spi:$common:$gson" CommonConsumer
java -cp "$work/common-consumer/target/classes:$classpath" CommonConsumer assembled
