package org.kylan1940.wordchain.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kylan1940.wordchain.game.WordChainGame;

public class QuitListener implements Listener {

    private final WordChainGame game;

    public QuitListener(WordChainGame game) {
        this.game = game;
    }

    // If a player disconnects, remove them from the lobby / eliminate them from the match
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.leave(event.getPlayer());
    }
}
