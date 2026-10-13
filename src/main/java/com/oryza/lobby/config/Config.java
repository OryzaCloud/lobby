package com.oryza.lobby.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Config(
    String appEnv,
    String lobbyBind,
    String lobbyInstanceId,
    String lobbyAdvertiseAddress,
    int lobbyAdvertisePort,
    String amqpUrl,
    Map<String, String> messagingHmacKeys,
    String messagingHmacActiveKeyId,
    Duration messagingMaxClockSkew,
    Map<String, String> lobbyTokenKeys,
    Duration lobbyTokenMaxAge,
    boolean lobbyOnlineMode,
    int lobbyMaxSessions,
    Duration lobbyMaxWait,
    Duration lobbyRpcTimeout,
    Duration lobbyTargetCacheTtl,
    int lobbyTransferBatch,
    long lobbyTransferSpreadMs,
    Duration lobbyTransferGrace,
    Duration lobbyMessagingGrace,
    String lobbyDefaultLocale,
    String lobbyMessagesDir,
    int lobbyViewDistance,
    String healthAddr,
    String healthToken,
    Duration shutdownTimeout,
    String logLevel,
    String logFormat
) {
    private static final Map<String, String> fileEnv = new HashMap<>();

    public static Config load() {
        loadFile(".env");
        String env = getEnvOrDefault("APP_ENV", "production");
        if ("development".equalsIgnoreCase(env)) {
            loadFile(".env.development");
            env = getEnvOrDefault("APP_ENV", "development");
        }
        boolean isDev = "development".equalsIgnoreCase(env);

        String bind = getEnvOrDefault("LOBBY_BIND", isDev ? "127.0.0.1:25580" : "0.0.0.0:25580");
        String instanceId = getEnvOrDefault("LOBBY_INSTANCE_ID", "lobby-" + UUID.randomUUID().toString().substring(0, 8));

        String[] bindParts = bind.split(":", 2);
        String defaultAdvHost = bindParts.length > 0 && !bindParts[0].isEmpty() && !"0.0.0.0".equals(bindParts[0]) ? bindParts[0] : "127.0.0.1";
        int defaultAdvPort = bindParts.length > 1 ? Integer.parseInt(bindParts[1]) : 25580;

        String advAddress = getEnvOrDefault("LOBBY_ADVERTISE_ADDRESS", defaultAdvHost);
        int advPort = Integer.parseInt(getEnvOrDefault("LOBBY_ADVERTISE_PORT", String.valueOf(defaultAdvPort)));

        String amqpUrl = getEnvOrSecret("AMQP_URL", isDev ? "amqp://guest:guest@127.0.0.1:5672/" : "");

        String rawHmacKeys = getEnvOrSecret("MESSAGING_HMAC_KEYS", isDev ? "dev-key-1:01234567890123456789012345678901" : "");
        String activeHmacKeyId = getEnvOrDefault("MESSAGING_HMAC_ACTIVE_KEY_ID", isDev ? "dev-key-1" : "");
        Map<String, String> messagingHmacKeys = parseKeys(rawHmacKeys);

        String rawTokenKeys = getEnvOrSecret("LOBBY_TOKEN_KEYS", isDev ? "dev-token-1:abcdef0123456789abcdef0123456789" : "");
        Map<String, String> lobbyTokenKeys = parseKeys(rawTokenKeys);

        long clockSkewSeconds = Long.parseLong(getEnvOrDefault("MESSAGING_MAX_CLOCK_SKEW", "60"));
        long tokenMaxAgeSeconds = Long.parseLong(getEnvOrDefault("LOBBY_TOKEN_MAX_AGE", "20"));

        boolean onlineMode = Boolean.parseBoolean(getEnvOrDefault("LOBBY_ONLINE_MODE", "false"));
        int maxSessions = Integer.parseInt(getEnvOrDefault("LOBBY_MAX_SESSIONS", "500"));
        long maxWaitSeconds = Long.parseLong(getEnvOrDefault("LOBBY_MAX_WAIT", "600"));

        long rpcTimeoutMs = Long.parseLong(getEnvOrDefault("LOBBY_RPC_TIMEOUT_MS", "2000"));
        long targetCacheTtlSeconds = Long.parseLong(getEnvOrDefault("LOBBY_TARGET_CACHE_TTL", "10"));

        int transferBatch = Integer.parseInt(getEnvOrDefault("LOBBY_TRANSFER_BATCH", "20"));
        long transferSpreadMs = Long.parseLong(getEnvOrDefault("LOBBY_TRANSFER_SPREAD_MS", "50"));
        long transferGraceSeconds = Long.parseLong(getEnvOrDefault("LOBBY_TRANSFER_GRACE", "10"));
        long messagingGraceSeconds = Long.parseLong(getEnvOrDefault("LOBBY_MESSAGING_GRACE", "60"));

        String defaultLocale = getEnvOrDefault("LOBBY_DEFAULT_LOCALE", "pt_BR");
        String messagesDir = getEnvOrDefault("LOBBY_MESSAGES_DIR", "");
        int viewDistance = Integer.parseInt(getEnvOrDefault("LOBBY_VIEW_DISTANCE", "4"));

        String healthAddr = getEnvOrDefault("HEALTH_ADDR", "127.0.0.1:8082");
        String healthToken = getEnvOrSecret("HEALTH_TOKEN", "");
        long shutdownTimeoutSeconds = Long.parseLong(getEnvOrDefault("SHUTDOWN_TIMEOUT", "20"));

        String logLevel = getEnvOrDefault("LOG_LEVEL", isDev ? "debug" : "info");
        String logFormat = getEnvOrDefault("LOG_FORMAT", isDev ? "text" : "json");

        Config config = new Config(
            env,
            bind,
            instanceId,
            advAddress,
            advPort,
            amqpUrl,
            messagingHmacKeys,
            activeHmacKeyId,
            Duration.ofSeconds(clockSkewSeconds),
            lobbyTokenKeys,
            Duration.ofSeconds(tokenMaxAgeSeconds),
            onlineMode,
            maxSessions,
            Duration.ofSeconds(maxWaitSeconds),
            Duration.ofMillis(rpcTimeoutMs),
            Duration.ofSeconds(targetCacheTtlSeconds),
            transferBatch,
            transferSpreadMs,
            Duration.ofSeconds(transferGraceSeconds),
            Duration.ofSeconds(messagingGraceSeconds),
            defaultLocale,
            messagesDir,
            viewDistance,
            healthAddr,
            healthToken,
            Duration.ofSeconds(shutdownTimeoutSeconds),
            logLevel,
            logFormat
        );

        config.validate();
        return config;
    }

    private static void loadFile(String filename) {
        Path path = Path.of(filename);
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String k = parts[0].trim();
                    String v = parts[1].trim();
                    if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    fileEnv.put(k, v);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void validate() {
        if ("production".equalsIgnoreCase(appEnv)) {
            if (amqpUrl.isEmpty()) {
                throw new IllegalStateException("AMQP_URL is required in production");
            }
            if (messagingHmacKeys.isEmpty()) {
                throw new IllegalStateException("MESSAGING_HMAC_KEYS is required in production");
            }
            if (messagingHmacActiveKeyId.isEmpty() || !messagingHmacKeys.containsKey(messagingHmacActiveKeyId)) {
                throw new IllegalStateException("MESSAGING_HMAC_ACTIVE_KEY_ID must exist in keys");
            }
            if (lobbyTokenKeys.isEmpty()) {
                throw new IllegalStateException("LOBBY_TOKEN_KEYS is required in production");
            }
            if (messagingHmacKeys.containsKey("dev-key-1")) {
                throw new IllegalStateException("Development HMAC keys are strictly forbidden in production");
            }
            if (lobbyTokenKeys.containsKey("dev-token-1")) {
                throw new IllegalStateException("Development token keys are strictly forbidden in production");
            }
        }

        for (Map.Entry<String, String> entry : messagingHmacKeys.entrySet()) {
            if (entry.getValue().getBytes().length < 32) {
                throw new IllegalStateException("Secret for messaging key " + entry.getKey() + " must be at least 32 bytes");
            }
        }
        for (Map.Entry<String, String> entry : lobbyTokenKeys.entrySet()) {
            if (entry.getValue().getBytes().length < 32) {
                throw new IllegalStateException("Secret for lobby token key " + entry.getKey() + " must be at least 32 bytes");
            }
        }
    }

    private static Map<String, String> parseKeys(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split(":", 2);
            if (kv.length == 2 && !kv[0].isEmpty() && !kv[1].isEmpty()) {
                result.put(kv[0], kv[1]);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String getEnvOrDefault(String key, String def) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        val = fileEnv.get(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        return def;
    }

    private static String getEnvOrSecret(String key, String def) {
        String fileKey = key + "_FILE";
        String filePath = getEnvOrDefault(fileKey, null);
        if (filePath != null && !filePath.trim().isEmpty()) {
            try {
                return Files.readString(Path.of(filePath.trim())).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read secret file " + filePath, e);
            }
        }
        return getEnvOrDefault(key, def);
    }
}
