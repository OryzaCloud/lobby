package com.oryza.lobby.display;

import com.oryza.lobby.i18n.MessageBundle;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;

public final class PlayerPanel {
    private final Entity panelEntity;
    private final MessageBundle bundle;

    public PlayerPanel(Instance instance, Pos centerPos, MessageBundle bundle) {
        this.bundle = bundle;

        this.panelEntity = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta meta = (TextDisplayMeta) panelEntity.getEntityMeta();
        meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
        meta.setBackgroundColor(0);
        meta.setBrightness(15, 15);
        meta.setShadow(true);
        meta.setSeeThrough(true);
        meta.setLineWidth(240);
        panelEntity.setInstance(instance, centerPos);
    }

    public void update(String locale, String hostname, String state, String customMessage) {
        String safeState = state != null && !state.isEmpty() ? state.toLowerCase() : "offline";
        String hostText = bundle.get(locale, "lobby.panel.hostname", Map.of("hostname", hostname));

        String statusKey = "lobby.panel.status." + safeState;
        String statusText = bundle.get(locale, statusKey, Map.of());
        if (statusText.equals(statusKey)) {
            statusText = bundle.get(locale, "lobby.panel.status.offline", Map.of());
        }

        String msgKey = "lobby.panel.message." + safeState;
        String msgText = customMessage != null ? customMessage : bundle.get(locale, msgKey, Map.of());
        if (msgText.equals(msgKey)) {
            msgText = bundle.get(locale, "lobby.panel.message.offline", Map.of());
        }

        NamedTextColor statusColor = "online".equalsIgnoreCase(safeState) ? NamedTextColor.GREEN : ("starting".equalsIgnoreCase(safeState) ? NamedTextColor.YELLOW : NamedTextColor.RED);

        Component fullText = Component.empty()
            .append(Component.text(hostText, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text(statusText, statusColor))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text(msgText, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Powered by Oryza", NamedTextColor.GRAY));

        ((TextDisplayMeta) panelEntity.getEntityMeta()).setText(fullText);
    }

    public void remove() {
        panelEntity.remove();
    }
}
