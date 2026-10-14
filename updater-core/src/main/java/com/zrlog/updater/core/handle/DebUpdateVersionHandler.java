package com.zrlog.updater.core.handle;

import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.core.PackageFiles;
import com.zrlog.updater.spi.DebUpgradeStatus;
import com.zrlog.updater.spi.SystemPackageUpdater;
import com.zrlog.updater.spi.UpdateRequest;

public final class DebUpdateVersionHandler implements UpdateVersionHandler {
    private final UpdateRequest request;
    private String message = "";
    private boolean finish;
    public DebUpdateVersionHandler(UpdateRequest request) { this.request = request; }
    public String getMessage() { return message; }
    public boolean isFinish() { return finish; }
    public void doHandle() {
        try {
            SystemPackageUpdater updater = (SystemPackageUpdater) request.updater;
            long submitted = System.currentTimeMillis();
            message = request.text.apply("upgrade.deb.status.queued");
            PackageFiles.publish(request, "restart", "running", "upgrade.deb.status.queued", null);
            updater.request(request.version.getChannel() == null
                    ? (request.version.getVersion().contains("-SNAPSHOT") ? "preview" : "release") : request.version.getChannel());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
            long lastUpdated = 0;
            while (System.nanoTime() < deadline) {
                DebUpgradeStatus state = updater.status();
                if (state != null && state.requestedAt >= submitted - 1000 && state.updatedAt != lastUpdated) {
                    lastUpdated = state.updatedAt; message = request.text.apply(state.message);
                    PackageFiles.publish(request, state.stage, state.status, state.message, null);
                    if ("complete".equals(state.stage) || "error".equals(state.stage)) {
                        finish = "complete".equals(state.stage); return;
                    }
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("deb updater did not report completion");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            message = request.text.apply("upgrade.error.generic") + " " + e.getMessage();
            try { PackageFiles.publish(request, "error", "error", "upgrade.error.generic", e.getMessage()); }
            catch (Exception progressError) { throw new IllegalStateException(progressError); }
        }
    }
}
