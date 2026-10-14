package com.zrlog.updater.docker;

import com.zrlog.common.updater.UpdateVersionHandler;

public final class ManualUpdateVersionHandler implements UpdateVersionHandler {
    private final String message;
    private boolean finish;
    public ManualUpdateVersionHandler(String message) { this.message = message; }
    public String getMessage() { return message; }
    public boolean isFinish() { return finish; }
    public void doHandle() { finish = true; }
}
