package com.zrlog.updater.faas;

import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.aws.*;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.*;
import com.zrlog.updater.common.ArchiveFiles;
import com.zrlog.updater.common.PackageFiles;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import static org.junit.Assert.*;

public class FaasPackageExecutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void providerAvailabilityUsesTheSuppliedLambdaFacts() {
        UpdateContext.Builder builder = new UpdateContext.Builder();
        builder.aws = new AwsLambdaUpdateConfig(new AwsCredentials("key", "secret", null), "region", "function", "bucket");
        assertFalse(new FaasProvider().onlineUpgradeSupported(builder.build()));
        builder.lambda = true;
        assertTrue(new FaasProvider().onlineUpgradeSupported(builder.build()));
        builder.aws = new AwsLambdaUpdateConfig(new AwsCredentials(null, null, null), null, null, null);
        assertFalse(new FaasProvider().onlineUpgradeSupported(builder.build()));
    }
    @Test public void manualGuidancePublishesOneStandardEventWithoutDownloading() {
        UpdateContext.Builder builder = new UpdateContext.Builder();
        builder.aws = new AwsLambdaUpdateConfig(new AwsCredentials(null, null, null), null, null, null);
        Version version = new Version(); version.setZipDownloadUrl("https://example.com/zrlog.zip");
        java.util.concurrent.atomic.AtomicInteger events = new java.util.concurrent.atomic.AtomicInteger();
        var handler = new FaasProvider().manualHandler(builder.build(), new UpdateRequest(null, version, null, null,
                (event, data) -> {
                    events.incrementAndGet();
                    assertEquals(com.zrlog.common.updater.UpgradeProgressEvent.EVENT, event);
                    assertEquals(com.zrlog.common.updater.UpgradeProgressEvent.STAGE_MANUAL, data.getStage());
                }, key -> key));
        handler.doHandle(); handler.doHandle();
        assertTrue(handler.isFinish()); assertEquals(1, events.get());
        assertTrue(handler.getMessage().contains(version.getZipDownloadUrl()));
    }
    @Test public void faasManualInstructionsUseHostArchitectureAndBucket() {
        UpdateContext.Builder b = new UpdateContext.Builder(); b.lambda = true; b.fileArch = "Linux-amd64";
        b.aws = new AwsLambdaUpdateConfig(new AwsCredentials(null, null, null), "region", "function", "custom-bucket");
        Version version = new Version(); version.setZipDownloadUrl("https://example.com/package.zip");
        FaasUpdateVersionHandler handler = new FaasUpdateVersionHandler(b.build(), key -> key, version, null, UpgradeProgressListener.NONE);
        handler.doHandle(); assertTrue(handler.isFinish());
        assertTrue(handler.getMessage().contains("custom-bucket"));
        assertTrue(handler.getMessage().contains("zrlog-Linux-amd64-latest.zip"));
    }
    private static void put(File root, String path, String content) throws Exception {
        File file = new File(root, path); Files.createDirectories(file.toPath().getParent()); Files.writeString(file.toPath(), content);
    }
    private static void entry(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name)); output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.closeEntry();
    }
    private static String read(JarFile file, String path) throws Exception {
        try (java.io.InputStream input = file.getInputStream(file.getEntry(path))) { return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    }
}
