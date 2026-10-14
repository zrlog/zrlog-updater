package com.zrlog.updater.spi;

public final class DockerConnection {
    public final String url, token, metadataOrigin, manifestFilename;
    public DockerConnection(String url, String token, String origin, String filename) {
        this.url = url; this.token = token; metadataOrigin = origin; manifestFilename = filename;
    }
}
