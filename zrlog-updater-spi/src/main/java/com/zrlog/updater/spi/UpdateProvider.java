package com.zrlog.updater.spi;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import java.io.File;

public interface UpdateProvider {
    UpdaterTypeEnum type();
    ApplicationUpdater create(UpdateContext context);
    default UpdateOperations operations(UpdateContext context, ApplicationUpdater updater) {
        throw new UnsupportedOperationException("Upgrade operations are not supplied for " + type());
    }
    UpdateVersionHandler handler(UpdateContext context, UpdateRequest request);
    default boolean onlineUpgradeSupported(UpdateContext context) { return true; }
    default UpdateVersionHandler manualHandler(UpdateContext context, UpdateRequest request) {
        return new UpdateVersionHandler() {
            public void doHandle() { }
            public boolean isFinish() { return false; }
            public String getMessage() { return request.text.apply("upgrade.error.unsupportedRuntime"); }
        };
    }
    File download(UpdateContext context, UpdateRequest request) throws Exception;
    boolean invalidPackage(File file, long length, String sha256, String md5);
    String checksum(File file, String algorithm) throws Exception;
    java.util.List<String> messageBundles();
}
