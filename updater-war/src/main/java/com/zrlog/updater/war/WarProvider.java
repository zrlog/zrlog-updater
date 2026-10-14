package com.zrlog.updater.war;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class WarProvider extends PackageUpdateProvider {
    public WarProvider() { super(UpdaterTypeEnum.WAR, "/i18n/updater/war/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new WarUpdater(context); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new WarUpdateVersionHandle(request.file, request.text, request.key, request.version, request.progress, request.updater);
    }
}
