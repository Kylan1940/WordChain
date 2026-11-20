package org.kylan1940.wordchain.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.game.WordChainGame;

public class ChatListener implements Listener {

    private final WordChain plugin;
    private final WordChainGame game;

    public ChatListener(WordChain plugin, WordChainGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    // LOWEST so we get the message first, before other chat plugins
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Only the player whose turn it is gets their chat used as a word.
        // Everybody else can still chat normally.
        if (!game.isCurrentPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        // Chat events run async, but the game must run on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> game.handleWord(player, message));
    }
}
