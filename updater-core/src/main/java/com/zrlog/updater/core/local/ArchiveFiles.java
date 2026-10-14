package com.zrlog.updater.core.local;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** File operations formerly supplied by base, without application dependencies. */
public final class ArchiveFiles {
    private ArchiveFiles() { }
    public static void unZip(String source, String target) throws IOException {
        File root = new File(target).getCanonicalFile(); Files.createDirectories(root.toPath());
        try (ZipFile zip = new ZipFile(source)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                File file = new File(root, entry.getName()).getCanonicalFile();
                if (!file.toPath().startsWith(root.toPath())) throw new IOException("Unsafe zip entry: " + entry.getName());
                if (entry.isDirectory()) { Files.createDirectories(file.toPath()); continue; }
                Files.createDirectories(file.toPath().getParent());
                try (java.io.InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    public static void getAllFiles(String source, List<File> result) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of(source))) {
            files.filter(Files::isRegularFile).forEach(path -> result.add(path.toFile()));
        }
    }
    public static void moveOrCopyFolder(String source, String destination, boolean move) throws IOException {
        Path from = Path.of(source), to = Path.of(destination).resolve(from.getFileName());
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir))); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = to.resolve(from.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        if (move) deleteFile(source);
    }
    public static void deleteFile(String source) throws IOException {
        Path root = Path.of(source); if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }
}
