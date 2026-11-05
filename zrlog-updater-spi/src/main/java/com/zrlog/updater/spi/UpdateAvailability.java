package com.zrlog.updater.spi;

public final class UpdateAvailability {
    public boolean online;
    public String reason;

    public UpdateAvailability() { }
    public UpdateAvailability(boolean online, String reason) {
        this.online = online;
        this.reason = reason;
    }
}
