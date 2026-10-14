package com.zrlog.updater.zip;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class ZipProvider extends PackageUpdateProvider {
    public ZipProvider() { super(UpdaterTypeEnum.ZIP, "/i18n/updater/zip/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new ZipUpdater(context); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new ZipUpdateVersionHandle(context, request);
    }
}
