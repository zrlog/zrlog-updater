package com.zrlog.updater.spi;

import com.zrlog.common.updater.UpgradeProgressListener;
import java.util.function.Function;

/** The application-facing upgrade lifecycle. Only providers interpret deployment protocols. */
public interface UpdateOperations {
    UpdateAvailability availability(Function<String, String> text);
    UpdateTaskMessages messages(Function<String, String> text);
    UpdateTask submit(UpdateCommand command, UpgradeProgressListener progress, Function<String, String> text) throws Exception;
    UpdateTask status(String requestId, Function<String, String> text) throws Exception;
    UpdateTask watch(String requestId, UpgradeProgressListener progress, Function<String, String> text) throws Exception;
    default boolean managesSourceSettings() { return false; }
    UpdateSourceSettings settings(String channel, Function<String, String> text);
    void saveSettings(String source, String channel, Function<String, String> text);
    /** Restores a trusted snapshot if the host's settings transaction fails after the provider write. */
    default void restoreSettings(UpdateSourceSettings previous, Function<String, String> text) {
        saveSettings(previous.source, previous.channel, text);
    }
}
