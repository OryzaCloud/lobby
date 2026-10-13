package com.oryza.lobby.isolation;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerMoveEvent;

public final class IsolationManager {
    public static void apply(GlobalEventHandler handler) {
        handler.addListener(PlayerChatEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerCommandEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockPlaceEvent.class, event -> event.setCancelled(true));
        handler.addListener(ItemDropEvent.class, event -> event.setCancelled(true));

        handler.addListener(PlayerMoveEvent.class, event -> {
            if (event.getNewPosition().samePoint(event.getPlayer().getPosition())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    public static void configurePlayer(Player player) {
        player.updateViewableRule(target -> target == player);
        player.updateViewerRule(target -> target == player || !(target instanceof Player));
    }
}
