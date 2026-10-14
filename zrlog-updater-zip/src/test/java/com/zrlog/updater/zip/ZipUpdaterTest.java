package com.zrlog.updater.zip;
import com.zrlog.updater.spi.UpdateContext;
import org.junit.Test;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;
public class ZipUpdaterTest {
    @Test public void preservesHostArgumentsAndFiltersAgent() {
        UpdateContext.Builder b = new UpdateContext.Builder();
        b.executable = new File("zrlog-starter.jar"); b.port = 18080;
        b.jvmArguments = List.of("-Xmx128m", "-javaagent:agent.jar");
        b.arguments = new String[]{"--contextPath=/blog"};
        List<String> command = new ZipUpdater(b.build()).buildRestartCommand();
        assertEquals(List.of("java", "-Xmx128m", "-jar", "zrlog-starter.jar", "--contextPath=/blog", "--port=18080"), command);
        b.arguments = new String[]{"--port=19080"};
        assertFalse(new ZipUpdater(b.build()).buildRestartCommand().contains("--port=18080"));
        b.arguments = new String[0];
        java.util.concurrent.atomic.AtomicInteger boundPort = new java.util.concurrent.atomic.AtomicInteger(18080);
        b.currentPort = boundPort::get;
        ZipUpdater updater = new ZipUpdater(b.build()); boundPort.set(18081);
        assertTrue(updater.buildRestartCommand().contains("--port=18081"));
    }
}
