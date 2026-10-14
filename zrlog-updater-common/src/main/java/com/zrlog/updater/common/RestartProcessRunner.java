package com.zrlog.updater.common;


import com.zrlog.updater.spi.UpdateContext;

import java.util.logging.Logger;

public final class RestartProcessRunner {

    private static final Logger LOGGER = Logger.getLogger(RestartProcessRunner.class.getName());

    private RestartProcessRunner() {
    }

    public static void restartAsync(UpdateContext context, ProcessStarter processStarter) {
        Thread thread = new Thread(() -> {
            try {
                context.stop.run();
                Thread.sleep(2000);
                processStarter.start();
                context.exit.accept(0);
            } catch (Exception e) {
                LOGGER.warning("Restart error " + e.getMessage());
            }
        }, "zrlog-upgrade-restart");
        thread.setDaemon(false);
        thread.start();
    }

    public interface ProcessStarter {

        void start() throws Exception;
    }
}
