package com.zrlog.updater.spi;

/** A pinned, idempotent upgrade request. Deployment selection and artifact URLs are server-owned. */
public final class UpdateCommand {
    public String requestId;
    public String version;
    public String buildId;
    public String channel;

    public void validate() {
        validateId(requestId);
        if (version == null || version.isBlank() || version.length() > 200
                || buildId == null || !buildId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || !("release".equals(channel) || "preview".equals(channel))) {
            throw new IllegalArgumentException("Invalid upgrade target");
        }
    }

    public static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{15,79}")) {
            throw new IllegalArgumentException("Invalid upgrade request ID");
        }
    }
}
