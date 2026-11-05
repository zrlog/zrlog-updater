package com.zrlog.updater.common;

import static org.junit.Assert.*;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.updater.spi.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackageUpdateOperationsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptedExecutionSurvivesObserverFailureAndIsIdempotentWhenMetadataDisappears() throws Exception {
        Fixture fixture = new Fixture();
        UpdateTask accepted = fixture.operations.submit(command(), (event, data) -> { throw new IOException("closed"); }, Function.identity());
        assertEquals(1, fixture.executions.get());
        assertEquals("running", accepted.state);
        assertTrue(accepted.awaitingRestart);
        fixture.metadataAvailable = false;
        assertTrue(fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity()).awaitingRestart);
        assertEquals(1, fixture.executions.get());
        ArchiveFiles.deleteFile(fixture.context.updateTemp.toString());
        fixture.runningBuild.set(command().buildId);
        var restored = new PackageUpdateOperations(fixture.context, fixture.updater, fixture.provider);
        assertEquals("succeeded", restored.status(command().requestId, Function.identity()).state);
        assertFalse(restored.status(command().requestId, Function.identity()).awaitingRestart);
    }

    @Test
    public void taskIdentityAndThePinnedManifestAreCheckedBeforeEffects() throws Exception {
        Fixture fixture = new Fixture();
        UpdateCommand wrong = command(); wrong.buildId = "changed-build";
        assertThrows(IllegalArgumentException.class, () -> fixture.operations.submit(wrong, UpgradeProgressListener.NONE, Function.identity()));
        assertEquals(0, fixture.executions.get());
        assertNull(fixture.operations.status(command().requestId, Function.identity()));
        fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity());
        assertThrows(IllegalArgumentException.class, () -> fixture.operations.submit(wrong, UpgradeProgressListener.NONE, Function.identity()));
        assertEquals(1, fixture.executions.get());
        wrong.requestId = "../../outside-path";
        assertThrows(IllegalArgumentException.class, () -> fixture.operations.status(wrong.requestId, Function.identity()));
    }

    @Test
    public void aFailedTaskIsNotResubmittedAndUnknownTasksStayUnknown() throws Exception {
        Fixture fixture = new Fixture(); fixture.fail = true;
        UpdateTask failed = fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity());
        assertTrue(failed.failed());
        assertTrue(failed.recoveryRequired);
        assertTrue(fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity()).failed());
        assertEquals(1, fixture.executions.get());
        assertNull(fixture.operations.watch("another-request-1234", (event, data) -> fail(), Function.identity()));
    }

    @Test
    public void checksumFailurePreventsExecutionAndWarSelectsItsOwnArtifact() throws Exception {
        for (UpdaterTypeEnum type : java.util.List.of(UpdaterTypeEnum.ZIP, UpdaterTypeEnum.WAR)) {
            Fixture fixture = new Fixture(type);
            fixture.downloadPackage = true;
            UpdateTask task = fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity());
            assertTrue(task.failed());
            assertEquals(0, fixture.executions.get());
            assertEquals(type == UpdaterTypeEnum.WAR ? "/zrlog.war" : "/zrlog.zip", fixture.downloaded.get().getPath());
        }
    }

    @Test
    public void anObserverDoesNotInterruptAnActiveExecution() throws Exception {
        Fixture fixture = new Fixture();
        fixture.entered = new java.util.concurrent.CountDownLatch(1);
        fixture.release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var executing = executor.submit(() -> fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity()));
            assertTrue(fixture.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("running", fixture.operations.status(command().requestId, Function.identity()).state);
            fixture.release.countDown();
            assertTrue(executing.get(5, java.util.concurrent.TimeUnit.SECONDS).awaitingRestart);
        } finally { fixture.release.countDown(); executor.shutdownNow(); }
    }

    @Test
    public void abandonedExecutionRequiresRecoveryButPendingRestartStillWaitsForTargetBuild() throws Exception {
        Fixture fixture = new Fixture();
        fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity());
        assertTrue(fixture.operations.status(command().requestId, Function.identity()).awaitingRestart);
        var path = fixture.context.updateTemp.toPath().resolveSibling(fixture.context.updateTemp.getName() + "-tasks").resolve(command().requestId + ".json");
        // Simulate a process stopping after accepting the task but before requesting restart.
        Files.writeString(path, Files.readString(path).replace("\"awaitingRestart\":true", "\"awaitingRestart\":false"));
        UpdateTask recovered = fixture.operations.status(command().requestId, Function.identity());
        assertEquals("interrupted", recovered.state);
        assertTrue(recovered.recoveryRequired);
        assertTrue(fixture.operations.submit(command(), UpgradeProgressListener.NONE, Function.identity()).failed());
        assertEquals(1, fixture.executions.get());
    }

    private static UpdateCommand command() {
        UpdateCommand command = new UpdateCommand(); command.requestId = "package-request-1234";
        command.version = "4.0.2"; command.buildId = "new-build"; command.channel = "release";
        return command;
    }

    private final class Fixture {
        final AtomicInteger executions = new AtomicInteger();
        final AtomicReference<String> runningBuild = new AtomicReference<>("old-build");
        final AtomicReference<URI> downloaded = new AtomicReference<>();
        final UpdateContext context;
        final ApplicationUpdater updater;
        final PackageUpdateProvider provider;
        final UpdateOperations operations;
        boolean metadataAvailable = true, fail, downloadPackage;
        java.util.concurrent.CountDownLatch entered, release;
        Fixture() throws Exception { this(UpdaterTypeEnum.ZIP); }
        Fixture(UpdaterTypeEnum type) throws Exception {
            UpdateContext.Builder builder = new UpdateContext.Builder();
            builder.updateTemp = temporary.newFolder();
            builder.currentBuildId = runningBuild::get;
            builder.metadataOrigin = "https://updates.example.test"; builder.manifestFilename = "version.json";
            builder.http = new UpdateHttp() {
                public String read(URI uri) throws Exception {
                    if (!metadataAvailable) throw new IOException("offline");
                    return "{\"version\":\"4.0.2\",\"buildId\":\"new-build\",\"releaseDate\":\"2026-11-05 00:00:00+08:00\","
                            + "\"zipDownloadUrl\":\"https://updates.example.test/zrlog.zip\",\"warDownloadUrl\":\"https://updates.example.test/zrlog.war\","
                            + "\"zipMd5sum\":\"bad-checksum\",\"warMd5sum\":\"bad-checksum\"}";
                }
                public int download(URI uri, File target) throws Exception {
                    downloaded.set(uri); Files.writeString(target.toPath(), "invalid-package"); return 200;
                }
                public HttpResponse<String> send(HttpRequest request) { throw new AssertionError(); }
            };
            context = builder.build();
            updater = new HostedUpdater(context, type);
            provider = new PackageUpdateProvider(type, "/test") {
                public ApplicationUpdater create(UpdateContext ignored) { return updater; }
                public File download(UpdateContext context, UpdateRequest request) throws Exception {
                    return downloadPackage ? super.download(context, request) : null;
                }
                public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) {
                    return new UpdateVersionHandler() {
                        public String getMessage() { return ""; }
                        public boolean isFinish() { return !fail; }
                        public void doHandle() {
                            assertTrue(Files.exists(context.updateTemp.toPath().resolveSibling(context.updateTemp.getName() + "-tasks").resolve(command().requestId + ".json")));
                            executions.incrementAndGet();
                            if (entered != null) {
                                entered.countDown();
                                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
                                catch (InterruptedException error) { throw new IllegalStateException(error); }
                            }
                            try { PackageFiles.publish(request, "restart", "running", "upgrade.status.restarting", null); }
                            catch (Exception error) { throw new IllegalStateException(error); }
                        }
                    };
                }
            };
            operations = provider.operations(context, updater);
        }
    }
}
