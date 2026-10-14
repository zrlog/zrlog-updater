package com.zrlog.updater.core;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.updater.core.deb.DebPackageUpdater;
import com.zrlog.updater.core.docker.DockerImageUpdater;
import com.zrlog.updater.core.handle.*;
import com.zrlog.updater.core.local.*;
import com.zrlog.updater.spi.*;
import java.io.File;

/** One registration per deployment type allows providers to be replaced independently. */
public final class ApplicationUpdateProviders {
    private ApplicationUpdateProviders() { }
    public abstract static class PackageProvider implements UpdateProvider {
        public java.util.List<String> messageBundles() {
            return java.util.List.of("/i18n/updater/common/backend", "/i18n/updater/" + type().name().toLowerCase(java.util.Locale.ROOT) + "/backend");
        }
        public File download(UpdateContext context, UpdateRequest request) throws Exception { return PackageFiles.download(context, request); }
        public boolean invalidPackage(File file, long size, String sha, String md5) { return PackageFiles.invalid(file, size, sha, md5); }
        public String checksum(File file, String algorithm) throws Exception { return PackageFiles.checksum(file, algorithm); }
    }
    public static final class Zip extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.ZIP; }
        public ApplicationUpdater create(UpdateContext context) { return new ZipUpdater(context); }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) {
            return new ZipUpdateVersionHandle(context, r.file, r.text, r.version, r.progress, r.updater);
        }
    }
    public static final class Native extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.NATIVE_IMAGE; }
        public ApplicationUpdater create(UpdateContext context) { return new NativeImageUpdater(context); }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) {
            return new ZipUpdateVersionHandle(context, r.file, r.text, r.version, r.progress, r.updater);
        }
    }
    public static final class War extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.WAR; }
        public ApplicationUpdater create(UpdateContext context) { return new WarUpdater(context); }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) {
            return new WarUpdateVersionHandle(r.file, r.text, r.key, r.version, r.progress, r.updater);
        }
    }
    public static final class Faas extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.FAAS; }
        public ApplicationUpdater create(UpdateContext context) { return new HostedUpdater(context, type()); }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) {
            return new FaasUpdateVersionHandler(context, r.text, r.version, r.file, r.progress);
        }
    }
    public static final class Docker extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.DOCKER; }
        public ApplicationUpdater create(UpdateContext context) { return new DockerImageUpdater(context); }
        public File download(UpdateContext context, UpdateRequest request) { throw new UnsupportedOperationException("Docker pulls images through its service"); }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) {
            return new ManualUpdateVersionHandler(r.text.apply("upgrade.tips.docker"));
        }
    }
    public static final class Deb extends PackageProvider {
        public UpdaterTypeEnum type() { return UpdaterTypeEnum.DEB; }
        public ApplicationUpdater create(UpdateContext context) { return new DebPackageUpdater(context); }
        public File download(UpdateContext context, UpdateRequest request) { return null; }
        public UpdateVersionHandler handler(UpdateContext context, UpdateRequest r) { return new DebUpdateVersionHandler(r); }
    }
}
