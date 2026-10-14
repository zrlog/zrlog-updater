package com.zrlog.updater.common;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateProvider;
import com.zrlog.updater.spi.UpdateProviders;
import com.zrlog.updater.spi.UpdateSession;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Selects the deployment updater and its executable; the host supplies runtime adaptation. */
public final class UpdaterUtils {
    private static final Logger LOGGER = Logger.getLogger(UpdaterUtils.class.getName());

    private UpdaterUtils() { }

    public static UpdaterTypeEnum selectType(boolean dockerMode, boolean faasMode,
                                             String packageType, boolean nativeImage) {
        if (dockerMode) return UpdaterTypeEnum.DOCKER;
        if (faasMode) return UpdaterTypeEnum.FAAS;
        if ("deb".equals(packageType)) return UpdaterTypeEnum.DEB;
        return nativeImage ? UpdaterTypeEnum.NATIVE_IMAGE : UpdaterTypeEnum.ZIP;
    }

    /** Selects and creates the installed SPI provider using the supplied host capabilities. */
    public static UpdateSession create(UpdaterRuntime runtime, UpdateContext host) {
        return create(runtime, host, UpdateProvider.class.getClassLoader());
    }

    static UpdateSession create(UpdaterRuntime runtime, UpdateContext host, ClassLoader loader) {
        UpdaterTypeEnum type = runtime.servlet ? UpdaterTypeEnum.WAR
                : selectType(runtime.docker, runtime.faas, runtime.packageType, runtime.nativeImage);
        File target = host.executable;
        try {
            if (type == UpdaterTypeEnum.ZIP) {
                target = findLauncherJar(runtime.classPath, runtime.applicationHome);
                if (target == null) {
                    LOGGER.fine("No unambiguous ZrLog launcher JAR found; ZIP updater is unavailable");
                    return null;
                }
            }
            UpdateContext.Builder context = new UpdateContext.Builder(host);
            context.executable = target;
            if (type == UpdaterTypeEnum.DOCKER && host.docker != null) {
                context.metadataOrigin = host.docker.metadataOrigin;
                context.manifestFilename = host.docker.manifestFilename;
            }
            return UpdateProviders.create(loader, type, context.build());
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Create " + type + " updater failed, target file: " + target, e);
            return null;
        }
    }

    static File findLauncherJar(String classPath, File applicationHome) {
        Set<File> launchers = new LinkedHashSet<>();
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            File candidate = launcherJar(new File(entry));
            if (candidate != null) launchers.add(candidate);
        }
        if (launchers.size() == 1) return launchers.iterator().next();
        if (!launchers.isEmpty()) return null;
        // Classpath launches may omit the distribution's launcher from the classpath.
        return launcherJar(new File(applicationHome, "zrlog-starter.jar"));
    }

    private static File launcherJar(File file) {
        if (!file.isFile() || !file.getName().endsWith(".jar")) return null;
        try (JarFile jar = new JarFile(file)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null && "com.zrlog.web.Application".equals(
                    manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))) {
                return file.getCanonicalFile();
            }
        } catch (IOException ignored) {
            // A missing or unreadable dependency must never become the restart target.
        }
        return null;
    }
}
