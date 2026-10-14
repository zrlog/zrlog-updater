package com.zrlog.common.updater;

public interface UpdateVersionHandler {

    String getMessage();

    boolean isFinish();

    void doHandle();
}
