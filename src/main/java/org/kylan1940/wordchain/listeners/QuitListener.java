package org.kylan1940.wordchain.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kylan1940.wordchain.game.RoomManager;

public class QuitListener implements Listener {

    private final RoomManager manager;

    public QuitListener(RoomManager manager) {
        this.manager = manager;
    }

    // If a player disconnects, remove them from the room / eliminate them from the match
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.leave(event.getPlayer());
    }
}