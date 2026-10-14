package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateProvider;
import com.zrlog.updater.spi.UpdateRequest;
import com.zrlog.updater.spi.UpdateSession;
import com.zrlog.updater.spi.DockerConnection;
import com.zrlog.common.updater.UpdateVersionHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;
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
        assertEquals(UpdaterTypeEnum.ZIP, UpdaterUtils.selectType(false, false, "war", false));
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
    public void shouldDiscoverZipProviderAndCreateItWithTheValidatedLauncher() throws Exception {
        File launcher = jar("my blog launcher.jar", "com.zrlog.web.Application");
        File dependency = jar("dependency.jar", null);
        UpdateContext.Builder host = new UpdateContext.Builder();
        host.executable = dependency;
        host.arguments = new String[]{"--port=8081"};
        host.stop = () -> { };
        try (URLClassLoader loader = services(ZipProvider.class)) {
            UpdateSession session = UpdaterUtils.create(runtime("zip", launcher + File.pathSeparator + dependency), host.build(), loader);
            assertNotNull(session);
            assertEquals(UpdaterTypeEnum.ZIP, session.updater.getType());
            assertEquals(launcher.getCanonicalFile(), session.updater.execFile());
            assertEquals(session.context.executable, session.updater.execFile());
            assertArrayEquals(host.arguments, session.context.arguments);
            assertSame(host.stop, session.context.stop);
            assertEquals(dependency, host.executable);
        }
    }

    @Test
    public void shouldPreserveExecutablesForOtherDeploymentTypesWithoutJarLookup() throws Exception {
        File executable = temporaryFolder.newFile("zrlog");
        for (Class<? extends TestProvider> provider : List.of(DockerProvider.class, FaasProvider.class,
                DebProvider.class, NativeProvider.class, WarProvider.class)) {
            UpdaterTypeEnum type = provider.getConstructor().newInstance().type();
            UpdateContext.Builder host = new UpdateContext.Builder();
            host.executable = executable;
            UpdaterRuntime runtime = type == UpdaterTypeEnum.WAR ? UpdaterRuntime.servlet(temporaryFolder.getRoot())
                    : new UpdaterRuntime(type == UpdaterTypeEnum.DOCKER, type == UpdaterTypeEnum.FAAS,
                    type == UpdaterTypeEnum.DEB ? "deb" : type == UpdaterTypeEnum.WAR ? "war" : "zip",
                    type == UpdaterTypeEnum.NATIVE_IMAGE, "not-a-jar", temporaryFolder.getRoot());
            try (URLClassLoader loader = services(provider)) {
                UpdateSession session = UpdaterUtils.create(runtime, host.build(), loader);
                assertNotNull(session);
                assertEquals(type, session.updater.getType());
                assertEquals(executable, session.updater.execFile());
            }
        }
    }

    @Test
    public void shouldSkipProviderCreationWhenZipLauncherIsAmbiguous() throws Exception {
        File first = jar("first.jar", "com.zrlog.web.Application");
        File second = jar("second.jar", "com.zrlog.web.Application");
        TestProvider.created.set(false);
        try (URLClassLoader loader = services(ZipProvider.class)) {
            assertNull(UpdaterUtils.create(runtime("zip", first + File.pathSeparator + second), new UpdateContext.Builder().build(), loader));
        }
        assertFalse(TestProvider.created.get());
    }

    @Test
    public void shouldKeepStartupAvailableWhenTheSelectedProviderCannotBeCreated() throws Exception {
        try (URLClassLoader missing = services(); URLClassLoader broken = services(BrokenProvider.class)) {
            assertNull(UpdaterUtils.create(runtime("deb", ""), new UpdateContext.Builder().build(), missing));
            assertNull(UpdaterUtils.create(runtime("deb", ""), new UpdateContext.Builder().build(), broken));
        }
    }

    @Test
    public void shouldUseDockerMetadataWithoutChangingTheOriginalHost() throws Exception {
        UpdateContext.Builder host = new UpdateContext.Builder();
        host.metadataOrigin = "https://dl.zrlog.com";
        host.manifestFilename = "last.zip.version.json";
        host.docker = new DockerConnection("http://updater", "token", host.metadataOrigin, "last.docker-image.version.json");
        try (URLClassLoader loader = services(DockerProvider.class)) {
            UpdateSession session = UpdaterUtils.create(new UpdaterRuntime(true, false, "zip", true, "", temporaryFolder.getRoot()), host.build(), loader);
            assertEquals(host.docker.manifestFilename, session.context.manifestFilename);
            assertEquals("last.zip.version.json", host.manifestFilename);
            assertSame(host.docker, session.context.docker);
        }
    }

    private UpdaterRuntime runtime(String packageType, String classPath) {
        return new UpdaterRuntime(false, false, packageType, false, classPath, temporaryFolder.getRoot());
    }

    @SafeVarargs
    private final URLClassLoader services(Class<? extends TestProvider>... providers) throws Exception {
        File root = temporaryFolder.newFolder();
        File registration = new File(root, "META-INF/services/" + UpdateProvider.class.getName());
        Files.createDirectories(registration.toPath().getParent());
        StringBuilder content = new StringBuilder();
        for (Class<?> provider : providers) content.append(provider.getName()).append('\n');
        Files.writeString(registration.toPath(), content);
        return new URLClassLoader(new URL[]{root.toURI().toURL()}, getClass().getClassLoader());
    }

    public abstract static class TestProvider implements UpdateProvider {
        static final AtomicBoolean created = new AtomicBoolean();
        public ApplicationUpdater create(UpdateContext context) {
            created.set(true);
            return new StubUpdater(type(), context.executable);
        }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest request) { throw new AssertionError(); }
        public File download(UpdateContext context, UpdateRequest request) { throw new AssertionError(); }
        public boolean invalidPackage(File file, long length, String sha256, String md5) { throw new AssertionError(); }
        public String checksum(File file, String algorithm) { throw new AssertionError(); }
        public List<String> messageBundles() { return List.of(); }
    }
    public static class ZipProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.ZIP; } }
    public static class WarProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.WAR; } }
    public static class DockerProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.DOCKER; } }
    public static class FaasProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.FAAS; } }
    public static class DebProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.DEB; } }
    public static class NativeProvider extends TestProvider { public UpdaterTypeEnum type() { return UpdaterTypeEnum.NATIVE_IMAGE; } }
    public static class BrokenProvider extends DebProvider {
        public ApplicationUpdater create(UpdateContext context) { throw new IllegalStateException("Cannot create provider"); }
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
