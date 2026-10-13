package com.oryza.lobby.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TokenVerifier {
    public static final String MAGIC = "OLB1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Map<String, String> keys;
    private final long maxAgeSeconds;
    private final Map<String, Long> nonceCache = new ConcurrentHashMap<>();

    public record VerifiedToken(String hostname, String routeId, long issuedAt, String nonce, String keyId) {}

    public TokenVerifier(Map<String, String> keys, long maxAgeSeconds) {
        this.keys = Map.copyOf(keys);
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public VerifiedToken verify(String serverAddress) {
        return verify(serverAddress, Instant.now().getEpochSecond());
    }

    public VerifiedToken verify(String serverAddress, long nowEpochSeconds) {
        if (serverAddress == null || serverAddress.isEmpty()) {
            return null;
        }

        String[] parts = serverAddress.split("\u0000", -1);
        if (parts.length != 7) {
            return null;
        }

        String hostname = parts[0];
        String magic = parts[1];
        String routeId = parts[2];
        String issuedAtStr = parts[3];
        String nonce = parts[4];
        String keyId = parts[5];
        String signature = parts[6];

        if (!MAGIC.equals(magic)) {
            return null;
        }

        long issuedAt;
        try {
            issuedAt = Long.parseLong(issuedAtStr);
        } catch (NumberFormatException e) {
            return null;
        }

        if (issuedAt > nowEpochSeconds + 5) {
            return null;
        }
        if (nowEpochSeconds - issuedAt > maxAgeSeconds) {
            return null;
        }

        String secret = keys.get(keyId);
        if (secret == null) {
            return null;
        }

        String expectedSig = computeSignature(hostname, routeId, issuedAt, nonce, secret);
        if (expectedSig == null || !MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }

        pruneNonceCache(nowEpochSeconds);
        String cacheKey = keyId + ":" + nonce;
        if (nonceCache.putIfAbsent(cacheKey, issuedAt) != null) {
            return null;
        }

        return new VerifiedToken(hostname, routeId, issuedAt, nonce, keyId);
    }

    public static String computeSignature(String hostname, String routeId, long issuedAt, String nonce, String secret) {
        try {
            String payload = "olb1\n" + hostname + "\n" + routeId + "\n" + issuedAt + "\n" + nonce;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            return null;
        }
    }

    private void pruneNonceCache(long nowEpochSeconds) {
        if (nonceCache.size() > 50000) {
            nonceCache.entrySet().removeIf(entry -> nowEpochSeconds - entry.getValue() > maxAgeSeconds);
        }
    }
}
