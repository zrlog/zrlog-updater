package com.zrlog.common.updater.aws;

import org.junit.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AwsSigV4SignerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2015-08-30T12:36:00Z"), ZoneOffset.UTC);

    @Test
    public void shouldHashAndEncodeAwsCanonicalValues() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                AwsSigV4Signer.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals("a%20b/%E4%B8%AD%E6%96%87", AwsSigV4Signer.uriEncode("a b/中文", false));
        assertEquals("a%20b%2Fc", AwsSigV4Signer.uriEncode("a b/c", true));
    }

    @Test
    public void shouldRejectMissingCredentials() throws Exception {
        AwsSigV4Signer signer = new AwsSigV4Signer(FIXED_CLOCK);

        assertThrows(IllegalArgumentException.class, () -> signer.sign(
                "GET",
                new URI("https://lambda.us-west-1.amazonaws.com/"),
                Map.of(),
                AwsSigV4Signer.sha256Hex(new byte[0]),
                "lambda",
                "us-west-1",
                new AwsCredentials("", "secret", null)
        ));
    }

    @Test
    public void shouldCreateDeterministicSignedHeaders() throws Exception {
        AwsSigV4Signer signer = new AwsSigV4Signer(FIXED_CLOCK);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", " application/json   charset=utf-8 ");

        Map<String, String> signed = signer.sign(
                "POST",
                new URI("https://lambda.us-west-1.amazonaws.com:9443/2015-03-31/functions/demo/code?b=two&a=one"),
                headers,
                AwsSigV4Signer.sha256Hex("{}".getBytes(StandardCharsets.UTF_8)),
                "lambda",
                "us-west-1",
                new AwsCredentials("AKID", "SECRET", "TOKEN")
        );

        assertEquals("lambda.us-west-1.amazonaws.com:9443", signed.get("Host"));
        assertEquals("20150830T123600Z", signed.get("X-Amz-Date"));
        assertEquals("TOKEN", signed.get("X-Amz-Security-Token"));
        assertTrue(signed.get("Authorization").contains("Credential=AKID/20150830/us-west-1/lambda/aws4_request"));
        assertTrue(signed.get("Authorization").contains("SignedHeaders=content-type;host;x-amz-date;x-amz-security-token"));
        assertTrue(signed.get("Authorization").matches(".*Signature=[0-9a-f]{64}$"));
    }

    @Test
    public void shouldOmitDefaultPortAndSessionTokenWhenAbsent() throws Exception {
        AwsSigV4Signer signer = new AwsSigV4Signer(FIXED_CLOCK);

        Map<String, String> signed = signer.sign(
                "GET",
                new URI("https://lambda.us-west-1.amazonaws.com?z=last&a=first"),
                Map.of(),
                AwsSigV4Signer.sha256Hex(new byte[0]),
                "lambda",
                "us-west-1",
                new AwsCredentials("AKID", "SECRET", null)
        );

        assertEquals("lambda.us-west-1.amazonaws.com", signed.get("Host"));
        assertFalse(signed.containsKey("X-Amz-Security-Token"));
        assertTrue(signed.get("Authorization").contains("SignedHeaders=host;x-amz-date"));
    }
}
