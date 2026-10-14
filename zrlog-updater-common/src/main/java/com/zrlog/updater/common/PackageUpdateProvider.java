package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateProvider;
import com.zrlog.updater.spi.UpdateRequest;
import java.io.File;
import java.util.List;

/** Shared package operations; each implementation owns its registration and messages. */
public abstract class PackageUpdateProvider implements UpdateProvider {
    private final UpdaterTypeEnum type;
    private final String messageBundle;
    protected PackageUpdateProvider(UpdaterTypeEnum type, String messageBundle) {
        this.type = type;
        this.messageBundle = messageBundle;
    }
    public final UpdaterTypeEnum type() { return type; }
    public final List<String> messageBundles() {
        return List.of("/i18n/updater/common/backend", messageBundle);
    }
    public File download(UpdateContext context, UpdateRequest request) throws Exception {
        return PackageFiles.download(context, request);
    }
    public final boolean invalidPackage(File file, long size, String sha, String md5) {
        return PackageFiles.invalid(file, size, sha, md5);
    }
    public final String checksum(File file, String algorithm) throws Exception {
        return PackageFiles.checksum(file, algorithm);
    }
}
