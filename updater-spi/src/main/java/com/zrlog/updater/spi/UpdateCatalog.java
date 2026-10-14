package com.zrlog.updater.spi;

import com.zrlog.common.vo.Version;

import java.net.URI;

/** Update metadata and package-source rules supplied by an independently assembled implementation. */
public interface UpdateCatalog {
    URI metadataUri(String origin, String filename, String channel);

    void validateDocker(Version version, String channel);

    String selectPackageSource(Version version, boolean war, String source);
}
