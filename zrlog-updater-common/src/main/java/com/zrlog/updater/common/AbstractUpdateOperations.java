package com.zrlog.updater.common;

import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.updater.spi.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Shared observation and UI contracts; observing a task never submits or cancels it. */
public abstract class AbstractUpdateOperations implements UpdateOperations {
    public UpdateTaskMessages messages(Function<String, String> text) {
        UpdateTaskMessages result = new UpdateTaskMessages();
        result.restoring = text.apply("upgrade.task.restoring");
        result.retrySubmission = text.apply("upgrade.task.retrySubmission");
        result.unknown = text.apply("upgrade.task.unknown");
        result.failed = text.apply("upgrade.error.generic");
        result.storageUnavailable = text.apply("upgrade.task.storageUnavailable");
        return result;
    }

    public UpdateTask watch(String requestId, UpgradeProgressListener progress, Function<String, String> text) throws Exception {
        UpdateCommand.validateId(requestId);
        UpdateTask initial = status(requestId, text);
        if (initial == null) return null;
        UpdateTask task = initial;
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
        while (true) {
            if (task == null || !Objects.equals(initial.id, task.id)
                    || !Objects.equals(initial.version, task.version)
                    || !Objects.equals(initial.buildId, task.buildId)
                    || !Objects.equals(initial.channel, task.channel)) {
                throw new IllegalStateException(text.apply("upgrade.task.mismatch"));
            }
            progress.onProgress(UpgradeProgressEvent.EVENT, task.progress());
            if (task.terminal() || task.awaitingRestart) return task;
            if (System.nanoTime() >= deadline) throw new IllegalStateException(text.apply("upgrade.task.timeout"));
            awaitNextStatus();
            task = status(requestId, text);
        }
    }

    protected void awaitNextStatus() throws InterruptedException { Thread.sleep(1000); }

    protected static void channel(String channel) {
        if (!("preview".equals(channel) || "release".equals(channel))) throw new IllegalArgumentException("Invalid update channel");
    }
}
