package com.zrlog.updater.core.local;


import com.zrlog.updater.spi.UpdateContext;

import java.util.logging.Logger;

class RestartProcessRunner {

    private static final Logger LOGGER = Logger.getLogger(RestartProcessRunner.class.getName());

    private RestartProcessRunner() {
    }

    static void restartAsync(UpdateContext context, ProcessStarter processStarter) {
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

    interface ProcessStarter {

        void start() throws Exception;
    }
}
