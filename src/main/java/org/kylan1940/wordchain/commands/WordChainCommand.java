package org.kylan1940.wordchain.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.game.RoomManager;
import org.kylan1940.wordchain.game.WordChainGame;
import org.kylan1940.wordchain.message.MessageUtil;

import java.util.ArrayList;
import java.util.List;

public class WordChainCommand implements CommandExecutor, TabCompleter {

    private final WordChain plugin;
    private final RoomManager manager;

    public WordChainCommand(WordChain plugin, RoomManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ---- Commands for players (permission: wordchain-play.command) ----
            case "join":
            case "leave":
            case "invite":
            case "accept":
            case "deny":
            case "start":
            case "status":
                if (!sender.hasPermission("wordchain-play.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "messages.only-player");
                    return true;
                }
                handlePlayerCommand((Player) sender, sub, args);
                return true;

            // ---- /wc stop (host) or /wc stop <host> (admin) ----
            case "stop":
                if (!sender.hasPermission("wordchain-play.command")) {
                    MessageUtil.send(sender, "messages.no-permission");
                    return true;
                }
                manager.stop(sender, args.length >= 2 ? args[1] : null);
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

    private void handlePlayerCommand(Player player, String sub, String[] args) {
        switch (sub) {

            case "join":
                manager.createRoom(player);
                break;

            case "leave":
                if (manager.getRoom(player) == null) {
                    MessageUtil.send(player, "messages.not-in-room");
                    return;
                }
                manager.leave(player);
                break;

            case "invite":
                if (args.length < 2) {
                    MessageUtil.send(player, "messages.usage-invite");
                    return;
                }
                manager.invite(player, args[1]);
                break;

            case "accept":
                manager.accept(player, args.length >= 2 ? args[1] : null);
                break;

            case "deny":
                manager.deny(player);
                break;

            case "start":
                manager.start(player);
                break;

            case "status":
                WordChainGame room = manager.getRoom(player);
                if (room == null) {
                    MessageUtil.send(player, "messages.not-in-room");
                    return;
                }
                MessageUtil.send(player, "messages.status",
                        "%host%", room.getOwnerName(),
                        "%state%", room.getState().name(),
                        "%players%", String.valueOf(room.getPlayers().size()));
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "messages.help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : new String[]{"join", "leave", "invite", "accept", "deny", "start", "stop", "status"}) {
                addIfAllowed(result, sender, args[0], sub, "wordchain-play.command");
            }
            addIfAllowed(result, sender, args[0], "reload", "wordchain-reload.command");
            return result;
        }

        if (args.length == 2) {
            String typed = args[1].toLowerCase();
            String sub = args[0].toLowerCase();

            // /wc invite <player>: online players who are not in a room
            if (sub.equals("invite")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (manager.getRoom(online) == null && online.getName().toLowerCase().startsWith(typed)) {
                        result.add(online.getName());
                    }
                }
            }
            // /wc accept <host>: hosts who invited you
            if (sub.equals("accept") && sender instanceof Player) {
                for (String name : manager.getInviterNames((Player) sender)) {
                    if (name.toLowerCase().startsWith(typed)) {
                        result.add(name);
                    }
                }
            }
            // /wc stop <host>: admin only
            if (sub.equals("stop") && sender.hasPermission("wordchain-stop.command")) {
                for (WordChainGame room : manager.getRooms()) {
                    if (room.getOwnerName().toLowerCase().startsWith(typed)) {
                        result.add(room.getOwnerName());
                    }
                }
            }
        }
        return result;
    }

    private void addIfAllowed(List<String> result, CommandSender sender, String typed, String sub, String permission) {
        if (sender.hasPermission(permission) && sub.startsWith(typed.toLowerCase())) {
            result.add(sub);
        }
    }
}