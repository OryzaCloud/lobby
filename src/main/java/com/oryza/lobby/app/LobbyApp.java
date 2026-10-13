package com.oryza.lobby.app;

import com.oryza.lobby.config.Config;
import com.oryza.lobby.health.HealthServer;
import com.oryza.lobby.i18n.MessageBundle;
import com.oryza.lobby.isolation.IsolationManager;
import com.oryza.lobby.messaging.AmqpClient;
import com.oryza.lobby.messaging.MessageSigner;
import com.oryza.lobby.scene.SceneBuilder;
import com.oryza.lobby.security.TokenVerifier;
import com.oryza.lobby.session.LobbySession;
import com.oryza.lobby.session.SessionManager;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.SharedInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LobbyApp {
    private static final Logger log = LoggerFactory.getLogger(LobbyApp.class);

    private final Config config;
    private final TokenVerifier tokenVerifier;
    private final MessageSigner messageSigner;
    private final MessageBundle messageBundle;

    private MinecraftServer minecraftServer;
    private InstanceContainer templateInstance;
    private SessionManager sessionManager;
    private AmqpClient amqpClient;
    private HealthServer healthServer;

    public LobbyApp(Config config) {
        this.config = config;
        this.tokenVerifier = new TokenVerifier(config.lobbyTokenKeys(), config.lobbyTokenMaxAge().toSeconds());
        this.messageSigner = new MessageSigner(config.messagingHmacKeys(), config.messagingHmacActiveKeyId(), config.messagingMaxClockSkew().toSeconds());
        this.messageBundle = new MessageBundle(config.lobbyDefaultLocale(), config.lobbyMessagesDir());
    }

    public void start() throws IOException {
        minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        this.templateInstance = instanceManager.createInstanceContainer();
        SceneBuilder.build(templateInstance);

        this.amqpClient = new AmqpClient(
            config.amqpUrl(),
            config.lobbyInstanceId(),
            config.lobbyAdvertiseAddress(),
            config.lobbyAdvertisePort(),
            config.lobbyMaxSessions(),
            messageSigner,
            () -> sessionManager != null ? sessionManager.getSessionCount() : 0
        );

        this.sessionManager = new SessionManager(messageBundle, amqpClient, config.lobbyTransferBatch(), config.lobbyTransferSpreadMs());

        amqpClient.setRouteStateConsumer(event -> sessionManager.onRouteStateChanged(event.routeId(), event.state()));
        amqpClient.setRouteReadyConsumer(event -> sessionManager.onRouteReady(event.routeId(), event.transferHost(), event.transferPort()));
        amqpClient.setRouteClosedConsumer(event -> sessionManager.onRouteClosed(event.routeId(), event.reason()));

        amqpClient.start();

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        IsolationManager.apply(globalEventHandler);

        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();
            event.setSpawningInstance(templateInstance);
            String rawAddress = player.getPlayerConnection().getServerAddress();

            TokenVerifier.VerifiedToken token = tokenVerifier.verify(rawAddress);
            if (token == null) {
                String msg = messageBundle.get(config.lobbyDefaultLocale(), "lobby.error.invalid_entry", Map.of());
                player.kick(Component.text(msg, NamedTextColor.RED));
                return;
            }

            if (sessionManager.getSessionCount() >= config.lobbyMaxSessions()) {
                String msg = messageBundle.get(config.lobbyDefaultLocale(), "lobby.error.full", Map.of());
                player.kick(Component.text(msg, NamedTextColor.RED));
                return;
            }

            SharedInstance playerInstance = instanceManager.createSharedInstance(templateInstance);
            event.setSpawningInstance(playerInstance);
            player.setRespawnPoint(SceneBuilder.SPAWN_POSITION);

            LobbySession session = sessionManager.createSession(player, playerInstance, token.routeId(), token.hostname());

            amqpClient.resolveTarget(token.routeId(), config.lobbyRpcTimeout())
                .thenAccept(resp -> {
                    session.setServerState(resp.state());
                    session.setMaxWait(Duration.ofSeconds(resp.maxWaitSeconds()));
                    if ("online".equalsIgnoreCase(resp.state())) {
                        session.transfer(resp.transferHost(), resp.transferPort());
                    }
                })
                .exceptionally(ex -> {
                    String msg = messageBundle.get(config.lobbyDefaultLocale(), "lobby.error.unavailable", Map.of());
                    player.kick(Component.text(msg, NamedTextColor.RED));
                    sessionManager.removeSession(player.getUuid(), "resolve_failed");
                    return null;
                });
        });

        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            IsolationManager.configurePlayer(player);
            LobbySession session = sessionManager.getSession(player.getUuid());
            if (session != null) {
                session.onSpawn();
            }
        });

        globalEventHandler.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            LobbySession session = sessionManager.getSession(player.getUuid());
            if (session != null) {
                sessionManager.removeSession(player.getUuid(), "disconnected");
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    if (session.getInstance().getPlayers().isEmpty()) {
                        MinecraftServer.getInstanceManager().unregisterInstance(session.getInstance());
                    }
                });
            }
        });

        this.healthServer = new HealthServer(config.healthAddr(), config.healthToken(), () -> true);
        healthServer.start();

        String[] bindParts = config.lobbyBind().split(":", 2);
        String host = bindParts[0];
        int port = Integer.parseInt(bindParts[1]);

        log.info("Starting Minestom Lobby on {}:{}", host, port);
        minecraftServer.start(host, port);
    }

    public void stop() {
        if (healthServer != null) {
            healthServer.stop();
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (amqpClient != null) {
            amqpClient.shutdown();
        }
        if (minecraftServer != null) {
            MinecraftServer.stopCleanly();
        }
    }
}
