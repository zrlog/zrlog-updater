package com.zrlog.updater.docker;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class DockerProvider extends PackageUpdateProvider {
    public DockerProvider() { super(UpdaterTypeEnum.DOCKER, "/i18n/updater/docker/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new DockerImageUpdater(context); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new ManualUpdateVersionHandler(request.text.apply("upgrade.tips.docker"));
    }
    public java.io.File download(UpdateContext context, UpdateRequest request) {
        throw new UnsupportedOperationException("Docker pulls images through its service");
    }
}
