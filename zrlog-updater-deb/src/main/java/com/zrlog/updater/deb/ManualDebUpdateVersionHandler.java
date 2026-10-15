package com.zrlog.updater.deb;

import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.spi.UpdateRequest;

/** Guidance for service installations that cannot submit an online package request. */
final class ManualDebUpdateVersionHandler implements UpdateVersionHandler {
    private final UpdateRequest request;
    private String message;
    private boolean finish;

    ManualDebUpdateVersionHandler(UpdateRequest request) { this.request = request; }
    public String getMessage() { return message; }
    public boolean isFinish() { return finish; }
    public void doHandle() {
        message = request.text.apply("upgrade.tips.systemService");
        if (request.version != null && request.version.getVersion().contains("-SNAPSHOT")) {
            message = message.replace("/etc/init.d/zrlog upgrade", "/etc/init.d/zrlog upgrade-preview");
        }
        finish = true;
    }
}
