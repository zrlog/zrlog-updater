package com.zrlog.updater.spi;

import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import java.io.File;
import java.util.Objects;
import java.util.function.Function;

public final class UpdateRequest {
    public final File file;
    public final Version version;
    public final String key;
    public final ApplicationUpdater updater;
    public final UpgradeProgressListener progress;
    public final Function<String, String> text;
    public UpdateRequest(File file, Version version, String key, ApplicationUpdater updater,
                         UpgradeProgressListener progress, Function<String, String> text) {
        this.file = file; this.version = version; this.key = key; this.updater = updater;
        this.progress = Objects.requireNonNull(progress); this.text = Objects.requireNonNull(text);
    }
}
