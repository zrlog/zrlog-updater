package com.zrlog.updater.deb;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class DebProvider extends PackageUpdateProvider {
    public DebProvider() { super(UpdaterTypeEnum.DEB, "/i18n/updater/deb/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new DebPackageUpdater(context); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new DebUpdateVersionHandler(request);
    }
    public java.io.File download(UpdateContext context, UpdateRequest request) { return null; }
}
