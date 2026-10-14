package com.zrlog.updater.core;

import com.google.gson.Gson;
import com.zrlog.common.vo.Version;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.updater.spi.UpdateCatalog;
import com.zrlog.updater.spi.UpdateCatalogs;

/** Entry point for assembled JAR and Native Image verification, never shipped as production code. */
public final class CatalogSmoke {
    public static void main(String[] args) {
        UpdateCatalog catalog = UpdateCatalogs.getInstance();
        Gson gson = new Gson();
        Version version = gson.fromJson(dockerManifest(), Version.class);
        catalog.validateDocker(version, "release");
        if (!"1234567".equals(version.getBuildId()) || version.getImages().size() != 1) {
            throw new AssertionError("Version DTO is not readable");
        }
        UpgradeProgressEvent.Data event = gson.fromJson(gson.toJson(
                UpgradeProgressEvent.data("download", "running", "Downloading", "artifact")), UpgradeProgressEvent.Data.class);
        if (!"artifact".equals(event.getDetail())) { throw new AssertionError("Progress DTO is not readable"); }
        System.out.println("Updater SPI, metadata and JSON smoke passed");
    }

    static String dockerManifest() {
        String image = "{\"repository\":\"zrlog/zrlog\",\"tag\":\"4.0.0\",\"digest\":\"sha256:" + "a".repeat(64)
                + "\",\"platforms\":[\"linux/arm64\"]}";
        return "{\"schemaVersion\":1,\"packageType\":\"docker-image\",\"channel\":\"release\","
                + "\"version\":\"4.0.0\",\"buildId\":\"1234567\",\"sourceCommit\":\"1234567" + "a".repeat(33)
                + "\",\"releaseDate\":\"2026-10-11 12:00:00Z\",\"images\":[" + image + "]}";
    }
}
