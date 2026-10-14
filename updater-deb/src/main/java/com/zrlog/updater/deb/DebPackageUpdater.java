package com.zrlog.updater.deb;

import com.google.gson.Gson;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.common.DefaultUpdateCatalog;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.common.PackageFiles;
import com.zrlog.updater.spi.DebUpgradeStatus;
import com.zrlog.updater.spi.SystemPackageUpdater;
import com.zrlog.updater.spi.UpdateContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A request runs unprivileged; installation runs only in the root-owned oneshot process. */
public final class DebPackageUpdater extends HostedUpdater implements SystemPackageUpdater {
    private static final Gson GSON = new Gson();
    public interface Commands { String run(String... arguments) throws Exception; }
    private final Commands commands;
    public DebPackageUpdater(UpdateContext context) { this(context, DebPackageUpdater::execute); }
    public DebPackageUpdater(UpdateContext context, Commands commands) {
        super(context, UpdaterTypeEnum.DEB); this.commands = commands;
    }
    private static void channel(String value) {
        if (!List.of("release", "preview").contains(value)) throw new IllegalArgumentException("Invalid update channel");
    }
    public boolean available() { return context.debRequests.isDirectory() && context.debRequests.canWrite(); }
    public void request(String channel) throws Exception {
        channel(channel);
        if (!available()) throw new IllegalStateException("deb updater request service is unavailable");
        try { Files.createFile(new File(context.debRequests, channel + ".request").toPath()); }
        catch (FileAlreadyExistsException e) { throw new IllegalStateException("A deb upgrade is already queued", e); }
    }
    public DebUpgradeStatus status() throws Exception {
        Path status = new File(context.debState, "status.json").toPath();
        return Files.exists(status) ? GSON.fromJson(Files.readString(status), DebUpgradeStatus.class) : null;
    }
    public int install(String channel, boolean json) throws Exception {
        channel(channel);
        if (!"0".equals(commands.run("id", "-u").trim())) throw new IllegalStateException("deb installation must run as root");
        Files.createDirectories(context.debState.toPath());
        Files.createDirectories(context.debWork.toPath());
        try (java.nio.channels.FileChannel lockChannel = java.nio.channels.FileChannel.open(
                new File(context.debState, "install.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock lock = lockChannel.tryLock();
            if (lock == null) throw new IllegalStateException("A deb installation is already running");
            try {
                Path marker = new File(context.debRequests, channel + ".request").toPath();
                long requestedAt = Files.exists(marker, LinkOption.NOFOLLOW_LINKS)
                        ? Files.getLastModifiedTime(marker, LinkOption.NOFOLLOW_LINKS).toMillis() : System.currentTimeMillis();
                Files.deleteIfExists(marker);
                DebUpgradeStatus result = new DebUpgradeStatus();
                result.requestedAt = requestedAt;
                File packageFile = new File(context.debWork, "update.deb");
                try {
                    save(result, "validate", "running", "upgrade.deb.status.validating", json);
                    Version version = new DefaultUpdateCatalog().readVersion(context, channel);
                    if (version.getZipFileSize() <= 0 || version.getZipSha256() == null || !version.getZipSha256().matches("[0-9a-fA-F]{64}"))
                        throw new IllegalStateException("deb manifest must include size and SHA-256");
                    result.version = version.getArtifactVersion(); result.buildId = version.getBuildId();
                    save(result, "download", "running", "upgrade.status.downloading", json);
                    String url = new DefaultUpdateCatalog().selectPackageSource(version, false, context.packageSource);
                    Files.deleteIfExists(packageFile.toPath());
                    if (context.http.download(java.net.URI.create(url), packageFile) != 200
                            || PackageFiles.invalid(packageFile, version.getZipFileSize(), version.getZipSha256(), null))
                        throw new IOException("Invalid deb update package");
                    String name = commands.run("dpkg-deb", "--field", packageFile.toString(), "Package").trim();
                    String packageVersion = commands.run("dpkg-deb", "--field", packageFile.toString(), "Version").trim();
                    String arch = commands.run("dpkg-deb", "--field", packageFile.toString(), "Architecture").trim();
                    String expectedVersion = version.getDebPackageVersion();
                    String legacyVersionPattern = java.util.regex.Pattern.quote(version.getArtifactVersion());
                    if (version.getArtifactVersion().endsWith("-SNAPSHOT")) {
                        legacyVersionPattern = "(?:" + legacyVersionPattern + "|" + java.util.regex.Pattern.quote(
                                version.getArtifactVersion().substring(0, version.getArtifactVersion().length() - 9)) + "~[A-Za-z0-9.+~-]+)";
                    }
                    boolean matchesVersion = expectedVersion != null && !expectedVersion.isEmpty()
                            ? expectedVersion.equals(packageVersion)
                            : packageVersion.equals(version.getArtifactVersion() + "+" + version.getBuildId())
                              || packageVersion.matches(legacyVersionPattern + "\\+[0-9]{14}-" + java.util.regex.Pattern.quote(version.getBuildId()));
                    if (!context.packageName.equals(name) || !matchesVersion
                            || !commands.run("dpkg", "--print-architecture").trim().equals(arch))
                        throw new IllegalStateException("deb package identity does not match the manifest or host");
                    save(result, "update", "running", "upgrade.deb.status.installing", json);
                    commands.run("dpkg", "--install", packageFile.toString());
                    String installed = commands.run("dpkg-query", "--show", "--showformat=${Version}", context.packageName).trim();
                    String installedStatus = commands.run("dpkg-query", "--show", "--showformat=${Status}", context.packageName).trim();
                    if (!packageVersion.equals(installed) || !"install ok installed".equals(installedStatus))
                        throw new IllegalStateException("Installed deb package verification failed");
                    save(result, "restart", "running", "upgrade.status.restarting", json);
                    commands.run("systemctl", "restart", context.serviceName);
                    commands.run("systemctl", "is-active", "--quiet", context.serviceName);
                    String pid = commands.run("systemctl", "show", "--property=MainPID", "--value", context.serviceName).trim();
                    if (!pid.matches("[1-9][0-9]*")) throw new IllegalStateException("Application service did not recover");
                    commands.run("kill", "-0", pid);
                    save(result, "complete", "complete", "upgrade.deb.status.completed", json);
                    return 0;
                } catch (Exception e) {
                    save(result, "error", "error", "upgrade.error.generic", json);
                    System.err.println("deb update failed: " + e.getMessage());
                    return 1;
                } finally { Files.deleteIfExists(packageFile.toPath()); }
            } finally { lock.release(); }
        }
    }
    private void save(DebUpgradeStatus status, String stage, String state, String message, boolean json) throws Exception {
        status.stage = stage; status.status = state; status.message = message; status.updatedAt = System.currentTimeMillis();
        Path target = new File(context.debState, "status.json").toPath();
        Path temporary = Files.createTempFile(context.debState.toPath(), "status-", ".json");
        try {
            Files.writeString(temporary, GSON.toJson(status), StandardCharsets.UTF_8);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        System.out.println(json ? GSON.toJson(status) : stage + ": " + message);
    }
    private static String execute(String... arguments) throws Exception {
        File output = Files.createTempFile("zrlog-deb-command-", ".log").toFile();
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(output).start();
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IOException("deb command timed out"); }
            String result = Files.readString(output.toPath());
            if (process.exitValue() != 0) throw new IOException(arguments[0] + " failed (exit " + process.exitValue() + "): " + result);
            return result;
        } finally { if (process.isAlive()) process.destroyForcibly(); Files.deleteIfExists(output.toPath()); }
    }
}
