package com.zrlog.updater.docker;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class DockerProvider extends PackageUpdateProvider {
    public UpdateVersionHandler manualHandler(UpdateContext context, UpdateRequest request) {
        return handler(context, request);
    }
    public DockerProvider() { super(UpdaterTypeEnum.DOCKER, "/i18n/updater/docker/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new DockerImageUpdater(context); }
    public com.zrlog.updater.spi.UpdateOperations operations(UpdateContext context, ApplicationUpdater updater) {
        return new DockerUpdateOperations(context, updater instanceof com.zrlog.updater.spi.DockerControl
                ? (com.zrlog.updater.spi.DockerControl) updater : null);
    }
    public boolean onlineUpgradeSupported(UpdateContext context) {
        return operations(context, null).availability(java.util.function.Function.identity()).online;
    }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new ManualUpdateVersionHandler(request.text.apply("upgrade.tips.docker"));
    }
    public java.io.File download(UpdateContext context, UpdateRequest request) {
        throw new UnsupportedOperationException("Docker pulls images through its service");
    }
}
