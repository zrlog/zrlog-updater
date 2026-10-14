package com.zrlog.updater.common;

import com.google.gson.Gson;
import com.zrlog.common.vo.Version;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.updater.spi.UpdateCatalog;
import com.zrlog.updater.spi.UpdateCatalogs;

/** Entry point for assembled JAR and Native Image verification, never shipped as production code. */
public final class CatalogSmoke {
    public static void main(String[] args) throws Exception {
        UpdateCatalog catalog = UpdateCatalogs.getInstance();
        com.zrlog.updater.spi.UpdateContext.Builder context = new com.zrlog.updater.spi.UpdateContext.Builder();
        context.executable = new java.io.File("zrlog");
        context.docker = new com.zrlog.updater.spi.DockerConnection("http://localhost", "test_token_123456789012345678901234", "https://dl.zrlog.com", "docker.json");
        context.http = new com.zrlog.updater.spi.UpdateHttp() {
            public String read(java.net.URI uri) { throw new AssertionError("No network in Native smoke"); }
            public int download(java.net.URI uri, java.io.File file) { throw new AssertionError("No download in Native smoke"); }
            public java.net.http.HttpResponse<String> send(java.net.http.HttpRequest request) { throw new AssertionError("No request in Native smoke"); }
        };
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("updater-factory-smoke");
        java.io.File launcher = home.resolve("zrlog-starter.jar").toFile();
        try {
            java.util.jar.Manifest manifest = new java.util.jar.Manifest();
            manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "com.zrlog.web.Application");
            try (java.util.jar.JarOutputStream ignored = new java.util.jar.JarOutputStream(
                    new java.io.FileOutputStream(launcher), manifest)) { }
            for (com.zrlog.common.UpdaterTypeEnum type : com.zrlog.common.UpdaterTypeEnum.values()) {
                com.zrlog.updater.spi.ApplicationUpdater updater = UpdaterUtils.getUpdater(type, context.executable,
                        launcher.toString(), home.toFile(), (selected, executable) -> {
                            com.zrlog.updater.spi.UpdateContext.Builder adapted = new com.zrlog.updater.spi.UpdateContext.Builder(context.build());
                            adapted.executable = executable;
                            return com.zrlog.updater.spi.UpdateProviders.get(selected).create(adapted.build());
                        });
                if (updater == null || updater.getType() != type) throw new AssertionError("Cannot construct " + type);
                if (type == com.zrlog.common.UpdaterTypeEnum.ZIP && !launcher.getCanonicalFile().equals(updater.execFile())) {
                    throw new AssertionError("ZIP factory did not select the launcher");
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(launcher.toPath());
            java.nio.file.Files.deleteIfExists(home);
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
        String aws;
        try {
            Class<?> request = Class.forName("com.zrlog.common.updater.aws.AwsLambdaUpdateCodeRequest");
            aws = gson.toJson(request.getConstructor(String.class, String.class).newInstance("bucket", "key"));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
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
