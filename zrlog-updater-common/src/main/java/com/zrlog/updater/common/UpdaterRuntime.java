package com.zrlog.updater.common;

import java.io.File;
import java.util.Objects;

/** Runtime facts supplied by the application; contains no provider or creation callback. */
public final class UpdaterRuntime {
    public final boolean docker, faas, nativeImage, servlet;
    public final String packageType, classPath;
    public final File applicationHome;

    public UpdaterRuntime(boolean docker, boolean faas, String packageType, boolean nativeImage,
                          String classPath, File applicationHome) {
        this(docker, faas, packageType, nativeImage, false, classPath, applicationHome);
    }

    private UpdaterRuntime(boolean docker, boolean faas, String packageType, boolean nativeImage,
                           boolean servlet, String classPath, File applicationHome) {
        this.docker = docker;
        this.faas = faas;
        this.packageType = packageType;
        this.nativeImage = nativeImage;
        this.servlet = servlet;
        this.classPath = Objects.requireNonNull(classPath);
        this.applicationHome = Objects.requireNonNull(applicationHome);
    }

    public static UpdaterRuntime servlet(File applicationHome) {
        return new UpdaterRuntime(false, false, "war", false, true, "", applicationHome);
    }
}
