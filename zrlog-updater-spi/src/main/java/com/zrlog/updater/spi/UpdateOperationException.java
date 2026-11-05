package com.zrlog.updater.spi;

/** A public, localized failure without private transport diagnostics. */
public final class UpdateOperationException extends IllegalStateException {
    private final String code;
    private final int status;
    public UpdateOperationException(int status, String code, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}
