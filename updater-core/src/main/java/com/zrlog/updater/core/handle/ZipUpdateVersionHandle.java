package com.zrlog.updater.core.handle;

import com.zrlog.updater.core.local.ArchiveFiles;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;

import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * 更新ZrLog，具体的流程看 run() 里面有详细流程
 */
public class ZipUpdateVersionHandle implements Serializable, UpdateVersionHandler {

    private String message = "";
    private final File file;
    private boolean finish;
    private final java.util.function.Function<String, String> backendRes;
    private final Version upgradeVersion;
    private final UpgradeProgressListener progressListener;
    private final ApplicationUpdater updater;
    private final UpdateContext context;

    public ZipUpdateVersionHandle(UpdateContext context, File file, java.util.function.Function<String, String> backendRes,
                                  Version upgradeVersion, UpgradeProgressListener progressListener, ApplicationUpdater updater) {
        this.context = context;
        this.file = file;
        this.backendRes = backendRes;
        this.upgradeVersion = upgradeVersion;
        this.progressListener = progressListener;
        this.updater = updater;
    }

    /**
     * 提示更新进度
     */
    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isFinish() {
        return finish;
    }

    /**
     * 修改文件的属性，让其可以快速执行
     */
    private void changeExecFile() {
        //非 unix，无法运行 chmod
        if (!Objects.equals(File.separator, "/")) {
            return;
        }
        List<String> execFiles = new ArrayList<>();
        execFiles.add(context.root + "/bin/start.sh");
        execFiles.add(context.root + "/bin/run.sh");
        execFiles.add(context.root + "/bin/start.bat");
        for (String execFile : execFiles) {
            try {
                Process process = Runtime.getRuntime().exec(Arrays.asList("chmod", "a+x", execFile).toArray(new String[0]));
                process.waitFor();
            } catch (Exception e) {
                publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR, "upgrade.zip.error.filePermissions",
                        execFile);
            }
        }
    }

    @Override
    public void doHandle() {
        ApplicationUpdater currentUpdater = Objects.requireNonNull(updater);
        publishStatus(UpgradeProgressEvent.STAGE_UNZIP, UpgradeProgressEvent.STATUS_RUNNING,
                extractKey("unzipping"), file.getName());
        try {
            ArchiveFiles.unZip(file.toString(), currentUpdater.getUnzipPath());
        } catch (Exception e) {
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR, "upgrade.error.unzip",
                    e.getMessage());
            return;
        }
        try {
            if (updater.getType() == com.zrlog.common.UpdaterTypeEnum.ZIP) changeExecFile();
            publishStatus(UpgradeProgressEvent.STAGE_UNZIP, UpgradeProgressEvent.STATUS_COMPLETE,
                    extractKey("unzipped"), null);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.status.restarting", null);
            if (finish) {
                return;
            }
            currentUpdater.restartProcessAsync(upgradeVersion);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.restartSubmitted", null);
            publishStatus(UpgradeProgressEvent.STAGE_COMPLETE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.completed", null);
            finish = true;
        } catch (Exception e) {
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR,
                    "upgrade.error.generic", e.getMessage());
        } finally {
            file.delete();
        }
    }

    private String extractKey(String status) {
        return (updater.getType() == com.zrlog.common.UpdaterTypeEnum.NATIVE_IMAGE ? "upgrade.native.status." : "upgrade.zip.status.") + status;
    }

    private void publishStatus(String stage, String status, String key, String detail) {
        String progressMessage = backendRes.apply(key);
        message = Objects.nonNull(detail) ? progressMessage + " " + detail : progressMessage;
        try {
            progressListener.onProgress(UpgradeProgressEvent.EVENT,
                    UpgradeProgressEvent.data(stage, status, progressMessage, detail));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
