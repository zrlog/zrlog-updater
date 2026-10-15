package com.zrlog.updater.spi;

import com.zrlog.common.vo.Version;

import java.net.URI;

/** Update metadata and package-source rules supplied by an independently assembled implementation. */
public interface UpdateCatalog {
    URI metadataUri(String origin, String filename, String channel);

    void validateDocker(Version version, String channel);

    String selectPackageSource(Version version, boolean war, String source);
    Version readVersion(UpdateContext context, String channel) throws Exception;

    /** Selects a published channel; the host supplies comparison with its running version. */
    default com.zrlog.common.updater.UpdateVersionCheckResult checkVersion(UpdateContext context,
            boolean preview, java.util.function.Predicate<Version> newer) throws Exception {
        throw new UnsupportedOperationException("Version checking is not supplied by this catalog");
    }
}
