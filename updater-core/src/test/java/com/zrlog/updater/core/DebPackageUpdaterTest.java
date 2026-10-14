package com.zrlog.updater.core;

import com.google.gson.Gson;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.core.deb.DebPackageUpdater;
import com.zrlog.updater.spi.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class DebPackageUpdaterTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void installsVerifiedIdentityAndPersistsRecoveryStatus() throws Exception {
        Fixture f = new Fixture();
        f.updater.request("preview");
        Files.writeString(new File(f.b.debRequests, "preview.request").toPath(), "ignored: arbitrary URL or command");
        assertEquals(0, f.updater.install("preview", true));
        assertTrue(f.commands.stream().anyMatch(s -> s.startsWith("dpkg --install ")));
        assertEquals("complete", new DebPackageUpdater(f.b.build(), f::command).status().stage);
        assertEquals("4.0.0-SNAPSHOT", f.updater.status().version);
        assertFalse(new File(f.b.debRequests, "preview.request").exists());
        assertFalse(new File(f.b.debWork, "update.deb").exists());
    }
    @Test public void refusesCorruptionWrongPackageAndFailedDpkg() throws Exception {
        for (String failure : List.of("checksum", "identity", "dpkg", "service")) {
            Fixture f = new Fixture(); f.failure = failure;
            assertEquals(1, f.updater.install("release", true));
            assertEquals("error", f.updater.status().stage);
            if (failure.equals("checksum") || failure.equals("identity"))
                assertFalse(f.commands.stream().anyMatch(s -> s.startsWith("dpkg --install ")));
        }
    }
    @Test public void rejectsInvalidChannelsAndDuplicateRequests() throws Exception {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.updater.request("../release"));
        f.updater.request("release");
        assertThrows(IllegalStateException.class, () -> f.updater.request("release"));
    }
    @Test public void supportsExistingExpandedSnapshotAndExactNewManifestVersion() throws Exception {
        Fixture legacy = new Fixture(); legacy.packageVersion = "4.0.0~builder+20261014120000-abc1234";
        assertEquals(0, legacy.updater.install("preview", true));
        Fixture modern = new Fixture(); modern.packageVersion = "4.0.0~builder+20261014120000-abc1234";
        modern.manifestPackageVersion = modern.packageVersion;
        assertEquals(0, modern.updater.install("preview", true));
        Fixture mismatch = new Fixture(); mismatch.manifestPackageVersion = "4.0.0+different";
        assertEquals(1, mismatch.updater.install("preview", true));
        assertFalse(mismatch.commands.stream().anyMatch(s -> s.startsWith("dpkg --install ")));
    }
    private final class Fixture {
        final UpdateContext.Builder b = new UpdateContext.Builder();
        final List<String> commands = new ArrayList<>();
        final DebPackageUpdater updater;
        final File source;
        String failure = "";
        String packageVersion = "4.0.0-SNAPSHOT+abc1234", manifestPackageVersion;
        Fixture() throws Exception {
            b.debRequests = temporary.newFolder(); b.debState = temporary.newFolder(); b.debWork = temporary.newFolder();
            b.metadataOrigin = "https://dl.example"; b.manifestFilename = "last.Linux-amd64.deb.version.json";
            source = temporary.newFile(); Files.writeString(source.toPath(), "deb-bytes");
            b.http = new UpdateHttp() {
                public String read(URI uri) throws Exception {
                    Version v = new Version(); v.setVersion("4.0.0-SNAPSHOT"); v.setBuildId("abc1234");
                    v.setReleaseDate("2026-10-14 00:00:00+08:00"); v.setZipDownloadUrl("https://dl.example/update.deb");
                    v.setDebPackageVersion(manifestPackageVersion);
                    v.setZipFileSize(source.length()); v.setZipSha256(PackageFiles.checksum(source, "SHA-256"));
                    return new Gson().toJson(v);
                }
                public int download(URI uri, File target) throws Exception {
                    Files.writeString(target.toPath(), failure.equals("checksum") ? "bad-bytes" : "deb-bytes"); return 200;
                }
                public HttpResponse<String> send(HttpRequest request) { throw new AssertionError(); }
            };
            updater = new DebPackageUpdater(b.build(), this::command);
        }
        String command(String... args) throws Exception {
            String command = String.join(" ", args); commands.add(command);
            if (command.equals("id -u")) return "0";
            if (args[0].equals("dpkg-deb")) {
                switch(args[3]) {
                    case "Package": return failure.equals("identity") ? "other" : "zrlog";
                    case "Version": return packageVersion;
                    case "Architecture": return "amd64";
                }
            }
            if (command.equals("dpkg --print-architecture")) return "amd64";
            if (args[0].equals("dpkg") && failure.equals("dpkg")) throw new java.io.IOException("dpkg failed");
            if (args[0].equals("dpkg-query")) return args[2].contains("Status") ? "install ok installed" : packageVersion;
            if (args[0].equals("systemctl") && failure.equals("service")) throw new java.io.IOException("service failed");
            if (command.contains("MainPID")) return "123";
            return "";
        }
    }
}
