package com.zrlog.updater.spi;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import java.io.File;

/** Execution contract; paths and application lifecycle are supplied by the host. */
public interface ApplicationUpdater {
    void restartProcessAsync(Version version);
    File getUpdateTempPath();
    default String buildUpgradeFile(String file, String key) throws Exception { return null; }
    default String backup() throws Exception { return ""; }
    String getUnzipPath();
    File execFile();
    UpdaterTypeEnum getType();
}
