package com.zrlog.updater.nativeimage;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageUpdateProvider;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class NativeProvider extends PackageUpdateProvider {
    public NativeProvider() { super(UpdaterTypeEnum.NATIVE_IMAGE, "/i18n/updater/native_image/backend"); }
    public ApplicationUpdater create(UpdateContext context) { return new NativeImageUpdater(context); }
    public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
        return new NativeUpdateVersionHandler(context, request);
    }
}
