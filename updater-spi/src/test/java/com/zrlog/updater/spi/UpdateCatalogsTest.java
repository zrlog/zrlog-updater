package com.zrlog.updater.spi;

import com.zrlog.common.vo.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceConfigurationError;

import static org.junit.Assert.*;

public class UpdateCatalogsTest {
    private static final String SERVICE = "META-INF/services/" + UpdateCatalog.class.getName();
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void spiWorksWithoutAnImplementation() throws Exception {
        try (URLClassLoader loader = services("")) {
            assertFalse(UpdateCatalogs.load(loader).isPresent());
        }
        assertTrue(assertThrows(IllegalStateException.class, UpdateCatalogs::getInstance)
                .getMessage().contains("No ZrLog updater"));
    }

    @Test public void discoversARegisteredImplementation() throws Exception {
        try (URLClassLoader loader = services(First.class.getName())) {
            assertEquals(First.class, UpdateCatalogs.load(loader).orElseThrow().getClass());
        }
    }

    @Test public void reportsConflictingImplementations() throws Exception {
        try (URLClassLoader loader = services(First.class.getName() + "\n" + Second.class.getName())) {
            assertThrows(IllegalStateException.class, () -> UpdateCatalogs.load(loader));
        }
    }

    @Test public void reportsAnInvalidServiceDeclaration() throws Exception {
        try (URLClassLoader loader = services("no.such.Provider")) {
            assertThrows(ServiceConfigurationError.class, () -> UpdateCatalogs.load(loader));
        }
    }

    private URLClassLoader services(String providers) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path descriptor = root.resolve(SERVICE);
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, providers, StandardCharsets.UTF_8);
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, UpdateCatalog.class.getClassLoader()) {
            @Override public Enumeration<URL> getResources(String name) throws java.io.IOException {
                return SERVICE.equals(name) ? Collections.enumeration(Collections.list(findResources(name)))
                        : super.getResources(name);
            }
        };
    }

    public static class First implements UpdateCatalog {
        public URI metadataUri(String origin, String filename, String channel) { return URI.create(origin); }
        public void validateDocker(Version version, String channel) { }
        public String selectPackageSource(Version version, boolean war, String source) { return source; }
    }
    public static class Second extends First { }
}
