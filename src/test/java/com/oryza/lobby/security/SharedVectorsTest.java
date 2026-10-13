package com.oryza.lobby.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryza.lobby.messaging.MessageSigner;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedVectorsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testMessagingVectors() throws Exception {
        JsonNode root = mapper.readTree(new File("testdata/shared_vectors.json"));
        JsonNode messagingVectors = root.get("messaging_vectors");

        for (JsonNode vector : messagingVectors) {
            String name = vector.get("name").asText();
            String messageId = vector.get("message_id").asText();
            long timestamp = vector.get("timestamp").asLong();
            String type = vector.get("type").asText();
            String body = vector.get("body").asText();
            String keyId = vector.get("key_id").asText();
            String secret = vector.get("secret").asText();
            String expectedSig = vector.get("expected_sig").asText();

            MessageSigner signer = new MessageSigner(Map.of(keyId, secret), keyId, 300);
            String actualSig = signer.sign(messageId, timestamp, type, body);
            assertEquals(expectedSig, actualSig, "Signature mismatch for " + name);

            assertTrue(signer.verify(messageId, timestamp, type, body, keyId, actualSig, timestamp), "Verify failed for " + name);
        }
    }

    @Test
    void testTokenVectors() throws Exception {
        JsonNode root = mapper.readTree(new File("testdata/shared_vectors.json"));
        JsonNode tokenVectors = root.get("token_vectors");

        for (JsonNode vector : tokenVectors) {
            String name = vector.get("name").asText();
            String hostname = vector.get("hostname").asText();
            String routeId = vector.get("route_id").asText();
            long issuedAt = vector.get("issued_at").asLong();
            String nonce = vector.get("nonce").asText();
            String keyId = vector.get("key_id").asText();
            String secret = vector.get("secret").asText();
            String expectedSig = vector.get("expected_sig").asText();
            String expectedAddress = vector.get("expected_address").asText();

            String actualSig = TokenVerifier.computeSignature(hostname, routeId, issuedAt, nonce, secret);
            assertEquals(expectedSig, actualSig, "Token signature mismatch for " + name);

            TokenVerifier verifier = new TokenVerifier(Map.of(keyId, "abcdef0123456789abcdef0123456789"), 20);

            if ("valid_token".equals(name)) {
                TokenVerifier.VerifiedToken token = verifier.verify(expectedAddress, issuedAt);
                assertNotNull(token, "Expected valid token for " + name);
                assertEquals(hostname, token.hostname());
                assertEquals(routeId, token.routeId());
                assertEquals(issuedAt, token.issuedAt());
                assertEquals(nonce, token.nonce());
                assertEquals(keyId, token.keyId());

                assertNull(verifier.verify(expectedAddress, issuedAt), "Replay should be rejected for " + name);
            } else if ("wrong_signature".equals(name)) {
                TokenVerifier wrongVerifier = new TokenVerifier(Map.of(keyId, "differentsecret0123456789abcdef02"), 20);
                assertNull(wrongVerifier.verify(expectedAddress, issuedAt), "Expected rejection for " + name);
            } else if ("expired_token".equals(name)) {
                assertNull(verifier.verify(expectedAddress, issuedAt + 100), "Expected rejection for " + name);
            } else if ("tampered_route_id".equals(name)) {
                String tamperedAddress = "play.localhost\u0000OLB1\u000011111111-2222-3333-4444-555555555555\u00001704110400\u0000a1b2c3d4e5f60718\u0000tk1\u0000" + expectedSig;
                assertNull(verifier.verify(tamperedAddress, issuedAt), "Expected rejection for " + name);
            }
        }
    }
}
