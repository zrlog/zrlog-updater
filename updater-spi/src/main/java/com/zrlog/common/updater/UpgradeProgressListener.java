package com.zrlog.common.updater;

public interface UpgradeProgressListener {

    UpgradeProgressListener NONE = (event, data) -> {
    };

    void onProgress(String event, UpgradeProgressEvent.Data data) throws Exception;
}
