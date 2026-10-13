package com.oryza.lobby.session;

import com.oryza.lobby.i18n.MessageBundle;
import com.oryza.lobby.messaging.AmqpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.SharedInstance;

public final class SessionManager {
    private final Map<UUID, LobbySession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<UUID>> routeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final MessageBundle bundle;
    private final AmqpClient amqpClient;
    private final int transferBatch;
    private final long transferSpreadMs;

    public SessionManager(MessageBundle bundle, AmqpClient amqpClient, int transferBatch, long transferSpreadMs) {
        this.bundle = bundle;
        this.amqpClient = amqpClient;
        this.transferBatch = transferBatch;
        this.transferSpreadMs = transferSpreadMs;

        this.scheduler.scheduleAtFixedRate(this::tickAll, 1, 1, TimeUnit.SECONDS);
    }

    public LobbySession createSession(Player player, SharedInstance instance, String routeId, String hostname) {
        LobbySession session = new LobbySession(player, instance, routeId, hostname, bundle);
        sessions.put(player.getUuid(), session);
        routeSessions.computeIfAbsent(routeId, k -> Collections.synchronizedList(new java.util.ArrayList<>())).add(player.getUuid());

        if (amqpClient != null) {
            amqpClient.publishPlayerJoined(routeId, session.getSessionId(), player.getUsername());
        }
        return session;
    }

    public LobbySession getSession(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public Collection<LobbySession> getAllSessions() {
        return sessions.values();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public void removeSession(UUID playerUuid, String reason) {
        LobbySession session = sessions.remove(playerUuid);
        if (session != null) {
            session.close(reason);
            List<UUID> list = routeSessions.get(session.getRouteId());
            if (list != null) {
                list.remove(playerUuid);
                if (list.isEmpty()) {
                    routeSessions.remove(session.getRouteId());
                }
            }
            if (amqpClient != null) {
                long waitedMs = Duration.between(session.getJoinedAt(), Instant.now()).toMillis();
                amqpClient.publishPlayerLeft(session.getRouteId(), session.getSessionId(), session.getPlayer().getUsername(), reason, waitedMs);
            }
        }
    }

    public void onRouteStateChanged(String routeId, String state) {
        List<UUID> list = routeSessions.get(routeId);
        if (list == null) {
            return;
        }
        synchronized (list) {
            for (UUID uuid : list) {
                LobbySession session = sessions.get(uuid);
                if (session != null) {
                    session.setServerState(state);
                }
            }
        }
    }

    public void onRouteReady(String routeId, String transferHost, int transferPort) {
        List<UUID> list = routeSessions.get(routeId);
        if (list == null) {
            return;
        }
        List<UUID> snapshot;
        synchronized (list) {
            snapshot = new java.util.ArrayList<>(list);
        }

        int index = 0;
        for (UUID uuid : snapshot) {
            LobbySession session = sessions.get(uuid);
            if (session != null && session.getState() == LobbySession.State.WAITING) {
                long delay = (index / transferBatch) * transferSpreadMs;
                scheduler.schedule(() -> session.transfer(transferHost, transferPort), delay + 500, TimeUnit.MILLISECONDS);
                index++;
            }
        }
    }

    public void onRouteClosed(String routeId, String reason) {
        List<UUID> list = routeSessions.get(routeId);
        if (list == null) {
            return;
        }
        List<UUID> snapshot;
        synchronized (list) {
            snapshot = new java.util.ArrayList<>(list);
        }
        for (UUID uuid : snapshot) {
            LobbySession session = sessions.get(uuid);
            if (session != null) {
                String loc = session.getPlayer().getLocale() != null ? session.getPlayer().getLocale().toString().toLowerCase() : "pt_br";
                String msg = bundle.get(loc, "lobby.error.route_closed", Map.of());
                session.getPlayer().kick(Component.text(msg, NamedTextColor.RED));
                removeSession(uuid, "route_closed");
            }
        }
    }

    private void tickAll() {
        Instant now = Instant.now();
        for (LobbySession session : sessions.values()) {
            session.tick(now);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
        for (LobbySession session : sessions.values()) {
            String loc = session.getPlayer().getLocale() != null ? session.getPlayer().getLocale().toString().toLowerCase() : "pt_br";
            String msg = bundle.get(loc, "lobby.error.restarting", Map.of());
            session.getPlayer().kick(Component.text(msg, NamedTextColor.RED));
        }
        sessions.clear();
        routeSessions.clear();
    }
}
