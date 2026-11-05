package com.zrlog.updater.docker;

import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.common.AbstractUpdateOperations;
import com.zrlog.updater.spi.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/** Owns all Docker protocol interpretation; callers see only the common upgrade contract. */
public class DockerUpdateOperations extends AbstractUpdateOperations {
    private final UpdateContext context;
    private final DockerControl suppliedClient;

    public DockerUpdateOperations(UpdateContext context, DockerControl client) {
        this.context = context;
        this.suppliedClient = client;
    }

    private DockerControl client() {
        return suppliedClient == null ? new DockerImageUpdater(context) : suppliedClient;
    }

    public UpdateAvailability availability(Function<String, String> text) {
        if (suppliedClient == null && (context.docker == null || context.docker.url == null || context.docker.token == null))
            return new UpdateAvailability(false, text.apply("upgrade.tips.docker"));
        try {
            client().capabilities();
            return new UpdateAvailability(true, null);
        } catch (RuntimeException error) {
            return new UpdateAvailability(false, text.apply("upgrade.docker.updaterUnavailable"));
        }
    }

    @Override
    public UpdateTaskMessages messages(Function<String, String> text) {
        UpdateTaskMessages messages = super.messages(text);
        messages.failed = text.apply("upgrade.docker.failed");
        return messages;
    }

    public UpdateTask submit(UpdateCommand command, UpgradeProgressListener progress, Function<String, String> text) {
        command.validate();
        DockerUpdaterProtocol.UpgradeRequest request = new DockerUpdaterProtocol.UpgradeRequest();
        request.requestId = command.requestId;
        request.version = command.version;
        request.buildId = command.buildId;
        request.channel = command.channel;
        try {
            UpdateTask task = snapshot(client().submit(request), text);
            if (task == null || !task.matches(command)) throw new UpdateOperationException(502, "task_mismatch", text.apply("upgrade.task.mismatch"));
            return task;
        } catch (DockerControl.RequestException error) { throw translateTask(error, text); }
    }

    public UpdateTask status(String requestId, Function<String, String> text) {
        UpdateCommand.validateId(requestId);
        try {
            UpdateTask task = snapshot(client().status(requestId), text);
            if (task != null && !requestId.equals(task.id)) throw new UpdateOperationException(502, "task_mismatch", text.apply("upgrade.task.mismatch"));
            return task;
        } catch (DockerControl.RequestException error) {
            if (error.getStatus() == 404) return null;
            throw translateTask(error, text);
        }
    }

    private UpdateTask snapshot(DockerUpdaterProtocol.Job job, Function<String, String> text) {
        if (job == null) return null;
        UpdateTask task = new UpdateTask();
        task.id = job.id;
        task.version = job.version;
        task.buildId = job.buildId;
        task.channel = job.channel;
        task.recoveryRequired = job.recoveryRequired;
        switch (job.phase == null ? "" : job.phase) {
            case "queued": task.state = "queued"; task.stage = "download"; break;
            case "pulling": task.state = "running"; task.stage = "download"; break;
            case "replacing":
            case "checking": task.state = "running"; task.stage = "restart"; break;
            case "succeeded": task.state = "succeeded"; task.stage = "complete"; break;
            case "failed":
            case "interrupted": task.state = job.phase; task.stage = "error"; break;
            default: throw new UpdateOperationException(502, "invalid_task", text.apply("upgrade.task.unknown"));
        }
        task.message = text.apply("upgrade.docker." + job.phase);
        if (task.failed() && task.recoveryRequired) task.detail = text.apply("upgrade.docker.recovery");
        return task;
    }

    public boolean managesSourceSettings() { return true; }

    public UpdateSourceSettings settings(String channel, Function<String, String> text) {
        channel(channel);
        UpdateSourceSettings result = new UpdateSourceSettings();
        result.managedByUpdater = true;
        result.channel = channel;
        result.label = text.apply("upgrade.docker.source.label");
        result.description = text.apply("upgrade.docker.source.description");
        try {
            DockerUpdaterProtocol.SettingsResponse settings = client().settings(channel);
            result.source = settings.settings.repository;
            result.channel = settings.settings.channel;
            result.options.add(new UpdateSourceSettings.Option("", text.apply("upgrade.docker.source.current")
                    + (settings.currentRepository == null ? "" : " (" + settings.currentRepository + ")")));
            Set<String> repositories = new LinkedHashSet<>();
            for (DockerUpdaterProtocol.Image image : settings.images) repositories.add(image.repository);
            for (String repository : repositories) result.options.add(new UpdateSourceSettings.Option(repository, repository));
        } catch (RuntimeException error) {
            UpdateOperationException failure = translate(error, text);
            result.errorCode = failure.getCode();
            result.errorMessage = failure.getMessage();
        }
        return result;
    }

    public void saveSettings(String source, String channel, Function<String, String> text) {
        channel(channel);
        if (source == null || source.length() > 255) throw new IllegalArgumentException("Invalid update source");
        try {
            DockerControl client = client();
            DockerUpdaterProtocol.SettingsResponse settings = client.settings(channel);
            if (!source.isEmpty() && settings.images.stream().noneMatch(image -> source.equals(image.repository)))
                throw new IllegalArgumentException(text.apply("upgrade.source.invalid"));
            client.saveSettings(source, channel);
        } catch (DockerControl.RequestException error) { throw translate(error, text); }
    }

    private static UpdateOperationException translateTask(DockerControl.RequestException error, Function<String, String> text) {
        String key;
        switch (error.getCode()) {
            case "request_id_conflict": key = "upgrade.task.mismatch"; break;
            case "upgrade_in_progress": key = "upgrade.task.busy"; break;
            case "version_changed": key = "upgrade.task.targetChanged"; break;
            case "history_full": key = "upgrade.docker.historyFull"; break;
            default: key = "upgrade.docker.updaterUnavailable";
        }
        return new UpdateOperationException(error.getStatus(), error.getCode(), text.apply(key));
    }

    private static UpdateOperationException translate(RuntimeException error, Function<String, String> text) {
        String code = error instanceof DockerControl.RequestException ? ((DockerControl.RequestException) error).getCode() : "updater_not_configured";
        int status = error instanceof DockerControl.RequestException ? ((DockerControl.RequestException) error).getStatus() : 503;
        String key;
        switch (code) {
            case "metadata_unavailable":
            case "metadata_not_found": key = "metadataUnavailable"; break;
            case "updater_unavailable": key = "serviceUnavailable"; break;
            case "updater_unauthorized": key = "unauthorized"; break;
            case "updater_not_configured":
            case "updater_configuration_mismatch": key = "configurationInvalid"; break;
            default: key = "unavailable";
        }
        return new UpdateOperationException(status, code, text.apply("upgrade.docker.source.error." + key));
    }

    public void restoreSettings(UpdateSourceSettings previous, Function<String, String> text) {
        try { client().saveSettings(previous.source, previous.channel); }
        catch (DockerControl.RequestException error) { throw translate(error, text); }
    }
}
