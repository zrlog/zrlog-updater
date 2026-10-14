package com.zrlog.updater.spi;

import java.util.Objects;

/** A discovered updater and the resolved host context used to construct it. */
public final class UpdateSession {
    public final UpdateContext context;
    public final ApplicationUpdater updater;

    public UpdateSession(UpdateContext context, ApplicationUpdater updater) {
        this.context = Objects.requireNonNull(context);
        this.updater = Objects.requireNonNull(updater);
    }
}
