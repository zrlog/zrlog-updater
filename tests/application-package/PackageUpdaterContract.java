import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateCatalogs;

/** Verifies updater discovery and host compatibility against actual application package JARs. */
class PackageUpdaterContract {
    public static void main(String[] args) throws Exception {
        UpdateCatalogs.getInstance();
        java.util.Set<com.zrlog.common.UpdaterTypeEnum> expected = args[0].equals("ZIP")
                ? java.util.Set.of(com.zrlog.common.UpdaterTypeEnum.ZIP, com.zrlog.common.UpdaterTypeEnum.DOCKER)
                : java.util.Set.of(com.zrlog.common.UpdaterTypeEnum.WAR);
        for (com.zrlog.common.UpdaterTypeEnum type : com.zrlog.common.UpdaterTypeEnum.values()) {
            if (com.zrlog.updater.spi.UpdateProviders.find(type).isPresent() != expected.contains(type)) {
                throw new AssertionError("Unexpected packaged updater: " + type);
            }
        }
        for (String resource : com.zrlog.updater.spi.UpdateProviders.messageResources("zh_CN")) {
            try (java.io.InputStream input = PackageUpdaterContract.class.getResourceAsStream(resource)) {
                if (input == null) throw new AssertionError("Missing updater messages: " + resource);
            }
        }
        String message = args[0].equals("ZIP") ? "upgrade.zip.status.unzipping" : "upgrade.status.backingUp";
        if (com.zrlog.util.I18nUtil.getBackend().get(message) == null) {
            throw new AssertionError("The packaged host cannot load updater messages");
        }
        Version legacy = new Version();
        legacy.setZipDownloadUrl("https://dl.zrlog.com/release/zrlog.zip");
        if (!legacy.getZipDownloadUrl().equals(UpdateCatalogs.getInstance().selectPackageSource(legacy, false, "dl"))) {
            throw new AssertionError("The packaged catalog cannot use updater SPI");
        }
        java.io.File target = new java.io.File(args[1]);
        String classPath = System.getProperty("java.class.path");
        try {
            com.zrlog.common.Updater updater;
            if (args[0].equals("ZIP")) {
                System.setProperty("java.class.path", target.toString());
                updater = com.zrlog.web.ApplicationUpdaterFactory.create(new String[]{"--port=18081"}, null);
            } else {
                updater = com.zrlog.web.ApplicationUpdaterFactory.createWar(target);
            }
            com.zrlog.common.UpdaterTypeEnum type = args[0].equals("ZIP")
                    ? com.zrlog.common.UpdaterTypeEnum.ZIP : com.zrlog.common.UpdaterTypeEnum.WAR;
            if (updater == null || updater.getType() != type || !target.getCanonicalFile().equals(updater.execFile().getCanonicalFile())) {
                throw new AssertionError("The packaged application cannot automatically assemble its updater");
            }
            if (!updater.execFile().equals(com.zrlog.common.updater.UpdaterHosts.context(updater).executable)) {
                throw new AssertionError("The legacy adapter lost the resolved startup target");
            }
        } finally {
            System.setProperty("java.class.path", classPath);
        }
        System.out.println(args[0] + " packaged updater automatic assembly, messages and host compatibility passed");
    }
}
