package com.zrlog.updater.spi;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.*;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;
public class UpdateProvidersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void absenceOnlyDisablesRequestedUpgradeCapability() {
        assertFalse(UpdateProviders.find(UpdaterTypeEnum.ZIP).isPresent());
        assertTrue(UpdateProviders.messageResources("en_US").isEmpty());
        assertThrows(IllegalStateException.class, () -> UpdateProviders.get(UpdaterTypeEnum.ZIP));
    }
    @Test public void selectsByDeploymentAndRejectsDuplicateProvider() throws Exception {
        try (URLClassLoader loader = services(First.class.getName())) {
            assertEquals(UpdaterTypeEnum.ZIP, UpdateProviders.load(loader, UpdaterTypeEnum.ZIP).get().type());
            assertFalse(UpdateProviders.load(loader, UpdaterTypeEnum.DEB).isPresent());
        }
        try (URLClassLoader loader = services(First.class.getName() + "\n" + Second.class.getName())) {
            assertThrows(IllegalStateException.class, () -> UpdateProviders.load(loader, UpdaterTypeEnum.ZIP));
        }
    }
    @Test public void brokenRegistrationDoesNotMasqueradeAsAbsence() throws Exception {
        try (URLClassLoader loader = services("missing.UpdaterProvider")) {
            assertThrows(java.util.ServiceConfigurationError.class, () -> UpdateProviders.load(loader, UpdaterTypeEnum.ZIP));
        }
    }
    private URLClassLoader services(String content) throws Exception {
        File root = temporary.newFolder(); File file = new File(root, "META-INF/services/" + UpdateProvider.class.getName());
        Files.createDirectories(file.toPath().getParent()); Files.writeString(file.toPath(), content + "\n");
        return new URLClassLoader(new URL[]{root.toURI().toURL()}, getClass().getClassLoader());
    }
    public static class First implements UpdateProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.ZIP; }
        public ApplicationUpdater create(UpdateContext c) { return null; }
        public UpdateVersionHandler handler(UpdateContext c, UpdateRequest r) { return null; }
        public File download(UpdateContext c, UpdateRequest r) { return null; }
        public boolean invalidPackage(File f, long size, String sha, String md5) { return false; }
        public String checksum(File f, String algorithm) { return ""; }
        public List<String> messageBundles() { return List.of(); }
    }
    public static class Second extends First { }
}
