package com.zrlog.updater.spi;

import com.zrlog.common.UpdaterTypeEnum;
import java.util.Optional;
import java.util.ServiceLoader;

public final class UpdateProviders {
    private UpdateProviders() { }
    public static java.util.Set<String> messageResources(String locale) {
        java.util.Set<String> resources = new java.util.LinkedHashSet<>();
        for (UpdateProvider provider : ServiceLoader.load(UpdateProvider.class, UpdateProvider.class.getClassLoader())) {
            for (String bundle : provider.messageBundles()) resources.add(bundle + "_" + locale + ".properties");
        }
        return resources;
    }
    public static Optional<UpdateProvider> find(UpdaterTypeEnum type) {
        return load(UpdateProvider.class.getClassLoader(), type);
    }
    public static Optional<UpdateProvider> load(ClassLoader loader, UpdaterTypeEnum type) {
        UpdateProvider found = null;
        for (UpdateProvider provider : ServiceLoader.load(UpdateProvider.class, loader)) {
            if (provider.type() != type) continue;
            if (found != null) throw new IllegalStateException("Multiple updater implementations for " + type);
            found = provider;
        }
        return Optional.ofNullable(found);
    }
    public static UpdateProvider get(UpdaterTypeEnum type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No updater implementation installed for " + type));
    }
}
