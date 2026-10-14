package com.zrlog.common.updater.aws;

import com.google.gson.annotations.SerializedName;

public class AwsLambdaUpdateCodeRequest {

    @SerializedName("S3Bucket")
    private String s3Bucket;
    @SerializedName("S3Key")
    private String s3Key;

    public AwsLambdaUpdateCodeRequest() {
    }

    public AwsLambdaUpdateCodeRequest(String s3Bucket, String s3Key) {
        this.s3Bucket = s3Bucket;
        this.s3Key = s3Key;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }
}
