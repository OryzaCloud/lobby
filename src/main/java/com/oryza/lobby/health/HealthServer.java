package com.oryza.lobby.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class HealthServer {
    private final HttpServer server;
    private final String healthToken;
    private final Supplier<Boolean> readinessSupplier;

    public HealthServer(String bindAddr, String healthToken, Supplier<Boolean> readinessSupplier) throws IOException {
        this.healthToken = healthToken;
        this.readinessSupplier = readinessSupplier;

        String[] parts = bindAddr.split(":", 2);
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor());

        this.server.createContext("/healthz", new SimpleHandler(200, "{\"status\":\"healthy\"}"));
        this.server.createContext("/readyz", exchange -> {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (readinessSupplier.get()) {
                sendResponse(exchange, 200, "{\"status\":\"ready\"}");
            } else {
                sendResponse(exchange, 503, "{\"status\":\"not_ready\"}");
            }
        });
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (healthToken == null || healthToken.isEmpty()) {
            return true;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return auth != null && auth.equals("Bearer " + healthToken);
    }

    private static void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private class SimpleHandler implements HttpHandler {
        private final int code;
        private final String body;

        SimpleHandler(int code, String body) {
            this.code = code;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            sendResponse(exchange, code, body);
        }
    }
}
