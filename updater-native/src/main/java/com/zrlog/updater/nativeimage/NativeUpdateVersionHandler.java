package com.zrlog.updater.nativeimage;

import com.zrlog.updater.common.ArchiveUpdateVersionHandler;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;

public final class NativeUpdateVersionHandler extends ArchiveUpdateVersionHandler {
    public NativeUpdateVersionHandler(UpdateContext context, UpdateRequest request) { super(context, request); }
    protected String extractKey(String status) { return "upgrade.native.status." + status; }
}
