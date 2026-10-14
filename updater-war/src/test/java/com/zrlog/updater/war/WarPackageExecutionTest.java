package com.zrlog.updater.war;

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

public class WarPackageExecutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void preservesWarSiteFilesAndCustomThemes() throws Exception {
        UpdateContext.Builder b = new UpdateContext.Builder();
        b.root = temporary.newFolder("webapps", "ROOT"); b.temp = temporary.newFolder("temp");
        b.executable = new File(b.root.getParentFile(), "ROOT.war");
        b.bundledTheme = path -> path.equals("/include/templates/default");
        put(b.root, "WEB-INF/db.properties", "existing-db");
        put(b.root, "WEB-INF/install.lock", "existing-install");
        put(b.root, "attached/photo.jpg", "existing-photo");
        put(b.root, "include/templates/default/index.html", "old-default");
        put(b.root, "include/templates/custom/index.html", "custom");
        File source = temporary.newFile("new.war");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source.toPath()))) {
            entry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n");
            entry(output, "include/templates/default/index.html", "new-default");
            entry(output, "WEB-INF/db.properties", "new-db");
        }
        WarUpdater updater = new WarUpdater(b.build());
        File backup = new File(updater.backup());
        assertEquals("existing-db", Files.readString(new File(backup, "ROOT/WEB-INF/db.properties").toPath()));
        updater.buildUpgradeFile(source.toString(), "upgrade-test");
        try (JarFile war = new JarFile(b.executable)) {
            assertEquals("existing-db", read(war, "WEB-INF/db.properties"));
            assertEquals("existing-install", read(war, "WEB-INF/install.lock"));
            assertEquals("existing-photo", read(war, "attached/photo.jpg"));
            assertEquals("new-default", read(war, "include/templates/default/index.html"));
            assertEquals("custom", read(war, "include/templates/custom/index.html"));
        }
        assertFalse(new File(b.temp, "upgrade-test").exists());
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
