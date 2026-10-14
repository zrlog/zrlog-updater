package com.zrlog.updater.core;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.aws.*;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.core.handle.FaasUpdateVersionHandler;
import com.zrlog.updater.core.local.*;
import com.zrlog.updater.spi.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import static org.junit.Assert.*;

public class PackageExecutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void loadsEveryDeploymentAndOwnsBothLanguages() throws Exception {
        for (UpdaterTypeEnum type : UpdaterTypeEnum.values()) assertEquals(type, UpdateProviders.get(type).type());
        Properties english = language("en_US"), chinese = language("zh_CN");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        assertEquals("正在解压", chinese.getProperty("upgrade.zip.status.unzipping"));
    }
    private Properties language(String locale) throws Exception {
        Properties properties = new Properties();
        for (String resource : UpdateProviders.messageResources(locale)) {
            try (java.io.InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(resource, input); properties.load(input);
            }
        }
        return properties;
    }
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
    @Test public void rejectsTraversalAndVerifiesShaBeforeMd5() throws Exception {
        File zip = temporary.newFile("unsafe.zip"), root = temporary.newFolder("extract");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) { entry(output, "../outside", "bad"); }
        assertThrows(java.io.IOException.class, () -> ArchiveFiles.unZip(zip.toString(), root.toString()));
        assertFalse(new File(root.getParentFile(), "outside").exists());
        assertFalse(PackageFiles.invalid(zip, zip.length(), PackageFiles.checksum(zip, "SHA-256"), "bad-md5"));
        assertTrue(PackageFiles.invalid(zip, zip.length(), "bad-sha", PackageFiles.checksum(zip, "MD5")));
        assertTrue(PackageFiles.invalid(zip, zip.length() + 1, null, null));
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
