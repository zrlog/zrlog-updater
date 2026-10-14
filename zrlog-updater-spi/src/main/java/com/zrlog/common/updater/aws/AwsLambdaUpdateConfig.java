package com.zrlog.common.updater.aws;

import com.zrlog.updater.spi.UpdateStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AwsLambdaUpdateConfig {

    public static final String ACCESS_KEY_ID_ENV = "AWS_ACCESS_KEY_ID";
    public static final String SECRET_ACCESS_KEY_ENV = "AWS_SECRET_ACCESS_KEY";
    public static final String SESSION_TOKEN_ENV = "AWS_SESSION_TOKEN";
    public static final String REGION_ENV = "AWS_REGION";
    public static final String DEFAULT_REGION_ENV = "AWS_DEFAULT_REGION";
    public static final String FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";
    public static final String S3_BUCKET_ENV = "LAMBDA_S3_REPO_NAME";

    private final AwsCredentials credentials;
    private final String region;
    private final String functionName;
    private final String s3Bucket;

    public AwsLambdaUpdateConfig(AwsCredentials credentials, String region, String functionName, String s3Bucket) {
        this.credentials = credentials;
        this.region = region;
        this.functionName = functionName;
        this.s3Bucket = s3Bucket;
    }

    public static AwsLambdaUpdateConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static AwsLambdaUpdateConfig fromEnvironment(Map<String, String> env) {
        return new AwsLambdaUpdateConfig(
                new AwsCredentials(
                        env.get(ACCESS_KEY_ID_ENV),
                        env.get(SECRET_ACCESS_KEY_ENV),
                        env.get(SESSION_TOKEN_ENV)
                ),
                firstNotEmpty(env.get(REGION_ENV), env.get(DEFAULT_REGION_ENV)),
                env.get(FUNCTION_NAME_ENV),
                env.get(S3_BUCKET_ENV)
        );
    }

    public List<String> missingRequiredFields() {
        List<String> missing = new ArrayList<>();
        if (!credentials.isValid()) {
            missing.add("AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY");
        }
        if (UpdateStrings.isEmpty(region)) {
            missing.add("AWS_REGION");
        }
        if (UpdateStrings.isEmpty(functionName)) {
            missing.add("AWS_LAMBDA_FUNCTION_NAME");
        }
        if (UpdateStrings.isEmpty(s3Bucket)) {
            missing.add("LAMBDA_S3_REPO_NAME");
        }
        return missing;
    }

    public void validate() {
        List<String> missing = missingRequiredFields();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing Lambda update config: " + String.join(", ", missing));
        }
    }

    public AwsCredentials getCredentials() {
        return credentials;
    }

    public String getRegion() {
        return region;
    }

    public String getFunctionName() {
        return functionName;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    private static String firstNotEmpty(String... values) {
        for (String value : values) {
            if (UpdateStrings.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
