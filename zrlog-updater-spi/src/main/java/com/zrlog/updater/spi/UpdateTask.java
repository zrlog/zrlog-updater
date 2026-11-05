package com.zrlog.updater.spi;

import com.zrlog.common.updater.UpgradeProgressEvent;
import java.util.Objects;

/** Public task snapshot, independent of the deployment protocol. */
public final class UpdateTask {
    public String id;
    public String version;
    public String buildId;
    public String channel;
    public String state;
    public String stage;
    public String message;
    public String detail;
    public boolean recoveryRequired;
    public boolean awaitingRestart;

    public boolean terminal() {
        return "succeeded".equals(state) || "failed".equals(state) || "interrupted".equals(state);
    }

    public boolean failed() {
        return "failed".equals(state) || "interrupted".equals(state);
    }

    public boolean matches(UpdateCommand command) {
        return Objects.equals(id, command.requestId) && Objects.equals(version, command.version)
                && Objects.equals(buildId, command.buildId) && Objects.equals(channel, command.channel);
    }

    public UpgradeProgressEvent.Data progress() {
        return UpgradeProgressEvent.data(stage, failed() ? "error" : "succeeded".equals(state)
                ? "complete" : "running", message, detail);
    }
}
