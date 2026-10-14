package com.zrlog.updater.zip;

import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.common.RestartProcessRunner;
import com.zrlog.updater.spi.ApplicationUpdater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ZipUpdater implements ApplicationUpdater {

    private static final Logger LOGGER = Logger.getLogger(ZipUpdater.class.getName());
    private final String[] args;
    private final File execFile;
    private final UpdateContext context;

    public ZipUpdater(UpdateContext context) {
        this.context = context;
        this.args = context.arguments;
        this.execFile = Objects.requireNonNull(context.executable);
    }

    @Override
    public File execFile() {
        return execFile;
    }

    @Override
    public UpdaterTypeEnum getType() {
        return UpdaterTypeEnum.ZIP;
    }

    List<String> buildRestartCommand() {
        List<String> inputArguments = context.jvmArguments;
        // 构造完整的命令
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("java");
        cmdArgs.addAll(inputArguments.stream().filter(e -> !e.startsWith("-javaagent")).collect(Collectors.toList()));
        cmdArgs.add("-jar");
        cmdArgs.add(execFile.toString());
        cmdArgs.addAll(Arrays.asList(args));
        if (cmdArgs.stream().noneMatch(e -> e.startsWith("--port="))) {
            cmdArgs.add("--port=" + context.port());
        }
        return cmdArgs;
    }

    public void restartProcessAsync(Version upgradeVersion) {
        List<String> cmdArgs = buildRestartCommand();
        LOGGER.info("ZrLog file updated. exec cmd\n" + String.join(" ", cmdArgs));
        RestartProcessRunner.restartAsync(context, () -> Runtime.getRuntime().exec(cmdArgs.toArray(new String[0])));
    }

    @Override
    public String getUnzipPath() {
        return context.root.toString();
    }

    @Override public File getUpdateTempPath() { return context.updateTemp; }

}
