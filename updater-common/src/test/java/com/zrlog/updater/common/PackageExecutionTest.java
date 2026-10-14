package com.zrlog.updater.common;

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

public class PackageExecutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void rejectsTraversalAndVerifiesShaBeforeMd5() throws Exception {
        File zip = temporary.newFile("unsafe.zip"), root = temporary.newFolder("extract");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) { entry(output, "../outside", "bad"); }
        assertThrows(java.io.IOException.class, () -> ArchiveFiles.unZip(zip.toString(), root.toString()));
        assertFalse(new File(root.getParentFile(), "outside").exists());
        assertFalse(PackageFiles.invalid(zip, zip.length(), PackageFiles.checksum(zip, "SHA-256"), "bad-md5"));
        assertTrue(PackageFiles.invalid(zip, zip.length(), "bad-sha", PackageFiles.checksum(zip, "MD5")));
        assertTrue(PackageFiles.invalid(zip, zip.length() + 1, null, null));
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
