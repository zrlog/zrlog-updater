package com.zrlog.updater.core;

import com.google.gson.Gson;
import com.zrlog.common.vo.Version;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.updater.spi.UpdateCatalog;
import com.zrlog.updater.spi.UpdateCatalogs;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class UpdateCatalogContractTest {
    private final UpdateCatalog catalog = UpdateCatalogs.getInstance();

    @Test public void usesConfiguredMetadataAndRejectsInvalidPaths() {
        assertEquals("https://mirror.example/packages/preview/docker.json",
                catalog.metadataUri("https://mirror.example/packages", "docker.json", "preview").toString());
        for (String origin : new String[]{"http://dl.example", "//dl.example", "file:///tmp", "https://user@dl.example",
                "https://dl.example/?next=evil", "https://dl.example/a/../b", "https://dl.example/#x"}) {
            assertThrows(IllegalArgumentException.class, () -> catalog.metadataUri(origin, "docker.json", "release"));
        }
        assertThrows(IllegalArgumentException.class, () -> catalog.metadataUri("https://dl.example", "../evil.json", "release"));
        assertThrows(IllegalArgumentException.class, () -> catalog.metadataUri("https://dl.example", "docker.json", "../preview"));
    }

    @Test public void preservesLegacyReleaseAndPublishedPackageSources() {
        Version version = new Gson().fromJson("{\"version\":\"3.9.0\",\"buildId\":\"1234567\","
                + "\"zipDownloadUrl\":\"https://dl.example/release/app.zip\",\"zipMd5sum\":\"legacy-md5\"}", Version.class);
        assertEquals(version.getZipDownloadUrl(), catalog.selectPackageSource(version, false, "dl"));
        assertEquals("legacy-md5", version.getZipMd5sum());
        assertThrows(IllegalStateException.class, () -> catalog.selectPackageSource(version, false, "github"));
        Version.DownloadSource dl = new Version.DownloadSource(); dl.id = "dl"; dl.url = version.getZipDownloadUrl();
        Version.DownloadSource github = new Version.DownloadSource(); github.id = "github";
        github.url = "https://github.com/example/app/releases/download/v4.0.0/app.zip";
        version.setZipSources(List.of(dl, github));
        assertThrows(IllegalStateException.class, () -> catalog.selectPackageSource(version, false, "github"));
        version.setZipSha256("a".repeat(64)); version.setZipFileSize(123);
        assertEquals(github.url, catalog.selectPackageSource(version, false, "github"));
        assertEquals(dl.url, catalog.selectPackageSource(version, false, "dl"));
        version.setZipSources(List.of(dl));
        assertThrows(IllegalStateException.class, () -> catalog.selectPackageSource(version, false, "github"));
        version.setZipSources(List.of(dl, dl));
        assertThrows(IllegalArgumentException.class, () -> catalog.selectPackageSource(version, false, "dl"));
    }

    @Test public void validatesDockerIdentityAndAmbiguousPlatforms() {
        Version version = new Gson().fromJson(CatalogSmoke.dockerManifest(), Version.class);
        catalog.validateDocker(version, "release");
        assertThrows(IllegalArgumentException.class, () -> catalog.validateDocker(version, "preview"));
        version.setImages(List.of(version.getImages().get(0), version.getImages().get(0)));
        assertThrows(IllegalArgumentException.class, () -> catalog.validateDocker(version, "release"));
    }

    @Test public void keepsProgressJsonCompatible() {
        UpgradeProgressEvent.Data progress = new Gson().fromJson(
                "{\"stage\":\"restart\",\"status\":\"running\",\"message\":\"Restarting\",\"detail\":\"4.0.0\"}",
                UpgradeProgressEvent.Data.class);
        assertEquals("upgrade-progress", UpgradeProgressEvent.EVENT);
        assertEquals("restart", progress.getStage());
        assertEquals("running", progress.getStatus());
        assertEquals("4.0.0", progress.getDetail());
    }

}
