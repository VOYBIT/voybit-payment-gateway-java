package com.voybit.paymentgateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Webhook {
    private static final long TOLERANCE_SECONDS = 300;
    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private Webhook() {
    }

    public static void verify(String secret, String id, String timestamp, String signature, byte[] rawBody) {
        verify(secret, id, timestamp, signature, rawBody, null);
    }

    public static void verify(String secret, String id, String timestamp, String signature, byte[] rawBody, Instant now) {
        String hex = signature != null && signature.startsWith("v1=") ? signature.substring(3) : "";
        if (secret == null || secret.isEmpty() || id == null || id.isEmpty() || !digits(timestamp) || rawBody == null || !HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("webhook signature is invalid");
        }
        long seconds = Long.parseLong(timestamp);
        long current = (now == null ? Instant.now() : now).getEpochSecond();
        if (Math.abs(current - seconds) > TOLERANCE_SECONDS) {
            throw new IllegalArgumentException("webhook timestamp is outside the 5 minute window");
        }
        byte[] supplied = HexFormat.of().parseHex(hex);
        byte[] prefix = (id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(rawBody, 0, signed, prefix.length, rawBody.length);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(signed), supplied)) {
                throw new IllegalArgumentException("webhook signature does not match");
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("webhook signature could not be checked", error);
        }
    }

    public static Map<String, Object> parse(byte[] rawBody) {
        if (rawBody == null) {
            throw new IllegalArgumentException("webhook body must be an object");
        }
        return Json.object(new String(rawBody, StandardCharsets.UTF_8));
    }

    private static boolean digits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
