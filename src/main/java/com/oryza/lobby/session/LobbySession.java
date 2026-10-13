package com.oryza.lobby.session;

import com.oryza.lobby.display.PlayerPanel;
import com.oryza.lobby.i18n.MessageBundle;
import com.oryza.lobby.scene.SceneBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.network.packet.server.common.TransferPacket;

public final class LobbySession {
    public enum State {
        WAITING,
        TRANSFERRING,
        CLOSED
    }

    private final UUID sessionId = UUID.randomUUID();
    private final Player player;
    private final SharedInstance instance;
    private final String routeId;
    private final String hostname;
    private final Instant joinedAt = Instant.now();
    private final MessageBundle bundle;
    private final PlayerPanel frontPanel;
    private final PlayerPanel backPanel;

    private State state = State.WAITING;
    private String serverState = "offline";
    private Duration maxWait = Duration.ofMinutes(10);
    private Instant transferredAt = null;

    public LobbySession(Player player, SharedInstance instance, String routeId, String hostname, MessageBundle bundle) {
        this.player = player;
        this.instance = instance;
        this.routeId = routeId;
        this.hostname = hostname;
        this.bundle = bundle;
        this.frontPanel = new PlayerPanel(instance, SceneBuilder.PANEL_FRONT_POSITION, bundle);
        this.backPanel = new PlayerPanel(instance, SceneBuilder.PANEL_BACK_POSITION, bundle);
    }

    private boolean spawned = false;

    public void onSpawn() {
        this.spawned = true;
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.addEffect(new net.minestom.server.potion.Potion(
            net.minestom.server.potion.PotionEffect.NIGHT_VISION,
            0,
            net.minestom.server.potion.Potion.INFINITE_DURATION,
            (byte) 0
        ));
        updatePanel(null);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Player getPlayer() {
        return player;
    }

    public SharedInstance getInstance() {
        return instance;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getHostname() {
        return hostname;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setServerState(String serverState) {
        this.serverState = serverState;
        updatePanel(null);
    }

    public void setMaxWait(Duration maxWait) {
        this.maxWait = maxWait;
    }

    public void updatePanel(String customMessage) {
        if (!spawned) {
            return;
        }
        String locale = playerLocale();
        frontPanel.update(locale, hostname, serverState, customMessage);
        backPanel.update(locale, hostname, serverState, customMessage);
    }

    public void tick(Instant now) {
        if (state == State.CLOSED) {
            return;
        }

        long elapsedSec = Duration.between(joinedAt, now).toSeconds();
        long remainingSec = maxWait.toSeconds() - elapsedSec;

        if (remainingSec <= 0) {
            state = State.CLOSED;
            String msg = bundle.get(playerLocale(), "lobby.error.timeout", Map.of());
            player.kick(Component.text(msg, NamedTextColor.RED));
            return;
        }

        if (state == State.TRANSFERRING && transferredAt != null) {
            if (Duration.between(transferredAt, now).toSeconds() > 10) {
                state = State.CLOSED;
                String msg = bundle.get(playerLocale(), "lobby.error.unavailable", Map.of());
                player.kick(Component.text(msg, NamedTextColor.RED));
                return;
            }
        }

        String timeStr = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60);
        String actionKey = remainingSec <= 60 ? "lobby.actionbar.timeout" : "lobby.actionbar.waiting";
        String remainStr = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
        String actionText = bundle.get(playerLocale(), actionKey, Map.of("elapsed", timeStr, "remaining", remainStr));
        player.sendActionBar(Component.text(actionText, NamedTextColor.YELLOW));
    }

    public void transfer(String transferHost, int transferPort) {
        if (state == State.CLOSED) {
            return;
        }
        this.state = State.TRANSFERRING;
        this.serverState = "online";
        this.transferredAt = Instant.now();
        updatePanel(null);

        player.sendPacket(new TransferPacket(transferHost, transferPort));
    }

    public void close(String reason) {
        this.state = State.CLOSED;
        frontPanel.remove();
        backPanel.remove();
    }

    private String playerLocale() {
        if (player.getLocale() != null && !player.getLocale().getLanguage().isEmpty()) {
            return player.getLocale().toString().toLowerCase();
        }
        return "pt_br";
    }
}
