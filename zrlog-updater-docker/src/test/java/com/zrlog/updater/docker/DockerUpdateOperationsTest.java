package com.zrlog.updater.docker;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.spi.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.junit.Test;

public class DockerUpdateOperationsTest {
    private static UpdateCommand command() {
        UpdateCommand command = new UpdateCommand();
        command.requestId = "browser-request-1234";
        command.version = "4.0.2-SNAPSHOT";
        command.buildId = "build-1234567";
        command.channel = "preview";
        return command;
    }

    private static DockerUpdaterProtocol.Job job(String phase) {
        DockerUpdaterProtocol.Job job = new DockerUpdaterProtocol.Job();
        UpdateCommand command = command();
        job.id = command.requestId; job.version = command.version;
        job.buildId = command.buildId; job.channel = command.channel; job.phase = phase;
        job.message = "private engine diagnostic";
        job.digest = "private-digest";
        return job;
    }

    private static Function<String, String> text(String locale) throws IOException {
        Properties properties = new Properties();
        for (String module : List.of("common", "docker")) {
            try (var input = DockerUpdateOperationsTest.class.getResourceAsStream("/i18n/updater/" + module + "/backend_" + locale + ".properties")) {
                properties.load(input);
            }
        }
        return key -> properties.getProperty(key, key);
    }

    private static DockerUpdateOperations operations(Client client) {
        return new DockerUpdateOperations(new UpdateContext.Builder().build(), client) {
            protected void awaitNextStatus() { }
        };
    }

    @Test
    public void publicTasksAndProgressHideDockerProtocolAndUseProviderMessages() throws Exception {
        Client client = new Client(job("queued"), job("pulling"), job("replacing"), job("checking"), job("succeeded"));
        var operations = operations(client);
        List<UpgradeProgressEvent.Data> events = new ArrayList<>();
        UpdateTask task = operations.watch(command().requestId,
                (name, value) -> { assertEquals("upgrade-progress", name); events.add((UpgradeProgressEvent.Data) value); }, text("en_US"));
        assertEquals(List.of("download", "download", "restart", "restart", "complete"),
                events.stream().map(UpgradeProgressEvent.Data::getStage).collect(java.util.stream.Collectors.toList()));
        assertEquals("succeeded", task.state);
        assertEquals("Image update completed", task.message);
        String json = new Gson().toJson(task);
        assertFalse(json.contains("phase")); assertFalse(json.contains("digest")); assertFalse(json.contains("private"));
        assertEquals(0, client.submissions);
    }

    @Test
    public void submitPinsTheArtifactAndRejectsAReplyForAnotherTask() throws Exception {
        Client client = new Client(job("queued"));
        var operations = operations(client);
        UpdateTask accepted = operations.submit(command(), UpgradeProgressListener.NONE, text("en_US"));
        assertTrue(accepted.matches(command()));
        assertEquals("4.0.2-SNAPSHOT", client.submitted.version);
        assertEquals("preview", client.submitted.channel);
        assertFalse(accepted.terminal());
        client.jobs.get(0).buildId = "unrelated-build";
        assertThrows(UpdateOperationException.class,
                () -> operations.submit(command(), UpgradeProgressListener.NONE, text("en_US")));
    }

    @Test
    public void failuresKeepRecoveryAdviceAndCannotBecomeSuccess() throws Exception {
        for (String phase : List.of("failed", "interrupted")) {
            var job = job(phase); job.recoveryRequired = true;
            var task = operations(new Client(job)).status(command().requestId, text("zh_CN"));
            assertTrue(task.failed()); assertEquals("error", task.stage);
            assertTrue(task.detail.contains("恢复"));
            assertFalse(task.message.contains("private"));
        }
    }

    @Test
    public void disconnectStopsObservationWithoutSubmittingOrCancellingTheTask() throws Exception {
        Client client = new Client(job("pulling"), job("succeeded"));
        assertThrows(IOException.class, () -> operations(client).watch(command().requestId,
                (event, data) -> { throw new IOException("closed"); }, text("en_US")));
        assertEquals(1, client.reads); assertEquals(0, client.submissions);
        assertEquals("succeeded", operations(client).status(command().requestId, text("en_US")).state);
    }

    @Test
    public void aChangedTaskCannotPublishCompletionAfterReconnect() throws Exception {
        var wrong = job("succeeded"); wrong.channel = "release";
        List<Object> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> operations(new Client(job("queued"), wrong))
                .watch(command().requestId, (event, data) -> events.add(data), text("en_US")));
        assertEquals(1, events.size());
    }

    @Test
    public void missingJobsAreUnknownAndProviderOwnsBothLanguagesAndSettingsErrors() throws Exception {
        Client client = new Client() {
            public DockerUpdaterProtocol.Job status(String id) { throw new RequestException(404); }
            public DockerUpdaterProtocol.SettingsResponse settings(String channel) { throw new RequestException(502, "metadata_unavailable"); }
        };
        var operations = operations(client);
        assertNull(operations.watch(command().requestId, (event, data) -> fail(), text("en_US")));
        for (String locale : List.of("zh_CN", "en_US")) {
            var messages = operations.messages(text(locale));
            assertFalse(messages.restoring.startsWith("upgrade."));
            assertFalse(messages.failed.startsWith("upgrade."));
            var settings = operations.settings("preview", text(locale));
            assertEquals("metadata_unavailable", settings.errorCode);
            assertFalse(settings.errorMessage.startsWith("upgrade."));
            assertTrue(settings.managedByUpdater);
        }
        assertEquals(0, client.submissions);
    }

    private static class Client implements DockerControl {
        final List<DockerUpdaterProtocol.Job> jobs;
        int reads, submissions;
        DockerUpdaterProtocol.UpgradeRequest submitted;
        Client(DockerUpdaterProtocol.Job... jobs) { this.jobs = Arrays.asList(jobs); }
        public DockerUpdaterProtocol.Capabilities capabilities() { return new DockerUpdaterProtocol.Capabilities(); }
        public DockerUpdaterProtocol.SettingsResponse settings(String channel) { throw new UnsupportedOperationException(); }
        public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) { throw new UnsupportedOperationException(); }
        public DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request) { submissions++; submitted = request; return jobs.get(0); }
        public DockerUpdaterProtocol.Job status(String id) { return jobs.get(reads++); }
    }
}
