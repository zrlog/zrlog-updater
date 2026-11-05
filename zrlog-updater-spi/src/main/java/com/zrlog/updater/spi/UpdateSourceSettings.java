package com.zrlog.updater.spi;

import java.util.ArrayList;
import java.util.List;

/** Selectable update sources. IDs are validated by the owning provider, never used as shell input. */
public final class UpdateSourceSettings {
    public boolean managedByUpdater;
    public String source;
    public String channel;
    public String label;
    public String description;
    public String errorCode;
    public String errorMessage;
    public List<Option> options = new ArrayList<>();

    public static final class Option {
        public String value;
        public String label;
        public Option() { }
        public Option(String value, String label) { this.value = value; this.label = label; }
    }
}
