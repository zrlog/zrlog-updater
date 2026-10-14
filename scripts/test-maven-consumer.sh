#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Publish only to a temporary file repository, then resolve with an empty consumer cache.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
version=1.0.0-consumer-test-SNAPSHOT
./mvnw -B -ntp -pl updater-spi,updater-core -am \
  "-Drevision=$version" "-Dmaven.repo.local=$work/producer-cache" -DskipTests \
  "-DaltSnapshotDeploymentRepository=updater-test::file://$work/repository" deploy

mkdir -p "$work/consumer/src/main/java"
cat > "$work/consumer/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.zrlog.test</groupId><artifactId>updater-consumer</artifactId><version>1</version>
  <properties><maven.compiler.release>11</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
  <repositories><repository><id>updater-test</id><url>file://$work/repository</url>
    <releases><enabled>false</enabled></releases><snapshots><enabled>true</enabled></snapshots>
  </repository></repositories>
  <dependencies>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-spi</artifactId><version>$version</version></dependency>
    <dependency><groupId>com.hibegin</groupId><artifactId>zrlog-updater-core</artifactId><version>$version</version><scope>runtime</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.16.0</version></plugin>
  </plugins></build>
</project>
EOF
cat > "$work/consumer/src/main/java/Consumer.java" <<'EOF'
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateCatalogs;

public final class Consumer {
    public static void main(String[] args) {
        if (args.length == 1) {
            if (UpdateCatalogs.load(Consumer.class.getClassLoader()).isPresent()) {
                throw new AssertionError("An unassembled implementation was discovered");
            }
            return;
        }
        Version legacy = new Version();
        legacy.setZipDownloadUrl("https://dl.zrlog.com/release/zrlog.zip");
        String selected = UpdateCatalogs.getInstance().selectPackageSource(legacy, false, "dl");
        if (!selected.equals(legacy.getZipDownloadUrl())) {
            throw new AssertionError("Legacy release manifest compatibility failed");
        }
        System.out.println("Fresh Maven consumer and runtime SPI discovery passed");
    }
}
EOF
./mvnw -B -ntp -f "$work/consumer/pom.xml" "-Dmaven.repo.local=$work/consumer-cache" verify
spi="$work/consumer-cache/com/hibegin/zrlog-updater-spi/$version/zrlog-updater-spi-$version.jar"
core="$work/consumer-cache/com/hibegin/zrlog-updater-core/$version/zrlog-updater-core-$version.jar"
java -cp "$work/consumer/target/classes:$spi" Consumer absent
java -cp "$work/consumer/target/classes:$spi:$core" Consumer
