package com.zrlog.updater.zip;

import com.zrlog.updater.common.ArchiveUpdateVersionHandler;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;
import java.io.File;
import java.util.List;

public final class ZipUpdateVersionHandle extends ArchiveUpdateVersionHandler {
    public ZipUpdateVersionHandle(UpdateContext context, UpdateRequest request) { super(context, request); }
    protected String extractKey(String status) { return "upgrade.zip.status." + status; }
    protected void prepareFiles() {
        if (!"/".equals(File.separator)) return;
        for (String path : List.of("bin/start.sh", "bin/run.sh", "bin/start.bat")) {
            File file = new File(context.root, path);
            try { new ProcessBuilder("chmod", "a+x", file.toString()).start().waitFor(); }
            catch (Exception e) { publish("error", "error", "upgrade.zip.error.filePermissions", file.toString()); }
        }
    }
}
