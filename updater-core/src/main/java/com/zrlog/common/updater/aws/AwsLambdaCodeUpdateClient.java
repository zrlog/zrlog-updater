package com.zrlog.common.updater.aws;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

public interface AwsLambdaCodeUpdateClient {

    void putObject(String bucket, String key, File file) throws IOException, InterruptedException;

    JsonObject updateFunctionCode(String functionName, String bucket, String key)
            throws IOException, InterruptedException;
}
