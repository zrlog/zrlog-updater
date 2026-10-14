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
        com.zrlog.updater.spi.UpdateContext.Builder context = new com.zrlog.updater.spi.UpdateContext.Builder();
        context.executable = new java.io.File("zrlog");
        context.docker = new com.zrlog.updater.spi.DockerConnection("http://localhost", "test_token_123456789012345678901234", "https://dl.zrlog.com", "docker.json");
        context.http = new com.zrlog.updater.spi.UpdateHttp() {
            public String read(java.net.URI uri) { throw new AssertionError("No network in Native smoke"); }
            public int download(java.net.URI uri, java.io.File file) { throw new AssertionError("No download in Native smoke"); }
            public java.net.http.HttpResponse<String> send(java.net.http.HttpRequest request) { throw new AssertionError("No request in Native smoke"); }
        };
        for (com.zrlog.common.UpdaterTypeEnum type : com.zrlog.common.UpdaterTypeEnum.values()) {
            if (com.zrlog.updater.spi.UpdateProviders.get(type).type() != type) throw new AssertionError("Missing " + type);
            if (com.zrlog.updater.spi.UpdateProviders.get(type).create(context.build()).getType() != type) throw new AssertionError("Cannot construct " + type);
        }
        for (String resource : com.zrlog.updater.spi.UpdateProviders.messageResources("zh_CN")) {
            try (java.io.InputStream input = CatalogSmoke.class.getResourceAsStream(resource)) {
                if (input == null) throw new AssertionError("Missing " + resource);
                java.util.Properties messages = new java.util.Properties(); messages.load(input);
                if (messages.isEmpty()) throw new AssertionError("Empty " + resource);
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        Gson gson = new Gson();
        com.zrlog.updater.spi.DebUpgradeStatus deb = gson.fromJson("{\"stage\":\"complete\",\"requestedAt\":1}", com.zrlog.updater.spi.DebUpgradeStatus.class);
        if (!"complete".equals(deb.stage) || deb.requestedAt != 1) throw new AssertionError("deb status is not readable");
        String aws = gson.toJson(new com.zrlog.common.updater.aws.AwsLambdaUpdateCodeRequest("bucket", "key"));
        if (!aws.contains("bucket")) throw new AssertionError("AWS request is not serializable");
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
