package org.kylan1940.wordchain.message;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
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

    // Shows a single line above the hotbar (used for the turn timer)
    public static void actionBar(Player player, String path, String... placeholders) {
        String message = WordChain.getInstance().getConfig().getString(path);
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(color(replace(message, placeholders))));
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
