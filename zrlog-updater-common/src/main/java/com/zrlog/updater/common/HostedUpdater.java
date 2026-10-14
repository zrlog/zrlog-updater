package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import java.io.File;

/** Deployment-managed runtimes do not replace the host executable in process. */
public class HostedUpdater implements ApplicationUpdater {
    protected final UpdateContext context;
    private final UpdaterTypeEnum type;
    public HostedUpdater(UpdateContext context, UpdaterTypeEnum type) { this.context = context; this.type = type; }
    public void restartProcessAsync(Version version) { throw new UnsupportedOperationException("Deployment manages restart"); }
    public File getUpdateTempPath() { return context.updateTemp; }
    public String getUnzipPath() { return context.root.toString(); }
    public File execFile() { return context.executable; }
    public UpdaterTypeEnum getType() { return type; }
}
