package org.kylan1940.wordchain.game;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.message.MessageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of all rooms, which player is in which room, and the pending invites.
 *
 * Flow:
 *   /wc join            -> creates a room, you are the host
 *   /wc invite <player> -> (host) invite a player
 *   /wc accept          -> (invited player) join the room
 *   /wc start           -> (host) start the game
 */
public class RoomManager {

    // An invite to a room that expires after some time
    private static class Invite {
        private final WordChainGame room;
        private final long expires; // System.currentTimeMillis() when it expires

        private Invite(WordChainGame room, long expires) {
            this.room = room;
            this.expires = expires;
        }
    }

    private final WordChain plugin;
    private final WordDictionary dictionary;

    private final List<WordChainGame> rooms = new ArrayList<>();
    private final Map<UUID, WordChainGame> playerRoom = new HashMap<>();
    // invited player -> list of invites (a player can be invited by more than one host)
    private final Map<UUID, List<Invite>> invites = new HashMap<>();

    public RoomManager(WordChain plugin, WordDictionary dictionary) {
        this.plugin = plugin;
        this.dictionary = dictionary;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    // The room the player is in (null if none)
    public WordChainGame getRoom(Player player) {
        return playerRoom.get(player.getUniqueId());
    }

    public List<WordChainGame> getRooms() {
        return rooms;
    }

    // Names of the hosts who have a valid invite for this player (used by tab completion)
    public List<String> getInviterNames(Player player) {
        List<String> names = new ArrayList<>();
        for (Invite invite : getValidInvites(player.getUniqueId())) {
            names.add(invite.room.getOwnerName());
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Create / Invite / Accept
    // ------------------------------------------------------------------

    // /wc join
    public void createRoom(Player host) {
        if (getRoom(host) != null) {
            MessageUtil.send(host, "messages.already-in-room");
            return;
        }

        WordChainGame room = new WordChainGame(plugin, dictionary, this, host.getUniqueId());
        rooms.add(room);
        playerRoom.put(host.getUniqueId(), room);
        room.addPlayer(host, false);

        // Joining by yourself isn't possible anymore, so clear invites you may have had
        invites.remove(host.getUniqueId());

        MessageUtil.send(host, "messages.room-created");
    }

    // /wc invite <player>
    public void invite(Player host, String targetName) {
        WordChainGame room = getRoom(host);
        if (room == null) {
            MessageUtil.send(host, "messages.not-in-room");
            return;
        }
        if (!room.isOwner(host)) {
            MessageUtil.send(host, "messages.not-host");
            return;
        }
        if (room.getState() == WordChainGame.State.RUNNING) {
            MessageUtil.send(host, "messages.room-not-waiting");
            return;
        }
        if (room.isFull()) {
            MessageUtil.send(host, "messages.room-full");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(host, "messages.player-offline", "%player%", targetName);
            return;
        }
        if (target.equals(host)) {
            MessageUtil.send(host, "messages.cannot-invite-self");
            return;
        }
        if (getRoom(target) != null) {
            MessageUtil.send(host, "messages.target-in-room", "%player%", target.getName());
            return;
        }

        // Already invited by this room?
        for (Invite invite : getValidInvites(target.getUniqueId())) {
            if (invite.room == room) {
                MessageUtil.send(host, "messages.already-invited", "%player%", target.getName());
                return;
            }
        }

        int seconds = plugin.getConfig().getInt("room.invite-timeout", 60);
        long expires = System.currentTimeMillis() + seconds * 1000L;
        invites.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(new Invite(room, expires));

        MessageUtil.send(host, "messages.invite-sent", "%player%", target.getName());
        MessageUtil.send(target, "messages.invite-received",
                "%player%", host.getName(),
                "%seconds%", String.valueOf(seconds));
    }

    // /wc accept [host]
    public void accept(Player player, String hostName) {
        if (getRoom(player) != null) {
            MessageUtil.send(player, "messages.already-in-room");
            return;
        }

        List<Invite> valid = getValidInvites(player.getUniqueId());
        if (valid.isEmpty()) {
            MessageUtil.send(player, "messages.no-invite");
            return;
        }

        // Pick the invite: by host name, or the only one we have
        Invite chosen = null;
        if (hostName != null) {
            for (Invite invite : valid) {
                if (invite.room.getOwnerName().equalsIgnoreCase(hostName)) {
                    chosen = invite;
                }
            }
            if (chosen == null) {
                MessageUtil.send(player, "messages.no-invite-from", "%player%", hostName);
                return;
            }
        } else if (valid.size() == 1) {
            chosen = valid.get(0);
        } else {
            MessageUtil.send(player, "messages.multiple-invites");
            return;
        }

        WordChainGame room = chosen.room;
        if (!rooms.contains(room) || room.getState() == WordChainGame.State.RUNNING) {
            MessageUtil.send(player, "messages.room-not-waiting");
            invites.remove(player.getUniqueId());
            return;
        }
        if (room.isFull()) {
            MessageUtil.send(player, "messages.room-full");
            return;
        }

        invites.remove(player.getUniqueId());
        playerRoom.put(player.getUniqueId(), room);
        room.addPlayer(player, true);
    }

    // /wc deny
    public void deny(Player player) {
        if (getValidInvites(player.getUniqueId()).isEmpty()) {
            MessageUtil.send(player, "messages.no-invite");
            return;
        }
        invites.remove(player.getUniqueId());
        MessageUtil.send(player, "messages.invite-denied");
    }

    // ------------------------------------------------------------------
    // Leave / Start / Stop
    // ------------------------------------------------------------------

    // /wc leave and also used when a player disconnects
    public void leave(Player player) {
        invites.remove(player.getUniqueId());

        WordChainGame room = getRoom(player);
        if (room == null) {
            return;
        }

        room.leave(player);
        playerRoom.remove(player.getUniqueId());

        // Nobody left in the room, close it
        if (room.getPlayers().isEmpty()) {
            removeRoom(room);
        }
    }

    // /wc start
    public void start(Player host) {
        WordChainGame room = getRoom(host);
        if (room == null) {
            MessageUtil.send(host, "messages.not-in-room");
            return;
        }
        if (!room.isOwner(host)) {
            MessageUtil.send(host, "messages.not-host");
            return;
        }
        if (room.getState() != WordChainGame.State.WAITING) {
            MessageUtil.send(host, "messages.room-not-waiting");
            return;
        }
        if (!room.start()) {
            MessageUtil.send(host, "messages.cannot-start",
                    "%min%", String.valueOf(plugin.getConfig().getInt("game.min-players", 2)));
        }
    }

    // /wc stop            -> the host closes their own room
    // /wc stop <host>     -> an admin closes the room of that host
    public void stop(CommandSender sender, String hostName) {
        WordChainGame room = null;

        if (hostName == null) {
            if (!(sender instanceof Player)) {
                MessageUtil.send(sender, "messages.only-player");
                return;
            }
            Player player = (Player) sender;
            room = getRoom(player);
            if (room == null) {
                MessageUtil.send(player, "messages.not-in-room");
                return;
            }
            if (!room.isOwner(player)) {
                MessageUtil.send(player, "messages.not-host");
                return;
            }
        } else {
            if (!sender.hasPermission("wordchain-stop.command")) {
                MessageUtil.send(sender, "messages.no-permission");
                return;
            }
            for (WordChainGame r : rooms) {
                if (r.getOwnerName().equalsIgnoreCase(hostName)) {
                    room = r;
                }
            }
            if (room == null) {
                MessageUtil.send(sender, "messages.room-not-found", "%player%", hostName);
                return;
            }
        }

        room.stop();
        MessageUtil.send(sender, "messages.stopped");
    }

    // ------------------------------------------------------------------
    // Internal (used by WordChainGame)
    // ------------------------------------------------------------------

    // Player is no longer in a room (eliminated)
    public void unmap(UUID uuid) {
        playerRoom.remove(uuid);
    }

    public void removeRoom(WordChainGame room) {
        rooms.remove(room);
        playerRoom.values().removeIf(r -> r == room);
        for (List<Invite> list : invites.values()) {
            list.removeIf(invite -> invite.room == room);
        }
    }

    // Server shutdown / plugin disable: close every room without messages
    public void shutdown() {
        for (WordChainGame room : new ArrayList<>(rooms)) {
            room.shutdown();
        }
        rooms.clear();
        playerRoom.clear();
        invites.clear();

        // Also closes stages that are waiting to finish (winner view)
        GameStage.stopAll();
    }

    // Removes expired invites and returns the ones that are still valid
    private List<Invite> getValidInvites(UUID uuid) {
        List<Invite> list = invites.get(uuid);
        if (list == null) {
            return new ArrayList<>();
        }
        long now = System.currentTimeMillis();
        list.removeIf(invite -> invite.expires < now || !rooms.contains(invite.room));
        return list;
    }
}