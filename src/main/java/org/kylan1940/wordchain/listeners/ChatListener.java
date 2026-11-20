package org.kylan1940.wordchain.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.game.RoomManager;
import org.kylan1940.wordchain.game.WordChainGame;

public class ChatListener implements Listener {

    private final WordChain plugin;
    private final RoomManager manager;

    public ChatListener(WordChain plugin, RoomManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // LOWEST so we get the message first, before other chat plugins
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Only the player whose turn it is (in their room) gets their chat used as a word.
        // Everybody else can still chat normally.
        WordChainGame game = manager.getRoom(player);
        if (game == null || !game.isCurrentPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        // Chat events run async, but the game must run on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> game.handleWord(player, message));
    }
}