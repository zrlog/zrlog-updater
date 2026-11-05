package com.zrlog.updater.common;

import com.google.gson.Gson;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.*;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Function;

/** Package execution and its durable receipt belong to updater, including across host restarts. */
public final class PackageUpdateOperations extends AbstractUpdateOperations {
    private static final Gson JSON = new Gson();
    private final UpdateContext context;
    private final ApplicationUpdater updater;
    private final UpdateProvider provider;
    private final Path directory;

    public PackageUpdateOperations(UpdateContext context, ApplicationUpdater updater, UpdateProvider provider) {
        UpdateContext.Builder packageContext = new UpdateContext.Builder(context);
        packageContext.docker = null;
        this.context = packageContext.build();
        this.updater = updater;
        this.provider = provider;
        // Package replacement may delete updateTemp, so receipts must live beside it.
        this.directory = context.updateTemp.toPath().resolveSibling(context.updateTemp.getName() + "-tasks");
    }

    public UpdateAvailability availability(Function<String, String> text) {
        if (updater == null) return new UpdateAvailability(false, text.apply("upgrade.error.unsupportedRuntime"));
        boolean available = updater != null && provider.onlineUpgradeSupported(context);
        String reason = null;
        if (!available) {
            reason = text.apply("upgrade.error.unsupportedRuntime");
        }
        return new UpdateAvailability(available, reason);
    }

    public UpdateTask submit(UpdateCommand command, UpgradeProgressListener progress, Function<String, String> text) throws Exception {
        command.validate();
        Record previous = read(command.requestId);
        if (previous != null) return existing(previous, command, text);
        UpdateAvailability availability = availability(text);
        if (!availability.online) throw new IllegalStateException(availability.reason);
        Files.createDirectories(directory);
        try (FileChannel lockFile = FileChannel.open(directory.resolve("execution.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = lockFile.tryLock(); }
            catch (OverlappingFileLockException error) { lock = null; }
            if (lock == null) {
                previous = read(command.requestId);
                if (previous != null) return existing(previous, command, text);
                throw new IllegalStateException(text.apply("upgrade.task.busy"));
            }
            try (FileLock acquired = lock) {
                previous = read(command.requestId);
                if (previous != null) return existing(previous, command, text);
                Version target = new DefaultUpdateCatalog().readVersion(context, command.channel);
                String artifactVersion = target.getArtifactVersion() == null ? target.getVersion() : target.getArtifactVersion();
                if (!Objects.equals(command.version, artifactVersion) || !Objects.equals(command.buildId, target.getBuildId()))
                    throw new IllegalArgumentException(text.apply("upgrade.task.targetChanged"));
                Record record = new Record();
                record.task = new UpdateTask();
                record.task.id = command.requestId;
                record.task.version = command.version;
                record.task.buildId = command.buildId;
                record.task.channel = command.channel;
                record.task.state = "running";
                record.task.stage = "download";
                record.task.message = "upgrade.status.downloading";
                save(record); // Durable identity before download, backup, replacement or restart.
                UpgradeProgressListener durableProgress = (event, data) -> {
                    if (data instanceof UpgradeProgressEvent.Data) {
                        UpgradeProgressEvent.Data value = (UpgradeProgressEvent.Data) data;
                        record.task.stage = value.getStage();
                        record.task.message = value.getMessage();
                        record.task.detail = value.getDetail();
                        if ("restart".equals(value.getStage())) record.task.awaitingRestart = true;
                        if ("error".equals(value.getStatus())) record.task.state = "failed";
                        save(record);
                        // The execution outlives a disconnected observer. Persistence errors still stop it.
                        try { progress.onProgress(event, display(record.task, text).progress()); }
                        catch (Exception disconnected) { /* Observation does not own the operation. */ }
                    }
                };
                try {
                    UpdateRequest input = new UpdateRequest(null, target, command.requestId, updater, durableProgress, text);
                    File file = provider.download(context, input);
                    UpdateVersionHandler handler = provider.handler(context,
                            new UpdateRequest(file, target, command.requestId, updater, durableProgress, text));
                    handler.doHandle();
                    if (handler.isFinish()) {
                        record.task.awaitingRestart = true;
                        record.task.stage = "restart";
                        record.task.message = "upgrade.task.awaitingRestart";
                    } else {
                        record.task.state = "failed";
                        record.task.recoveryRequired = record.task.awaitingRestart;
                        if (!"error".equals(record.task.stage)) record.task.message = "upgrade.error.generic";
                    }
                } catch (Exception error) {
                    record.task.state = "failed";
                    record.task.recoveryRequired = record.task.awaitingRestart;
                    record.task.message = "upgrade.error.generic";
                    record.task.detail = null;
                }
                save(record);
                return display(record.task, text);
            }
        }
    }

    private UpdateTask existing(Record record, UpdateCommand command, Function<String, String> text) throws Exception {
        if (!record.task.matches(command)) throw new IllegalArgumentException(text.apply("upgrade.task.mismatch"));
        return status(command.requestId, text);
    }

    public UpdateTask status(String requestId, Function<String, String> text) throws Exception {
        UpdateCommand.validateId(requestId);
        Record record = read(requestId);
        if (record == null) return null;
        if (record.task.terminal()) return display(record.task, text);
        try (FileChannel file = FileChannel.open(directory.resolve("execution.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock acquired;
            try { acquired = file.tryLock(); }
            catch (OverlappingFileLockException executing) { acquired = null; }
            if (acquired == null) return display(record.task, text);
            try (FileLock lock = acquired) {
                // Re-read under the execution lock: the writer may have just completed.
                record = read(requestId);
                if (!record.task.terminal()) {
                    if (Objects.equals(context.currentBuildId.get(), record.task.buildId)) {
                        record.task.state = "succeeded";
                        record.task.stage = "complete";
                        record.task.message = "upgrade.task.succeeded";
                        record.task.awaitingRestart = false;
                        record.task.detail = null;
                        save(record);
                    } else if (!record.task.awaitingRestart) {
                        record.task.state = "interrupted";
                        record.task.stage = "error";
                        record.task.recoveryRequired = true;
                        record.task.message = "upgrade.task.interrupted";
                        record.task.detail = null;
                        save(record);
                    }
                }
            }
        }
        return display(record.task, text);
    }

    private Record read(String id) throws Exception {
        UpdateCommand.validateId(id);
        Path file = directory.resolve(id + ".json");
        if (!Files.exists(file)) return null;
        Record record = JSON.fromJson(Files.readString(file), Record.class);
        if (record == null || record.task == null || !id.equals(record.task.id)) throw new IllegalStateException("Invalid upgrade task record");
        return record;
    }

    private void save(Record record) throws Exception {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".task-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(record));
            Files.move(temporary, directory.resolve(record.task.id + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static UpdateTask display(UpdateTask value, Function<String, String> text) {
        UpdateTask task = JSON.fromJson(JSON.toJson(value), UpdateTask.class);
        task.message = text.apply(value.message);
        if (task.failed()) task.stage = "error";
        return task;
    }

    public UpdateSourceSettings settings(String channel, Function<String, String> text) {
        channel(channel);
        UpdateSourceSettings value = new UpdateSourceSettings();
        value.channel = channel;
        value.source = context.packageSource == null ? "dl" : context.packageSource;
        value.label = text.apply("upgrade.source.label");
        value.description = text.apply("upgrade.source.description");
        value.options.add(new UpdateSourceSettings.Option("dl", text.apply("upgrade.source.dl")));
        value.options.add(new UpdateSourceSettings.Option("github", text.apply("upgrade.source.github")));
        return value;
    }

    public void saveSettings(String source, String channel, Function<String, String> text) {
        channel(channel);
        if (!("dl".equals(source) || "github".equals(source))) throw new IllegalArgumentException("Invalid update source");
        // Package source persistence is a host setting; the provider validates its domain.
    }

    static final class Record {
        UpdateTask task;
    }
}
