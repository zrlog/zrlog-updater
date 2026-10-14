package com.zrlog.updater.war;

import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.common.ArchiveFiles;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class WarUpdater implements ApplicationUpdater {
    private static final Logger LOGGER = Logger.getLogger(WarUpdater.class.getName());
    private final File warFilePath;
    private final UpdateContext context;

    public WarUpdater(UpdateContext context) {
        this.context = context;
        this.warFilePath = Objects.requireNonNull(context.executable);
    }

    private Map<String, String> getCopyFileMap(File tempFilePath) {
        Map<String, String> copyFileMap = new LinkedHashMap<>();
        copyFileMap.put(context.root + "/WEB-INF/db.properties", tempFilePath + "/WEB-INF/");
        copyFileMap.put(context.root + "/WEB-INF/install.lock", tempFilePath + "/WEB-INF/");
        copyFileMap.put(context.root + "/WEB-INF/conf.properties", tempFilePath + "/WEB-INF/");
        copyFileMap.put(context.root + "/attached", tempFilePath.toString());
        copyFileMap.put(context.root + "/favicon.ico", tempFilePath.toString());
        copyFileMap.put(context.root + "/error", tempFilePath.toString());
        return copyFileMap;
    }

    private boolean isClassPathTemplate(String path) {
        return context.bundledTheme.test(path);
    }

    private void fillTemplateCopyInfo(File tempFilePath, Map<String, String> copyFileMap) {
        File templatePath = new File(context.root, "include/templates");
        File[] templates = templatePath.listFiles();
        if (templates != null) {
            for (File template : templates) {
                if (template.isDirectory() && isClassPathTemplate(template.toString().substring(context.root.toString().length()))) {
                    //skip default template folder
                    continue;
                }
                copyFileMap.put(template.toString(), tempFilePath + "/include/templates");
            }
        }
    }

    private void doCopy(Map<String, String> pathMap) throws java.io.IOException {
        for (Map.Entry<String, String> entry : pathMap.entrySet()) {
            if (new File(entry.getKey()).exists()) {
                ArchiveFiles.moveOrCopyFolder(entry.getKey(), entry.getValue(), false);
            }
        }
    }

    @Override
    public String backup() throws java.io.IOException {
        String backupFolder = context.root.getAbsoluteFile().getParentFile().getParentFile() + File.separator + "backup" + File.separator + new SimpleDateFormat("yyyy-MM-dd_HH_mm").format(new Date()) + File.separator;
        new File(backupFolder).mkdirs();
        ArchiveFiles.moveOrCopyFolder(context.root.toString(), backupFolder, false);
        String warPath = context.root.getParent() + File.separator + warFilePath.getName();
        if (new File(warPath).exists()) {
            ArchiveFiles.moveOrCopyFolder(warPath, backupFolder, false);
        }
        return new File(backupFolder).toString();
    }

     @Override
    public void restartProcessAsync(Version upgradeVersion) {
        context.releaseJdbcDrivers.run();
        context.stop.run();
    }

    @Override
    public String getUnzipPath() {
        return "";
    }

    @Override
    public File execFile() {
        return warFilePath;
    }

    @Override
    public UpdaterTypeEnum getType() {
        return UpdaterTypeEnum.WAR;
    }

    @Override
    public String buildUpgradeFile(String upgradeFile, String upgradeKey) throws Exception {
        File tempFilePath = new File(context.temp, upgradeKey);
        ArchiveFiles.unZip(upgradeFile, tempFilePath + File.separator);
        Map<String, String> copyFileMap = getCopyFileMap(tempFilePath);
        fillTemplateCopyInfo(tempFilePath, copyFileMap);
        doCopy(copyFileMap);
        JarPackageUtil.inJar(new ArrayList<>(List.of(tempFilePath)), tempFilePath + File.separator, warFilePath.toString());
        ArchiveFiles.deleteFile(tempFilePath.toString());
        return warFilePath.toString();
    }
    @Override public File getUpdateTempPath() { return context.updateTemp; }
}
