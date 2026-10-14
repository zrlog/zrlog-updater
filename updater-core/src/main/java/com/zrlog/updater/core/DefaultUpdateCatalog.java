package com.zrlog.updater.core;

import com.zrlog.common.vo.Version;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.spi.UpdateCatalog;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Existing ZrLog metadata rules, discovered through the standalone updater SPI. */
public final class DefaultUpdateCatalog implements UpdateCatalog {
    @Override
    public Version readVersion(com.zrlog.updater.spi.UpdateContext context, String channel) throws Exception {
        String content = context.http.read(metadataUri(context.metadataOrigin, context.manifestFilename, channel));
        if (content == null || content.trim().isEmpty()) throw new java.io.IOException("Configured update metadata source returned an empty document");
        Version version = new com.google.gson.Gson().fromJson(content.trim(), Version.class);
        if (version == null) throw new java.io.IOException("Invalid update manifest");
        if (context.docker != null) validateDocker(version, channel);
        version.setArtifactVersion(version.getVersion());
        version.setChannel(channel);
        java.util.Date date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX").parse(version.getReleaseDate());
        version.setBuildDate(date);
        version.setReleaseDate(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(date));
        return version;
    }
    @Override
    public URI metadataUri(String origin, String filename, String channel) {
        URI base = URI.create(origin.endsWith("/") ? origin : origin + "/");
        if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null || !base.normalize().equals(base)
                || !filename.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.json")
                || !("release".equals(channel) || "preview".equals(channel))) {
            throw new IllegalArgumentException("Invalid update metadata configuration in build.properties");
        }
        return base.resolve(channel + "/" + filename);
    }

    @Override
    public void validateDocker(Version version, String channel) {
        if (version == null || version.getSchemaVersion() != 1 || !"docker-image".equals(version.getPackageType())
                || !channel.equals(version.getChannel()) || version.getVersion() == null
                || !version.getVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")
                || version.getSourceCommit() == null || !version.getSourceCommit().matches("[0-9a-f]{40}")
                || version.getBuildId() == null || !version.getBuildId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || ("release".equals(channel) && (!version.getBuildId().matches("[0-9a-f]{7,40}")
                    || !version.getSourceCommit().startsWith(version.getBuildId())))
                || version.getImages() == null || version.getImages().isEmpty() || version.getImages().size() > 32) {
            throw new IllegalArgumentException("Invalid Docker update manifest");
        }
        OffsetDateTime.parse(version.getReleaseDate(), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssXXX")
                .withResolverStyle(ResolverStyle.STRICT));
        Set<String> targets = new HashSet<>();
        for (DockerUpdaterProtocol.Image image : version.getImages()) {
            if (image == null || image.repository == null
                    || !image.repository.matches("[a-z0-9][a-z0-9.:-]*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)+")
                    || image.repository.length() > 255 || image.digest == null || !image.digest.matches("sha256:[0-9a-f]{64}")
                    || image.tag == null || !image.tag.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")
                    || image.platforms == null || image.platforms.isEmpty() || image.platforms.size() > 16) {
                throw new IllegalArgumentException("Invalid Docker image entry");
            }
            String repository = image.repository;
            String first = repository.substring(0, repository.indexOf('/'));
            if (!first.contains(".") && !first.contains(":") && !first.equals("localhost")) { repository = "docker.io/" + repository; }
            repository = repository.replaceFirst("^index\\.docker\\.io/", "docker.io/");
            for (String platform : image.platforms) {
                if (platform == null || !platform.matches("[a-z0-9]+/[a-z0-9]+(?:/[a-z0-9]+)?")
                        || !targets.add(repository + "/" + platform.replaceFirst("^linux/arm64/v8$", "linux/arm64"))) {
                    throw new IllegalArgumentException("Ambiguous Docker platform");
                }
            }
        }
    }
    @Override
    public String selectPackageSource(Version version, boolean war, String source) {
        String selected = source == null ? "dl" : source;
        if (!"dl".equals(selected) && !"github".equals(selected)) {
            throw new IllegalArgumentException("Invalid upgrade package source");
        }
        List<Version.DownloadSource> locations = war ? version.getWarSources() : version.getZipSources();
        if (locations == null || locations.isEmpty()) {
            if ("dl".equals(selected)) { return war ? version.getWarDownloadUrl() : version.getZipDownloadUrl(); }
            throw new IllegalStateException("The selected package source is not published in this manifest");
        }
        String checksum = war ? version.getWarSha256() : version.getZipSha256();
        long size = war ? version.getWarFileSize() : version.getZipFileSize();
        if (checksum == null || !checksum.matches("[0-9a-fA-F]{64}") || size <= 0) {
            throw new IllegalStateException("Package sources require SHA-256 and file size");
        }
        Version.DownloadSource match = null;
        for (Version.DownloadSource location : locations) {
            if (location != null && selected.equals(location.id)) {
                if (match != null) { throw new IllegalArgumentException("Ambiguous package source"); }
                match = location;
            }
        }
        if (match == null || match.url == null) {
            throw new IllegalStateException("The selected package source is not published in this manifest");
        }
        URI uri = URI.create(match.url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("Invalid package download URL");
        }
        return uri.toString();
    }
}
