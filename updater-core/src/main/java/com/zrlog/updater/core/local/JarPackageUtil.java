package com.zrlog.updater.core.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class JarPackageUtil {
    private JarPackageUtil() { }
    public static void inJar(List<File> files, String basePath, String target) throws IOException {
        List<File> entries = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) ArchiveFiles.getAllFiles(file.toString(), entries);
            else entries.add(file);
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        for (File file : entries) {
            if (file.toString().endsWith("META-INF" + File.separator + "MANIFEST.MF")) {
                try (InputStream input = new FileInputStream(file)) { manifest = new Manifest(input); }
            }
        }
        Path destination = Path.of(target).toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".zrlog-update-", ".war");
        try {
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary), manifest)) {
                for (File file : entries) {
                    String name = file.toString().substring(basePath.length()).replace('\\', '/');
                    if ("META-INF/MANIFEST.MF".equals(name)) continue;
                    output.putNextEntry(new JarEntry(name));
                    Files.copy(file.toPath(), output); output.closeEntry();
                }
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
