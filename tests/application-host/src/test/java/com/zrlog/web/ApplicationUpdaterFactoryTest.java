package com.zrlog.web;

import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

public class ApplicationUpdaterFactoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldCreateZipUpdaterForTheLauncherRatherThanTheLastDependency() throws Exception {
        File launcher = jar("my blog launcher.jar", "com.zrlog.web.Application");
        File dependency = jar("slf4j-simple-2.0.13.jar", null);
        String original = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", launcher + File.pathSeparator + dependency);
            String[] arguments = {"--port=18081"};
            Updater updater = ApplicationUpdaterFactory.create(arguments, null);
            assertNotNull(updater);
            assertEquals(UpdaterTypeEnum.ZIP, updater.getType());
            assertEquals(launcher.getCanonicalFile(), updater.execFile());
            assertEquals(launcher.getCanonicalFile(), com.zrlog.common.updater.UpdaterHosts.context(updater).executable);
            assertArrayEquals(arguments, com.zrlog.common.updater.UpdaterHosts.context(updater).arguments);
        } finally {
            System.setProperty("java.class.path", original);
        }
    }

    private File jar(String name, String mainClass) throws Exception {
        File file = temporaryFolder.newFile(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        try (JarOutputStream ignored = new JarOutputStream(new FileOutputStream(file), manifest)) {
            // An actual JAR manifest exercises the same entrypoint lookup as packaged applications.
        }
        return file;
    }
}
