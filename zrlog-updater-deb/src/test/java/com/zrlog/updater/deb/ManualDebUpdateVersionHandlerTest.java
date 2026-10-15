package com.zrlog.updater.deb;

import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualDebUpdateVersionHandlerTest {
    @Test public void manualGuidanceSelectsTheReleaseOrPreviewCommandWithoutInstalling() {
        for (String value : new String[]{null, "4.0.0", "4.0.0-SNAPSHOT"}) {
            Version version = value == null ? null : new Version();
            if (version != null) version.setVersion(value);
            var handler = new DebProvider().manualHandler(new UpdateContext.Builder().build(),
                    new UpdateRequest(null, version, null, null, UpgradeProgressListener.NONE,
                            key -> "Use /etc/init.d/zrlog upgrade"));
            assertFalse(handler.isFinish());
            handler.doHandle();
            assertTrue(handler.isFinish());
            assertEquals("Use /etc/init.d/zrlog upgrade" +
                    (value != null && value.endsWith("-SNAPSHOT") ? "-preview" : ""), handler.getMessage());
        }
    }
}
