package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;
import com.zrlog.updater.spi.UpdateStrings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;

/** Package transfer and integrity checks, shared by all package providers. */
public final class PackageFiles {
    private PackageFiles() { }
    public static String checksum(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }
    public static boolean invalid(File file, long length, String sha256, String md5) {
        try {
            if (file == null || !file.isFile() || file.length() <= 0 || (length > 0 && length != file.length())) return true;
            if (UpdateStrings.isNotEmpty(sha256)) return !sha256.equalsIgnoreCase(checksum(file, "SHA-256"));
            return UpdateStrings.isNotEmpty(md5) && !md5.equalsIgnoreCase(checksum(file, "MD5"));
        } catch (Exception e) { return true; }
    }
    public static int percent(File file, long size) {
        return file == null || size <= 0 || !file.exists() ? 0 : (int) Math.min(file.length() * 100 / size, 100);
    }
    public static File download(UpdateContext context, UpdateRequest request) throws Exception {
        boolean war = request.updater != null && request.updater.getType() == UpdaterTypeEnum.WAR;
        long size = war ? request.version.getWarFileSize() : request.version.getZipFileSize();
        String sha = war ? request.version.getWarSha256() : request.version.getZipSha256();
        String md5 = war ? request.version.getWarMd5sum() : request.version.getZipMd5sum();
        String url = new DefaultUpdateCatalog().selectPackageSource(request.version, war, context.packageSource);
        File directory = request.updater == null ? context.updateTemp : request.updater.getUpdateTempPath();
        Files.createDirectories(directory.toPath());
        File file = Files.createTempFile(directory.toPath(), "zrlog-", war ? ".war" : ".zip").toFile();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread transfer = new Thread(() -> {
            try { if (context.http.download(URI.create(url), file) != 200) throw new IOException("Update package download failed"); }
            catch (Exception e) { failure.set(e); }
        }, "zrlog-update-download");
        transfer.setDaemon(true);
        publish(request, "download", "running", "upgrade.status.downloading", null);
        transfer.start();
        try {
            int previous = 0;
            while (transfer.isAlive()) {
                transfer.join(1000);
                int percent = percent(file, size);
                if (percent > previous) {
                    publish(request, "download", "running", "upgrade.status.downloading", percent + "%");
                    previous = percent;
                }
            }
            if (failure.get() != null || invalid(file, size, sha, md5)) throw new IOException("Invalid update package", failure.get());
            publish(request, "download", "complete", "upgrade.status.downloaded", file.getName());
            return file;
        } catch (Exception e) {
            transfer.interrupt();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!transfer.isAlive()) Files.deleteIfExists(file.toPath());
            throw e;
        }
    }
    public static void publish(UpdateRequest request, String stage, String status, String key, String detail) throws Exception {
        request.progress.onProgress(UpgradeProgressEvent.EVENT, UpgradeProgressEvent.data(stage, status, request.text.apply(key), detail));
    }
}
