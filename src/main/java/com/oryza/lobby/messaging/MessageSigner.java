package com.oryza.lobby.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MessageSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Map<String, String> keys;
    private final String activeKeyId;
    private final long maxClockSkewSeconds;

    public MessageSigner(Map<String, String> keys, String activeKeyId, long maxClockSkewSeconds) {
        this.keys = Map.copyOf(keys);
        this.activeKeyId = activeKeyId;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
    }

    public String sign(String messageId, long timestamp, String type, String body) {
        String secret = keys.get(activeKeyId);
        if (secret == null) {
            throw new IllegalStateException("Active key secret not found");
        }
        return computeSignature(messageId, timestamp, type, body, secret);
    }

    public boolean verify(String messageId, long timestamp, String type, String body, String keyId, String signature, long nowEpochSeconds) {
        if (Math.abs(nowEpochSeconds - timestamp) > maxClockSkewSeconds) {
            return false;
        }
        String secret = keys.get(keyId);
        if (secret == null) {
            return false;
        }
        String expected = computeSignature(messageId, timestamp, type, body, secret);
        return MessageDigest.isEqual(expected.toLowerCase().getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public static String computeBodyHash(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String computeSignature(String messageId, long timestamp, String type, String body, String secret) {
        try {
            String bodyHash = computeBodyHash(body);
            String canonical = "v1\n" + messageId + "\n" + timestamp + "\n" + type + "\n" + bodyHash;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }
}
