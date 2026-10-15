package com.zrlog.updater.spi;

import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

public interface DockerControl {
    DockerUpdaterProtocol.Capabilities capabilities();
    DockerUpdaterProtocol.SettingsResponse settings(String channel);
    DockerUpdaterProtocol.Settings saveSettings(String repository, String channel);
    DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request);
    DockerUpdaterProtocol.Job status(String requestId);
    static void requestId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{15,79}"))
            throw new IllegalArgumentException("Invalid updater request ID");
    }
    class RequestException extends IllegalStateException {
        private final int status;
        private final String code;
        public RequestException(int status) {
            this(status, status == 401 || status == 403 ? "updater_unauthorized" : "updater_request_failed");
        }
        public RequestException(int status, String code) {
            super("Docker updater request failed (HTTP " + status + ")"); this.status = status; this.code = code;
        }
        public int getStatus() { return status; }
        public String getCode() { return code; }
    }
}
