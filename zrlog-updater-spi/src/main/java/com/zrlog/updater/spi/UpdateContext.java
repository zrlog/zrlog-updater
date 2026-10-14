package com.zrlog.updater.spi;

import com.zrlog.common.updater.aws.AwsLambdaUpdateConfig;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Host capabilities. This is never populated from an admin HTTP request. */
public final class UpdateContext {
    public final File root, temp, updateTemp, executable;
    public final String[] arguments;
    public final List<String> jvmArguments;
    public final int port;
    public final java.util.function.IntSupplier currentPort;
    public final String fileArch, metadataOrigin, manifestFilename, packageSource;
    public final boolean lambda;
    public final AwsLambdaUpdateConfig aws;
    public final Predicate<String> bundledTheme;
    public final Runnable stop;
    public final Runnable releaseJdbcDrivers;
    public final IntConsumer exit;
    public final UpdateHttp http;
    public final DockerConnection docker;
    public final File debRequests, debState, debWork;
    public final String packageName, serviceName;

    public UpdateContext(Builder b) {
        root = Objects.requireNonNull(b.root); temp = Objects.requireNonNull(b.temp);
        updateTemp = Objects.requireNonNull(b.updateTemp); executable = b.executable;
        arguments = b.arguments.clone(); jvmArguments = List.copyOf(b.jvmArguments); port = b.port; currentPort = b.currentPort;
        fileArch = b.fileArch; metadataOrigin = b.metadataOrigin; manifestFilename = b.manifestFilename;
        packageSource = b.packageSource; lambda = b.lambda; aws = b.aws;
        bundledTheme = b.bundledTheme; stop = b.stop; releaseJdbcDrivers = b.releaseJdbcDrivers;
        exit = b.exit; http = b.http; docker = b.docker;
        debRequests = b.debRequests; debState = b.debState; debWork = b.debWork;
        packageName = b.packageName; serviceName = b.serviceName;
    }
    public int port() { return currentPort == null ? port : currentPort.getAsInt(); }

    public static final class Builder {
        public Builder() { }
        public Builder(UpdateContext c) {
            root=c.root; temp=c.temp; updateTemp=c.updateTemp; executable=c.executable;
            arguments=c.arguments; jvmArguments=c.jvmArguments; port=c.port; currentPort=c.currentPort;
            fileArch=c.fileArch; metadataOrigin=c.metadataOrigin; manifestFilename=c.manifestFilename; packageSource=c.packageSource;
            lambda=c.lambda; aws=c.aws; bundledTheme=c.bundledTheme; stop=c.stop; releaseJdbcDrivers=c.releaseJdbcDrivers;
            exit=c.exit; http=c.http; docker=c.docker;
            debRequests=c.debRequests; debState=c.debState; debWork=c.debWork; packageName=c.packageName; serviceName=c.serviceName;
        }
        public File root = new File("."), temp = new File("temp"), updateTemp = new File("conf/update-temp"), executable;
        public String[] arguments = new String[0];
        public List<String> jvmArguments = List.of();
        public int port = 8080;
        public java.util.function.IntSupplier currentPort;
        public String fileArch = "", metadataOrigin = "", manifestFilename = "", packageSource = "dl";
        public boolean lambda;
        public AwsLambdaUpdateConfig aws = AwsLambdaUpdateConfig.fromEnvironment();
        public Predicate<String> bundledTheme = path -> false;
        public Runnable stop = () -> { };
        public Runnable releaseJdbcDrivers = () -> { };
        public IntConsumer exit = System::exit;
        public UpdateHttp http;
        public DockerConnection docker;
        public File debRequests = new File("/var/lib/zrlog/upgrade-request");
        public File debState = new File("/var/lib/zrlog-updater");
        public File debWork = new File("/var/cache/zrlog-updater");
        public String packageName = "zrlog", serviceName = "zrlog.service";
        public UpdateContext build() { return new UpdateContext(this); }
    }
}
