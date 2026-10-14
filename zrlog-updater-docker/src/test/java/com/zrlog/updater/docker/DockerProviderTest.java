package com.zrlog.updater.docker;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.spi.UpdateProviders;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class DockerProviderTest {
    @Test public void loadsOnlyItsProviderAndOwnMessages() throws Exception {
        assertEquals(UpdaterTypeEnum.DOCKER, UpdateProviders.get(UpdaterTypeEnum.DOCKER).type());
        for (UpdaterTypeEnum type : UpdaterTypeEnum.values()) {
            assertEquals(type == UpdaterTypeEnum.DOCKER, UpdateProviders.find(type).isPresent());
        }
        Properties english = language("en_US"), chinese = language("zh_CN");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        assertFalse(chinese.isEmpty());
    }
    private Properties language(String locale) throws Exception {
        Properties result = new Properties();
        assertEquals(2, UpdateProviders.messageResources(locale).size());
        for (String resource : UpdateProviders.messageResources(locale)) {
            assertTrue(resource, resource.startsWith("/i18n/updater/common/") || resource.startsWith("/i18n/updater/docker/"));
            try (java.io.InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(resource, input); result.load(input);
            }
        }
        return result;
    }
}
