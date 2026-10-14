package com.zrlog.updater.spi;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import java.io.File;

public interface UpdateProvider {
    UpdaterTypeEnum type();
    ApplicationUpdater create(UpdateContext context);
    UpdateVersionHandler handler(UpdateContext context, UpdateRequest request);
    File download(UpdateContext context, UpdateRequest request) throws Exception;
    boolean invalidPackage(File file, long length, String sha256, String md5);
    String checksum(File file, String algorithm) throws Exception;
    java.util.List<String> messageBundles();
}
