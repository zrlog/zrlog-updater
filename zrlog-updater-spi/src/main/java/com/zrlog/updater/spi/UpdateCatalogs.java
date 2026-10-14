package com.zrlog.updater.spi;

import java.util.Optional;
import java.util.ServiceLoader;

/** Discovery is independent of Web modules. A missing implementation fails only when requested. */
public final class UpdateCatalogs {
    private UpdateCatalogs() { }

    private static final class Holder {
        private static final Optional<UpdateCatalog> INSTANCE = load(UpdateCatalog.class.getClassLoader());
    }

    public static UpdateCatalog getInstance() {
        return Holder.INSTANCE.orElseThrow(() -> new IllegalStateException(
                "No ZrLog updater catalog implementation is installed"));
    }

    public static Optional<UpdateCatalog> load(ClassLoader loader) {
        UpdateCatalog found = null;
        for (UpdateCatalog candidate : ServiceLoader.load(UpdateCatalog.class, loader)) {
            if (found != null) {
                throw new IllegalStateException("Multiple ZrLog updater catalog implementations are installed: "
                        + found.getClass().getName() + ", " + candidate.getClass().getName());
            }
            found = candidate;
        }
        return Optional.ofNullable(found);
    }
}
