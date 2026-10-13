package com.oryza.lobby.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minestom.server.MinecraftServer;

public final class AmqpClient {
    public static final String EXCHANGE_LOBBY = "oryza.lobby";

    private final String amqpUrl;
    private final String instanceId;
    private final String advertiseAddress;
    private final int advertisePort;
    private final int capacity;
    private final MessageSigner signer;
    private final Supplier<Integer> sessionCountSupplier;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, CompletableFuture<TargetResolveResponse>> pendingRpc = new ConcurrentHashMap<>();
    private final Map<String, TargetResolveResponse> targetCache = new ConcurrentHashMap<>();

    private Connection connection;
    private Channel channel;
    private volatile boolean running = true;

    private Consumer<RouteStateEvent> routeStateConsumer;
    private Consumer<RouteReadyEvent> routeReadyConsumer;
    private Consumer<RouteClosedEvent> routeClosedConsumer;

    public record TargetResolveResponse(String routeId, String hostname, String displayName, String state, boolean autoStart, String transferHost, int transferPort, long maxWaitSeconds) {}
    public record RouteStateEvent(String routeId, String hostname, String state, boolean autoStart) {}
    public record RouteReadyEvent(String routeId, String hostname, String transferHost, int transferPort) {}
    public record RouteClosedEvent(String routeId, String reason) {}

    public AmqpClient(
        String amqpUrl,
        String instanceId,
        String advertiseAddress,
        int advertisePort,
        int capacity,
        MessageSigner signer,
        Supplier<Integer> sessionCountSupplier
    ) {
        this.amqpUrl = amqpUrl;
        this.instanceId = instanceId;
        this.advertiseAddress = advertiseAddress;
        this.advertisePort = advertisePort;
        this.capacity = capacity;
        this.signer = signer;
        this.sessionCountSupplier = sessionCountSupplier;
    }

    public void setRouteStateConsumer(Consumer<RouteStateEvent> consumer) {
        this.routeStateConsumer = consumer;
    }

    public void setRouteReadyConsumer(Consumer<RouteReadyEvent> consumer) {
        this.routeReadyConsumer = consumer;
    }

    public void setRouteClosedConsumer(Consumer<RouteClosedEvent> consumer) {
        this.routeClosedConsumer = consumer;
    }

    public void start() {
        connect();
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 1, 5, TimeUnit.SECONDS);
    }

    private synchronized void connect() {
        if (!running || amqpUrl.isEmpty()) {
            return;
        }
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(amqpUrl);
            this.connection = factory.newConnection("lobby-" + instanceId);
            this.channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_LOBBY, "topic", true);

            String queueName = "lobby.instance." + instanceId;
            channel.queueDeclare(queueName, false, true, true, Map.of("x-message-ttl", 30000));
            channel.queueBind(queueName, EXCHANGE_LOBBY, "lobby.route.state");
            channel.queueBind(queueName, EXCHANGE_LOBBY, "lobby.route.ready");
            channel.queueBind(queueName, EXCHANGE_LOBBY, "lobby.route.closed");

            channel.basicConsume(queueName, true, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                    processMessage(envelope.getRoutingKey(), properties, body);
                }
            });
        } catch (Exception e) {
            scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    private void processMessage(String routingKey, AMQP.BasicProperties props, byte[] body) {
        try {
            if (body.length > 64 * 1024) {
                return;
            }
            Map<String, Object> headers = props.getHeaders();
            if (headers == null) {
                return;
            }
            Object keyIdObj = headers.get("x-key-id");
            Object sigObj = headers.get("x-signature");
            if (keyIdObj == null || sigObj == null) {
                return;
            }
            String keyId = keyIdObj.toString();
            String signature = sigObj.toString();
            long timestamp = props.getTimestamp() != null ? props.getTimestamp().getTime() / 1000 : 0;
            String messageId = props.getMessageId();
            String type = props.getType();
            String bodyStr = new String(body, StandardCharsets.UTF_8);

            if (!signer.verify(messageId, timestamp, type, bodyStr, keyId, signature, Instant.now().getEpochSecond())) {
                return;
            }

            JsonNode root = mapper.readTree(bodyStr);
            String correlationId = props.getCorrelationId();

            if (correlationId != null && pendingRpc.containsKey(correlationId)) {
                CompletableFuture<TargetResolveResponse> future = pendingRpc.remove(correlationId);
                if (root.path("ok").asBoolean(false)) {
                    JsonNode data = root.path("data");
                    TargetResolveResponse resp = new TargetResolveResponse(
                        data.path("route_id").asText(),
                        data.path("hostname").asText(),
                        data.path("display_name").asText(),
                        data.path("state").asText(),
                        data.path("auto_start").asBoolean(true),
                        data.path("transfer_host").asText(),
                        data.path("transfer_port").asInt(25565),
                        data.path("max_wait_seconds").asLong(600)
                    );
                    targetCache.put(resp.routeId(), resp);
                    future.complete(resp);
                } else {
                    future.completeExceptionally(new RuntimeException("RPC error: " + root.path("error").path("code").asText()));
                }
                return;
            }

            JsonNode data = root.path("data");
            if ("lobby.route.state".equals(routingKey) && routeStateConsumer != null) {
                routeStateConsumer.accept(new RouteStateEvent(
                    data.path("route_id").asText(),
                    data.path("hostname").asText(),
                    data.path("state").asText(),
                    data.path("auto_start").asBoolean(true)
                ));
            } else if ("lobby.route.ready".equals(routingKey) && routeReadyConsumer != null) {
                routeReadyConsumer.accept(new RouteReadyEvent(
                    data.path("route_id").asText(),
                    data.path("hostname").asText(),
                    data.path("transfer_host").asText(),
                    data.path("transfer_port").asInt(25565)
                ));
            } else if ("lobby.route.closed".equals(routingKey) && routeClosedConsumer != null) {
                routeClosedConsumer.accept(new RouteClosedEvent(
                    data.path("route_id").asText(),
                    data.path("reason").asText()
                ));
            }
        } catch (Exception ignored) {
        }
    }

    private void sendHeartbeat() {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        try {
            int currentSessions = sessionCountSupplier.get();
            Map<String, Object> data = Map.of(
                "instance_id", instanceId,
                "address", advertiseAddress,
                "port", advertisePort,
                "protocols", List.of(MinecraftServer.PROTOCOL_VERSION),
                "sessions", currentSessions,
                "capacity", capacity,
                "version", "1.0.0",
                "started_at", Instant.now().toString()
            );
            publishEvent("lobby.instance.heartbeat", "lobby.instance.heartbeat", mapper.writeValueAsString(Map.of("data", data)), null, null);
        } catch (Exception ignored) {
        }
    }

    public CompletableFuture<TargetResolveResponse> resolveTarget(String routeId, Duration timeout) {
        TargetResolveResponse cached = targetCache.get(routeId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<TargetResolveResponse> future = new CompletableFuture<>();
        String correlationId = UUID.randomUUID().toString();
        pendingRpc.put(correlationId, future);

        scheduler.schedule(() -> {
            CompletableFuture<TargetResolveResponse> removed = pendingRpc.remove(correlationId);
            if (removed != null) {
                removed.completeExceptionally(new RuntimeException("Target resolve RPC timed out"));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            Map<String, Object> req = Map.of("data", Map.of("route_id", routeId, "instance_id", instanceId));
            String body = mapper.writeValueAsString(req);
            String replyQueue = "lobby.instance." + instanceId;
            publishEvent("lobby.target.resolve", "lobby.target.resolve", body, correlationId, replyQueue);
        } catch (Exception e) {
            pendingRpc.remove(correlationId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void publishPlayerJoined(String routeId, UUID sessionId, String player) {
        try {
            Map<String, Object> data = Map.of(
                "instance_id", instanceId,
                "route_id", routeId,
                "session_id", sessionId.toString(),
                "player", player,
                "joined_at", Instant.now().toString()
            );
            publishEvent("lobby.player.joined", "lobby.player.joined", mapper.writeValueAsString(Map.of("data", data)), null, null);
        } catch (Exception ignored) {
        }
    }

    public void publishPlayerLeft(String routeId, UUID sessionId, String player, String reason, long waitedMs) {
        try {
            Map<String, Object> data = Map.of(
                "instance_id", instanceId,
                "route_id", routeId,
                "session_id", sessionId.toString(),
                "player", player,
                "reason", reason,
                "waited_ms", waitedMs
            );
            publishEvent("lobby.player.left", "lobby.player.left", mapper.writeValueAsString(Map.of("data", data)), null, null);
        } catch (Exception ignored) {
        }
    }

    private void publishEvent(String routingKey, String type, String body, String correlationId, String replyTo) throws IOException {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        String messageId = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();
        String signature = signer.sign(messageId, now, type, body);

        Map<String, Object> headers = new HashMap<>();
        headers.put("x-key-id", signer.getActiveKeyId());
        headers.put("x-signature", signature);
        headers.put("x-signature-version", "1");

        AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder()
            .messageId(messageId)
            .timestamp(java.util.Date.from(Instant.ofEpochSecond(now)))
            .type(type)
            .appId("lobby")
            .headers(headers);

        if (correlationId != null) {
            builder.correlationId(correlationId);
        }
        if (replyTo != null) {
            builder.replyTo(replyTo);
        }

        channel.basicPublish(EXCHANGE_LOBBY, routingKey, builder.build(), body.getBytes(StandardCharsets.UTF_8));
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }
}
