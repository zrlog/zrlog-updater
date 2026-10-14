package com.zrlog.updater.faas;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class FaasProvider extends PackageUpdateProvider {
    public FaasProvider() { super(UpdaterTypeEnum.FAAS, "/i18n/updater/faas/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new HostedUpdater(context, type()); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new FaasUpdateVersionHandler(context, request.text, request.version, request.file, request.progress);
    }
}
