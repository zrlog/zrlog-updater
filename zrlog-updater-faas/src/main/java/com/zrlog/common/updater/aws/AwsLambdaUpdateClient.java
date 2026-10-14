package com.zrlog.common.updater.aws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.updater.spi.UpdateHttp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class AwsLambdaUpdateClient implements AwsLambdaCodeUpdateClient {

    private static final int UPDATE_FUNCTION_CODE_MAX_ATTEMPTS = 30;
    private static final long UPDATE_FUNCTION_CODE_RETRY_INTERVAL_MILLIS = 2000;
    private final UpdateHttp http;
    private final AwsSigV4Signer signer;
    private final AwsCredentials credentials;
    private final String region;
    private final Gson gson = new Gson();

    public AwsLambdaUpdateClient(AwsCredentials credentials, String region, UpdateHttp http) {
        this.credentials = credentials;
        this.region = region;
        this.signer = new AwsSigV4Signer();
        this.http = java.util.Objects.requireNonNull(http);
    }

    @Override
    public void putObject(String bucket, String key, File file) throws IOException, InterruptedException {
        URI uri = s3ObjectUri(bucket, key);
        String payloadSha256 = sha256Hex(file);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Amz-Content-Sha256", payloadSha256);
        Map<String, String> signedHeaders = signer.sign("PUT", uri, headers, payloadSha256, "s3", region, credentials);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .PUT(HttpRequest.BodyPublishers.ofFile(file.toPath()));
        applyHeaders(builder, signedHeaders);
        HttpResponse<String> response = send(builder.build());
        requireSuccess("S3 PutObject", response);
    }

    @Override
    public JsonObject updateFunctionCode(String functionName, String bucket, String key)
            throws IOException, InterruptedException {
        URI uri = lambdaUri(functionName, "code");
        String requestBody = gson.toJson(new AwsLambdaUpdateCodeRequest(bucket, key));
        HttpResponse<String> response = null;
        for (int i = 1; i <= UPDATE_FUNCTION_CODE_MAX_ATTEMPTS; i++) {
            response = sendLambdaJsonRequest("PUT", uri, requestBody);
            if (isSuccess(response)) {
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            if (isUpdateInProgress(response) && i < UPDATE_FUNCTION_CODE_MAX_ATTEMPTS) {
                Thread.sleep(UPDATE_FUNCTION_CODE_RETRY_INTERVAL_MILLIS);
                continue;
            }
            requireSuccess("Lambda PUT", response);
        }
        requireSuccess("Lambda PUT", response);
        throw new IllegalStateException("Lambda PUT failed");
    }

    private HttpResponse<String> sendLambdaJsonRequest(String method, URI uri, String body)
            throws IOException, InterruptedException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String payloadSha256 = AwsSigV4Signer.sha256Hex(bytes);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        Map<String, String> signedHeaders = signer.sign(method, uri, headers, payloadSha256, "lambda", region, credentials);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        applyHeaders(builder, signedHeaders);
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        }
        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try { return http.send(request); }
        catch (IOException | InterruptedException e) { throw e; }
        catch (Exception e) { throw new IOException("AWS update request failed", e); }
    }

    private URI s3ObjectUri(String bucket, String key) {
        String encodedKey = AwsSigV4Signer.uriEncode(key, false);
        if (canUseVirtualHostedStyle(bucket)) {
            return URI.create("https://" + bucket + ".s3." + region + ".amazonaws.com/" + encodedKey);
        }
        return URI.create("https://s3." + region + ".amazonaws.com/" +
                AwsSigV4Signer.uriEncode(bucket, true) + "/" + encodedKey);
    }

    private URI lambdaUri(String functionName, String action) {
        return URI.create("https://lambda." + region + ".amazonaws.com/2015-03-31/functions/" +
                AwsSigV4Signer.uriEncode(functionName, true) + "/" + action);
    }

    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("Host".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            builder.header(entry.getKey(), entry.getValue());
        }
    }

    private static boolean canUseVirtualHostedStyle(String bucket) {
        return bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]");
    }

    private static String sha256Hex(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read;
                while ((read = fis.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return bytesToHex(digest.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void requireSuccess(String action, HttpResponse<String> response) {
        if (response == null) {
            throw new IllegalStateException(action + " failed");
        }
        int statusCode = response.statusCode();
        if (isSuccess(response)) {
            return;
        }
        String body = response.body();
        if (body != null && body.length() > 1000) {
            body = body.substring(0, 1000);
        }
        throw new IllegalStateException(action + " failed, status=" + statusCode + ", body=" + body);
    }

    private static boolean isSuccess(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    private static boolean isUpdateInProgress(HttpResponse<String> response) {
        if (response.statusCode() != 409) {
            return false;
        }
        String body = response.body();
        return body != null && body.contains("An update is in progress");
    }
}
