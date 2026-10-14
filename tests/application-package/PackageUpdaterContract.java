import com.zrlog.common.updater.PackageDownloadSource;
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
        if (!legacy.getZipDownloadUrl().equals(PackageDownloadSource.select(legacy, false, "dl"))) {
            throw new AssertionError("The packaged base facade cannot use updater SPI");
        }
        System.out.println(args[0] + " packaged updater discovery, messages and host compatibility passed");
    }
}
