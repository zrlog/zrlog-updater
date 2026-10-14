package com.zrlog.common.updater;

import java.util.Objects;

public final class UpgradeProgressEvent {

    /**
     * Upgrade SSE uses one event name. The payload stage tells the client how to render or replace the line.
     */
    public static final String EVENT = "upgrade-progress";
    public static final String STAGE_DOWNLOAD = "download";
    public static final String STAGE_VALIDATE = "validate";
    public static final String STAGE_UPLOAD = "upload";
    public static final String STAGE_UPDATE = "update";
    public static final String STAGE_BACKUP = "backup";
    public static final String STAGE_MERGE = "merge";
    public static final String STAGE_UNZIP = "unzip";
    public static final String STAGE_RESTART = "restart";
    public static final String STAGE_COMPLETE = "complete";
    public static final String STAGE_MANUAL = "manual";
    public static final String STAGE_ERROR = "error";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETE = "complete";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_MANUAL = "manual";

    private UpgradeProgressEvent() {
    }

    public static Data data(String stage, String message) {
        return data(stage, STATUS_RUNNING, message, null);
    }

    public static Data data(String stage, String message, String detail) {
        return data(stage, STATUS_RUNNING, message, detail);
    }

    public static Data data(String stage, String status, String message, String detail) {
        return new Data(stage, status, Objects.toString(message, ""), detail);
    }

    public static class Data {

        private String stage;
        private String status;
        private String message;
        private String detail;

        public Data() {
        }

        public Data(String stage, String status, String message, String detail) {
            this.stage = stage;
            this.status = status;
            this.message = Objects.toString(message, "");
            setDetail(detail);
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = Objects.toString(message, "");
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = Objects.nonNull(detail) && !detail.isBlank() ? detail : null;
        }

    }
}
