package com.zrlog.updater.zip;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class ZipPackageExecutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void upgradesWithTheSuppliedHostBeforeAnyApplicationConfigExists() throws Exception {
        File archive = temporary.newFile("zrlog.zip");
        try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
            stream.putNextEntry(new ZipEntry("version.txt"));
            stream.write("new-version".getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
        }
        UpdateContext.Builder builder = new UpdateContext.Builder();
        builder.root = temporary.newFolder("runtime");
        UpdateContext context = builder.build();
        AtomicBoolean restarted = new AtomicBoolean();
        var updater = new HostedUpdater(context, UpdaterTypeEnum.ZIP) {
            public void restartProcessAsync(Version version) { restarted.set(true); }
        };
        var handler = new ZipProvider().handler(context, new UpdateRequest(archive, new Version(), null,
                updater, UpgradeProgressListener.NONE, key -> key));
        handler.doHandle();
        assertTrue(handler.isFinish());
        assertTrue(restarted.get());
        assertEquals("new-version", Files.readString(new File(builder.root, "version.txt").toPath()));
    }
}
