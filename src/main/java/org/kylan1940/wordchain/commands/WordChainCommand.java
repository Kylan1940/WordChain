package org.kylan1940.wordchain.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.game.WordChainGame;
import org.kylan1940.wordchain.message.MessageUtil;

import java.util.ArrayList;
import java.util.List;

public class WordChainCommand implements CommandExecutor, TabCompleter {

    private final WordChain plugin;
    private final WordChainGame game;

    public WordChainCommand(WordChain plugin, WordChainGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join":
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "messages.only-player");
                    return true;
                }
                if (!sender.hasPermission("wordchain-play.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                game.join((Player) sender);
                return true;

            case "leave":
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "messages.only-player");
                    return true;
                }
                if (!sender.hasPermission("wordchain-play.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                if (!game.isInGame((Player) sender)) {
                    MessageUtil.send(sender, "messages.not-in-game");
                    return true;
                }
                game.leave((Player) sender);
                return true;

            case "status":
                if (!sender.hasPermission("wordchain-play.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                MessageUtil.send(sender, "messages.status",
                        "%state%", game.getState().name(),
                        "%players%", String.valueOf(game.getPlayers().size()));
                return true;

            case "start":
                if (!sender.hasPermission("wordchain-start.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                if (!game.forceStart()) {
                    MessageUtil.send(sender, "messages.cannot-start",
                            "%min%", String.valueOf(plugin.getConfig().getInt("game.min-players", 2)));
                }
                return true;

            case "stop":
                if (!sender.hasPermission("wordchain-stop.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                game.stop();
                MessageUtil.send(sender, "messages.stopped");
                return true;

            case "reload":
                if (!sender.hasPermission("wordchain-reload.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                plugin.reloadConfig();
                MessageUtil.send(sender, "messages.reloaded");
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "messages.help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            addIfAllowed(result, sender, args[0], "join", "wordchain-play.command");
            addIfAllowed(result, sender, args[0], "leave", "wordchain-play.command");
            addIfAllowed(result, sender, args[0], "status", "wordchain-play.command");
            addIfAllowed(result, sender, args[0], "start", "wordchain-start.command");
            addIfAllowed(result, sender, args[0], "stop", "wordchain-stop.command");
            addIfAllowed(result, sender, args[0], "reload", "wordchain-reload.command");
        }
        return result;
    }

    private void addIfAllowed(List<String> result, CommandSender sender, String typed, String sub, String permission) {
        if (sender.hasPermission(permission) && sub.startsWith(typed.toLowerCase())) {
            result.add(sub);
        }
    }
}
