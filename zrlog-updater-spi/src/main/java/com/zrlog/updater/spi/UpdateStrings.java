package com.zrlog.updater.spi;

public final class UpdateStrings {
    private UpdateStrings() { }
    public static boolean isEmpty(String value) { return value == null || value.isEmpty(); }
    public static boolean isNotEmpty(String value) { return !isEmpty(value); }
}
