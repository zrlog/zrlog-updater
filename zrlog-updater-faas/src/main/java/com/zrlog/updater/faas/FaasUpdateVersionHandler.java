package com.zrlog.updater.faas;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zrlog.updater.spi.UpdateStrings;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.common.PackageFiles;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.aws.AwsLambdaUpdateConfig;
import com.zrlog.common.updater.aws.AwsLambdaUpdateClient;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.Objects;

public class FaasUpdateVersionHandler implements UpdateVersionHandler {

    private static final String S3_BUCKET_ENV = "LAMBDA_S3_REPO_NAME";
    private static final String DEFAULT_S3_BUCKET = "zrlog-update-bucket";
    private final Version version;
    private final Function<String, String> backend;
    private final UpdateContext context;
    private final File packageFile;
    private final UpgradeProgressListener progressListener;
    private final AwsLambdaUpdateConfig lambdaUpdateConfig;
    private String message = "";
    private boolean finish;

    public FaasUpdateVersionHandler(UpdateContext context, Function<String, String> backend, Version version, File packageFile, UpgradeProgressListener progressListener) {
        this.context = context; this.version = version; this.backend = backend; this.packageFile = packageFile;
        this.progressListener = progressListener; this.lambdaUpdateConfig = context.aws;
    }

    private String getS3UpdateShell(String downloadUrl, String functionName) {
        return getS3UpdateShell(downloadUrl, functionName, System.getenv(S3_BUCKET_ENV));
    }

    private String getS3UpdateShell(String downloadUrl, String functionName, String s3Bucket) {
        String finalName = "zrlog-" + context.fileArch + "-latest.zip";
        String lambdaRepoName = Objects.requireNonNullElse(s3Bucket, DEFAULT_S3_BUCKET);
        return "export LAMBDA_S3_REPO_NAME=\"" + lambdaRepoName + "\"\n" +
                "export AWS_LAMBDA_FUNCTION_NAME=\"" + functionName + "\"\n" +
                "LOCAL_FILE=\"${TMPDIR:-/tmp}/" + finalName + "\"\n" +
                "UA=\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36\"\n" +
                "wget --user-agent=\"${UA}\" -O \"${LOCAL_FILE}\" \"" + downloadUrl + "\"\n" +
                "aws s3 cp \"${LOCAL_FILE}\" s3://${LAMBDA_S3_REPO_NAME}/" + finalName + "\n" +
                "aws lambda update-function-code \\\n" +
                "  --function-name ${AWS_LAMBDA_FUNCTION_NAME} \\\n" +
                "  --s3-bucket ${LAMBDA_S3_REPO_NAME} \\\n" +
                "  --s3-key " + finalName + "\n\n";
    }

    public boolean isOnlineUpgradeSupported() {
        return context.lambda && lambdaUpdateConfig.missingRequiredFields().isEmpty();
    }

    private boolean isOnlineUpgradeSupportedByCurrentConfig() {
        return context.lambda && lambdaUpdateConfig.missingRequiredFields().isEmpty();
    }

    @Override
    public String getMessage() {
        if (UpdateStrings.isNotEmpty(message)) {
            return message;
        }
        if (context.lambda) {
            List<String> missing = lambdaUpdateConfig.missingRequiredFields();
            if (!missing.isEmpty()) {
                return "#### " + backend.apply("upgrade.lambda.online.missing") + "\n" +
                        "- " + backend.apply("upgrade.lambda.online.missingEnv") + " " +
                        String.join(", ", missing) + "\n\n" + getManualUpgradeMessage();
            }
        }
        return getManualUpgradeMessage();
    }

    private String getManualUpgradeMessage() {
        String downloadUrl = version.getZipDownloadUrl();
        if (context.lambda) {
            return "#### " + backend.apply("upgrade.lambda.title") + "\n```bash\n" +
                    getS3UpdateShell(downloadUrl, lambdaUpdateConfig.getFunctionName(), lambdaUpdateConfig.getS3Bucket()) +
                    "```";
        }
        return "[" + backend.apply("upgrade.lambda.download") + "](" + downloadUrl + ")";
    }

    @Override
    public boolean isFinish() {
        return finish;
    }

    @Override
    public void doHandle() {
        if (finish) {
            return;
        }
        if (!isOnlineUpgradeSupportedByCurrentConfig()) {
            message = getMessage();
            finish = true;
            publishStatus(UpgradeProgressEvent.STAGE_MANUAL, UpgradeProgressEvent.STATUS_MANUAL, message, null);
            return;
        }
        try {
            File upgradePackage = getUpgradePackage();
            publishStatus(UpgradeProgressEvent.STAGE_VALIDATE, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.lambda.status.validating",
                    upgradePackage.getName());
            validatePackage(upgradePackage);
            publishStatus(UpgradeProgressEvent.STAGE_VALIDATE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.lambda.status.validating",
                    upgradePackage.getName());
            String bucket = lambdaUpdateConfig.getS3Bucket();
            String key = getS3ObjectKey();
            String s3Object = "s3://" + bucket + "/" + key;
            publishStatus(UpgradeProgressEvent.STAGE_UPLOAD, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.lambda.status.uploading",
                    s3Object);
            AwsLambdaUpdateClient client = new AwsLambdaUpdateClient(lambdaUpdateConfig.getCredentials(),
                    lambdaUpdateConfig.getRegion(), context.http);
            client.putObject(bucket, key, upgradePackage);
            publishStatus(UpgradeProgressEvent.STAGE_UPLOAD, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.lambda.status.uploading",
                    s3Object);
            publishStatus(UpgradeProgressEvent.STAGE_UPDATE, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.lambda.status.updating",
                    lambdaUpdateConfig.getFunctionName());
            JsonObject response = client.updateFunctionCode(lambdaUpdateConfig.getFunctionName(), bucket, key);
            String lastUpdateStatus = getJsonValue(response, "LastUpdateStatus");
            if ("Failed".equalsIgnoreCase(lastUpdateStatus)) {
                throw new IllegalStateException(getJsonValue(response, "LastUpdateStatusReason"));
            }
            String lastUpdateDetail = UpdateStrings.isEmpty(lastUpdateStatus) ? "" :
                    "LastUpdateStatus=" + lastUpdateStatus;
            publishStatus(UpgradeProgressEvent.STAGE_UPDATE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.lambda.status.updating",
                    lastUpdateDetail);
            publishStatus(UpgradeProgressEvent.STAGE_COMPLETE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.lambda.status.updated",
                    lastUpdateDetail);
            finish = true;
        } catch (Exception e) {
            message = "- " + backend.apply("upgrade.lambda.online.error") + " " + e.getMessage() + "\n\n" +
                    getManualUpgradeMessage();
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR, message, null);
        }
    }

    private File getUpgradePackage() {
        if (Objects.nonNull(packageFile) && packageFile.exists()) {
            return packageFile;
        }
        throw new IllegalStateException("Missing downloaded update package");
    }

    private void validatePackage(File file) throws Exception {
        if (!file.exists() || file.length() <= 0) {
            throw new IllegalStateException("Missing update package " + file);
        }
        if (version.getZipFileSize() > 0 && file.length() != version.getZipFileSize()) {
            throw new IllegalStateException("Invalid package size, expected=" + version.getZipFileSize() +
                    ", actual=" + file.length());
        }
        if (PackageFiles.invalid(file, version.getZipFileSize(), version.getZipSha256(), version.getZipMd5sum())) {
            throw new IllegalStateException("Invalid package checksum");
        }
    }

    private void publishStatus(String stage, String status, String key, String detail) {
        String progressMessage = Objects.toString(backend.apply(key), key);
        if (key.startsWith("####") || key.startsWith("- ") || key.contains("\n")) {
            progressMessage = key;
        }
        message = UpdateStrings.isNotEmpty(detail) ? progressMessage + " " + detail : progressMessage;
        try {
            progressListener.onProgress(UpgradeProgressEvent.EVENT,
                    UpgradeProgressEvent.data(stage, status, progressMessage, detail));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getS3ObjectKey() {
        String versionTag = version.getBuildId();
        if (UpdateStrings.isEmpty(versionTag)) {
            versionTag = version.getVersion();
        }
        if (UpdateStrings.isEmpty(versionTag)) {
            versionTag = "latest";
        }
        return "zrlog-" + context.fileArch + "-" +
                versionTag.replaceAll("[^A-Za-z0-9._-]", "-") + ".zip";
    }

    private static String getJsonValue(JsonObject jsonObject, String key) {
        JsonElement element = jsonObject.get(key);
        if (Objects.isNull(element) || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

}
