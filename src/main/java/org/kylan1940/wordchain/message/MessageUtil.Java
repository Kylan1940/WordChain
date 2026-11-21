package org.kylan1940.wordchain.message;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.kylan1940.wordchain.WordChain;

import java.util.List;

public final class MessageUtil {
    private MessageUtil() {
    }

    public static void send(CommandSender sender, String path, String... placeholders) {
        List<String> messages = WordChain.getInstance().getConfig().getStringList(path);
        if (messages.isEmpty()) {
            String message = WordChain.getInstance().getConfig().getString(path);
            if (message == null || message.isEmpty()) {
                return;
            }
            sender.sendMessage(color(replace(message, placeholders)));
            return;
        }
        for (String message : messages) {
            sender.sendMessage(color(replace(message, placeholders)));
        }
    }

    // Shows a single line above the hotbar (used for the turn timer).
    // Uses the Spigot chat API, so it works on Spigot, Paper and their forks.
    public static void actionBar(Player player, String path, String... placeholders) {
        String message = WordChain.getInstance().getConfig().getString(path);
        if (message == null || message.isEmpty()) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(color(replace(message, placeholders))));
    }

    // Shows a title + subtitle in the middle of the screen.
    // path = for example "titles.winner" (reads path.title and path.subtitle from config.yml)
    public static void title(Player player, String path, String... placeholders) {
        FileConfiguration config = WordChain.getInstance().getConfig();
        String title = config.getString(path + ".title", "");
        String subtitle = config.getString(path + ".subtitle", "");
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }
        player.sendTitle(
                color(replace(title, placeholders)),
                color(replace(subtitle, placeholders)),
                config.getInt("titles.fade-in", 10),
                config.getInt("titles.stay", 70),
                config.getInt("titles.fade-out", 20));
    }

    private static String replace(String message, String... placeholders) {
        String prefix = WordChain.getInstance().getConfig().getString("prefix", "");
        message = message.replace("%prefix%", prefix);
        for (int i = 0; i < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }
        return message;
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

}