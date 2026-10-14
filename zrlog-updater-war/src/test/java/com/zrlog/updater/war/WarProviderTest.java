package com.zrlog.updater.war;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.spi.UpdateProviders;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class WarProviderTest {
    @Test public void loadsOnlyItsProviderAndOwnMessages() throws Exception {
        assertEquals(UpdaterTypeEnum.WAR, UpdateProviders.get(UpdaterTypeEnum.WAR).type());
        for (UpdaterTypeEnum type : UpdaterTypeEnum.values()) {
            assertEquals(type == UpdaterTypeEnum.WAR, UpdateProviders.find(type).isPresent());
        }
        Properties english = language("en_US"), chinese = language("zh_CN");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        assertFalse(chinese.isEmpty());
    }
    private Properties language(String locale) throws Exception {
        Properties result = new Properties();
        assertEquals(2, UpdateProviders.messageResources(locale).size());
        for (String resource : UpdateProviders.messageResources(locale)) {
            assertTrue(resource, resource.startsWith("/i18n/updater/common/") || resource.startsWith("/i18n/updater/war/"));
            try (java.io.InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(resource, input); result.load(input);
            }
        }
        return result;
    }
    @Test public void releasesJdbcDriversBeforeStoppingTheHost() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        com.zrlog.updater.spi.UpdateContext.Builder host = new com.zrlog.updater.spi.UpdateContext.Builder();
        host.executable = new java.io.File("ROOT.war");
        host.releaseJdbcDrivers = () -> calls.add("jdbc");
        host.stop = () -> calls.add("stop");
        UpdateProviders.create(UpdaterTypeEnum.WAR, host.build()).updater.restartProcessAsync(null);
        assertEquals(java.util.List.of("jdbc", "stop"), calls);
    }
}
