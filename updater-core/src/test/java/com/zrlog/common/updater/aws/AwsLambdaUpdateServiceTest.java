package com.zrlog.common.updater.aws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AwsLambdaUpdateServiceTest {

    @Test
    public void shouldBuildConfigFromEnvironmentMap() {
        Map<String, String> env = new HashMap<>();
        env.put(AwsLambdaUpdateConfig.ACCESS_KEY_ID_ENV, "env-ak");
        env.put(AwsLambdaUpdateConfig.SECRET_ACCESS_KEY_ENV, "env-sk");
        env.put(AwsLambdaUpdateConfig.SESSION_TOKEN_ENV, "env-token");
        env.put(AwsLambdaUpdateConfig.DEFAULT_REGION_ENV, "us-west-1");
        env.put(AwsLambdaUpdateConfig.FUNCTION_NAME_ENV, "xiaochun-zrlog-com");
        env.put(AwsLambdaUpdateConfig.S3_BUCKET_ENV, "deploy-bucket");

        AwsLambdaUpdateConfig config = AwsLambdaUpdateConfig.fromEnvironment(env);

        Assert.assertEquals("env-ak", config.getCredentials().getAccessKeyId());
        Assert.assertEquals("env-sk", config.getCredentials().getSecretAccessKey());
        Assert.assertEquals("env-token", config.getCredentials().getSessionToken());
        Assert.assertEquals("us-west-1", config.getRegion());
        Assert.assertEquals("xiaochun-zrlog-com", config.getFunctionName());
        Assert.assertEquals("deploy-bucket", config.getS3Bucket());
        Assert.assertTrue(config.missingRequiredFields().isEmpty());
    }

    @Test
    public void shouldReportMissingConfigFields() {
        AwsLambdaUpdateConfig config = AwsLambdaUpdateConfig.fromEnvironment(new HashMap<>());

        Assert.assertEquals(4, config.missingRequiredFields().size());
        Assert.assertTrue(config.missingRequiredFields().contains("AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY"));
        Assert.assertTrue(config.missingRequiredFields().contains("AWS_REGION"));
        Assert.assertTrue(config.missingRequiredFields().contains("AWS_LAMBDA_FUNCTION_NAME"));
        Assert.assertTrue(config.missingRequiredFields().contains("LAMBDA_S3_REPO_NAME"));
    }

    @Test
    public void shouldUpdateWithInjectedClient() throws Exception {
        File file = tempPackageFile("zrlog-faas", ".zip");
        AwsLambdaUpdateConfig config = new AwsLambdaUpdateConfig(
                new AwsCredentials("ak", "sk", null),
                "us-west-1",
                "xiaochun-zrlog-com",
                "zrlog-update-bucket"
        );
        FakeClient fakeClient = new FakeClient();

        String key = "lambda/xiaochun-zrlog-com/test.zip";
        JsonObject response = new AwsLambdaUpdateService(config, fakeClient)
                .update(file, key);

        Assert.assertEquals("Successful", response.get("LastUpdateStatus").getAsString());
        Assert.assertEquals("put:zrlog-update-bucket/lambda/xiaochun-zrlog-com/test.zip", fakeClient.calls.get(0));
        Assert.assertEquals("update:xiaochun-zrlog-com:zrlog-update-bucket/lambda/xiaochun-zrlog-com/test.zip",
                fakeClient.calls.get(1));
    }

    @Test
    public void shouldSerializeLambdaUpdateCodeRequestWithAwsFieldNames() {
        AwsLambdaUpdateCodeRequest request = new AwsLambdaUpdateCodeRequest("deploy-bucket", "lambda/zrlog.zip");

        JsonObject jsonObject = new Gson().toJsonTree(request).getAsJsonObject();

        Assert.assertEquals("deploy-bucket", jsonObject.get("S3Bucket").getAsString());
        Assert.assertEquals("lambda/zrlog.zip", jsonObject.get("S3Key").getAsString());
    }

    @Test
    public void shouldRejectMissingS3KeyBeforeCallingClient() throws Exception {
        FakeClient fakeClient = new FakeClient();

        try {
            new AwsLambdaUpdateService(validConfig(), fakeClient).update(tempPackageFile("zrlog-faas", ".zip"), "");
            Assert.fail("Expected missing S3 key error");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Missing S3 object key"));
            Assert.assertTrue(fakeClient.calls.isEmpty());
        }
    }

    @Test
    public void shouldRejectMissingPackageFileBeforeCallingClient() throws Exception {
        FakeClient fakeClient = new FakeClient();

        try {
            new AwsLambdaUpdateService(validConfig(), fakeClient).update(new File("/tmp/not-exists-zrlog-faas.zip"), "lambda/test.zip");
            Assert.fail("Expected missing package file error");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Missing package file"));
            Assert.assertTrue(fakeClient.calls.isEmpty());
        }
    }

    @Test
    public void shouldRejectEmptyPackageFileBeforeCallingClient() throws Exception {
        File file = File.createTempFile("zrlog-empty-faas", ".zip");
        file.deleteOnExit();
        FakeClient fakeClient = new FakeClient();

        try {
            new AwsLambdaUpdateService(validConfig(), fakeClient).update(file, "lambda/test.zip");
            Assert.fail("Expected empty package file error");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Empty package file"));
            Assert.assertTrue(fakeClient.calls.isEmpty());
        }
    }

    @Test
    public void shouldRejectMissingConfigBeforeCallingClient() throws Exception {
        FakeClient fakeClient = new FakeClient();

        try {
            new AwsLambdaUpdateService(AwsLambdaUpdateConfig.fromEnvironment(new HashMap<>()), fakeClient)
                    .update(tempPackageFile("zrlog-faas", ".zip"), "lambda/test.zip");
            Assert.fail("Expected missing config error");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Missing Lambda update config"));
            Assert.assertTrue(fakeClient.calls.isEmpty());
        }
    }

    private static File tempPackageFile(String prefix, String suffix) throws Exception {
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write("zip".getBytes());
        }
        return file;
    }

    private static AwsLambdaUpdateConfig validConfig() {
        return new AwsLambdaUpdateConfig(
                new AwsCredentials("ak", "sk", null),
                "us-west-1",
                "xiaochun-zrlog-com",
                "zrlog-update-bucket"
        );
    }

    private static class FakeClient implements AwsLambdaCodeUpdateClient {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void putObject(String bucket, String key, File file) {
            calls.add("put:" + bucket + "/" + key);
        }

        @Override
        public JsonObject updateFunctionCode(String functionName, String bucket, String key) {
            calls.add("update:" + functionName + ":" + bucket + "/" + key);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("LastUpdateStatus", "Successful");
            return jsonObject;
        }
    }
}
