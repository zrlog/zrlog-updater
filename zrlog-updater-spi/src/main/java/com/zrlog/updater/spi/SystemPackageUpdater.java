package com.zrlog.updater.spi;

public interface SystemPackageUpdater extends ApplicationUpdater {
    boolean available();
    void request(String channel) throws Exception;
    DebUpgradeStatus status() throws Exception;
    int install(String channel, boolean json) throws Exception;
}
