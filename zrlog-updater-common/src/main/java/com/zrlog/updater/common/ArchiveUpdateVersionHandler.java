package com.zrlog.updater.common;

import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;
import java.util.Objects;

/** Extraction and restart flow shared by archive deployments, with deployment-owned steps. */
public abstract class ArchiveUpdateVersionHandler implements UpdateVersionHandler {
    protected final UpdateContext context;
    protected final UpdateRequest request;
    private String message = "";
    private boolean finish;
    protected ArchiveUpdateVersionHandler(UpdateContext context, UpdateRequest request) {
        this.context = context; this.request = request;
    }
    protected abstract String extractKey(String status);
    protected void prepareFiles() throws Exception { }
    public final String getMessage() { return message; }
    public final boolean isFinish() { return finish; }
    public final void doHandle() {
        Objects.requireNonNull(request.updater);
        publish("unzip", "running", extractKey("unzipping"), request.file.getName());
        try { ArchiveFiles.unZip(request.file.toString(), request.updater.getUnzipPath()); }
        catch (Exception e) { publish("error", "error", "upgrade.error.unzip", e.getMessage()); return; }
        try {
            prepareFiles();
            publish("unzip", "complete", extractKey("unzipped"), null);
            publish("restart", "running", "upgrade.status.restarting", null);
            if (finish) return;
            request.updater.restartProcessAsync(request.version);
            publish("restart", "complete", "upgrade.status.restartSubmitted", null);
            publish("complete", "complete", "upgrade.status.completed", null);
            finish = true;
        } catch (Exception e) { publish("error", "error", "upgrade.error.generic", e.getMessage()); }
        finally { request.file.delete(); }
    }
    protected final void publish(String stage, String status, String key, String detail) {
        message = request.text.apply(key) + (detail == null ? "" : " " + detail);
        try { PackageFiles.publish(request, stage, status, key, detail); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
