package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.ApplicationUpdater;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

public class UpdaterUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldPrioritizeDockerFaasAndDebOverNativeOrZip() {
        assertEquals(UpdaterTypeEnum.DOCKER, UpdaterUtils.selectType(true, true, "deb", true));
        assertEquals(UpdaterTypeEnum.FAAS, UpdaterUtils.selectType(false, true, "deb", true));
        assertEquals(UpdaterTypeEnum.DEB, UpdaterUtils.selectType(false, false, "deb", true));
        assertEquals(UpdaterTypeEnum.DEB, UpdaterUtils.selectType(false, false, "deb", false));
        assertEquals(UpdaterTypeEnum.NATIVE_IMAGE, UpdaterUtils.selectType(false, false, "zip", true));
        assertEquals(UpdaterTypeEnum.ZIP, UpdaterUtils.selectType(false, false, "zip", false));
    }

    @Test
    public void shouldResolveSingleJarLaunchAndDeduplicateClasspathEntries() throws Exception {
        File launcher = jar("launcher.jar", "com.zrlog.web.Application");
        assertEquals(launcher.getCanonicalFile(), resolve(launcher.toString()));
        assertEquals(launcher.getCanonicalFile(), resolve(launcher + File.pathSeparator + launcher));
    }

    @Test
    public void shouldUseOnlyAValidatedDistributionLauncherForClasspathLaunches() throws Exception {
        File dependency = jar("dependency.jar", "another.Application");
        assertNull(resolve(dependency.toString()));
        File launcher = jar("zrlog-starter.jar", "com.zrlog.web.Application");
        assertEquals(launcher.getCanonicalFile(), resolve(dependency.toString()));
    }

    @Test
    public void shouldRejectUnreadableUnrelatedAndAmbiguousJars() throws Exception {
        File broken = temporaryFolder.newFile("broken.jar");
        jar("zrlog-starter.jar", "another.Application");
        assertNull(resolve(broken.toString()));
        File first = jar("first.jar", "com.zrlog.web.Application");
        File second = jar("second.jar", "com.zrlog.web.Application");
        assertNull(resolve(first + File.pathSeparator + second));
    }

    @Test
    public void shouldPassTheValidatedZipLauncherToTheHostFactory() throws Exception {
        File launcher = jar("my blog launcher.jar", "com.zrlog.web.Application");
        File dependency = jar("dependency.jar", null);
        ApplicationUpdater updater = UpdaterUtils.getUpdater(UpdaterTypeEnum.ZIP, dependency,
                launcher + File.pathSeparator + dependency, temporaryFolder.getRoot(), StubUpdater::new);
        assertNotNull(updater);
        assertEquals(UpdaterTypeEnum.ZIP, updater.getType());
        assertEquals(launcher.getCanonicalFile(), updater.execFile());
    }

    @Test
    public void shouldPreserveExecutablesForOtherDeploymentTypesWithoutJarLookup() throws Exception {
        File executable = temporaryFolder.newFile("zrlog");
        for (UpdaterTypeEnum type : UpdaterTypeEnum.values()) {
            if (type == UpdaterTypeEnum.ZIP) continue;
            ApplicationUpdater updater = UpdaterUtils.getUpdater(type, executable,
                    "not-a-jar", temporaryFolder.getRoot(), StubUpdater::new);
            assertNotNull(updater);
            assertEquals(type, updater.getType());
            assertEquals(executable, updater.execFile());
        }
    }

    @Test
    public void shouldSkipFactoryWhenZipLauncherIsAmbiguous() throws Exception {
        File first = jar("first.jar", "com.zrlog.web.Application");
        File second = jar("second.jar", "com.zrlog.web.Application");
        AtomicBoolean called = new AtomicBoolean();
        assertNull(UpdaterUtils.getUpdater(UpdaterTypeEnum.ZIP, null,
                first + File.pathSeparator + second, temporaryFolder.getRoot(), (type, target) -> {
                    called.set(true);
                    return new StubUpdater(type, target);
                }));
        assertFalse(called.get());
    }

    @Test
    public void shouldKeepStartupAvailableWhenTheSelectedProviderCannotBeCreated() {
        assertNull(UpdaterUtils.getUpdater(UpdaterTypeEnum.DEB, null, "", temporaryFolder.getRoot(),
                (type, target) -> { throw new IllegalStateException("No updater implementation installed for DEB"); }));
    }

    private File resolve(String classPath) {
        return UpdaterUtils.findLauncherJar(classPath, temporaryFolder.getRoot());
    }

    private File jar(String name, String mainClass) throws Exception {
        File file = temporaryFolder.newFile(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream ignored = new JarOutputStream(new FileOutputStream(file), manifest)) { }
        return file;
    }

    private static final class StubUpdater implements ApplicationUpdater {
        private final UpdaterTypeEnum type;
        private final File executable;
        private StubUpdater(UpdaterTypeEnum type, File executable) { this.type = type; this.executable = executable; }
        public void restartProcessAsync(Version version) { throw new AssertionError("No upgrades in selection tests"); }
        public File getUpdateTempPath() { return new File("update-temp"); }
        public String getUnzipPath() { return ""; }
        public File execFile() { return executable; }
        public UpdaterTypeEnum getType() { return type; }
    }
}
