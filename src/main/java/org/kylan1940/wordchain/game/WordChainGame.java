package org.kylan1940.wordchain.game;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.kylan1940.wordchain.WordChain;
import org.kylan1940.wordchain.message.MessageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One Word Chain room (one game). Rooms are created and tracked by RoomManager.
 *
 * Flow:  WAITING (host invites players)  ->  host types /wc start  ->  STARTING (countdown)
 *        ->  RUNNING  ->  game over, the room is closed
 *
 * Rules:
 *  - Players take turns typing an English word in chat.
 *  - The word must start with the last letter of the previous word.
 *  - A word can only be used once per game.
 *  - Every player has lives (game health) for the WHOLE game.
 *  - Every turn a player also has turn health (tries). Each wrong word costs 1 try.
 *    When the tries are gone, or the timer runs out, the player loses 1 game life
 *    and the turn goes to the next player. At 0 lives the player is eliminated.
 *    Last player standing wins.
 */
public class WordChainGame {

    public enum State {
        WAITING, STARTING, RUNNING
    }

    private final WordChain plugin;
    private final WordDictionary dictionary;
    private final RoomManager manager;

    // The host of the room (the player who created it). Only the host can invite and start.
    private UUID owner;

    private State state = State.WAITING;

    // Players who joined. While RUNNING this only holds the players still alive, in turn order.
    private final List<UUID> players = new ArrayList<>();
    // Everyone who started the match (used to send the final messages)
    private final Set<UUID> participants = new HashSet<>();
    // Lives left per player (global for the whole match)
    private final Map<UUID, Integer> lives = new HashMap<>();
    // Words already used in this match
    private final Set<String> usedWords = new HashSet<>();

    private int currentIndex = 0;       // whose turn it is (index in players)
    private int round = 1;              // +1 every time all players had a turn
    private char requiredLetter = 0;    // 0 = first word, any letter is allowed
    private int turnTries = 0;          // wrong words left in the current turn (resets every turn)
    private int timeLeft = 0;           // seconds left for the current turn
    private int countdown = 0;          // seconds left before the game starts

    private BukkitTask countdownTask;
    private BukkitTask turnTask;

    public WordChainGame(WordChain plugin, WordDictionary dictionary, RoomManager manager, UUID owner) {
        this.plugin = plugin;
        this.dictionary = dictionary;
        this.manager = manager;
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public State getState() {
        return state;
    }

    public List<UUID> getPlayers() {
        return players;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return getName(owner);
    }

    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(owner);
    }

    public boolean isFull() {
        return players.size() >= plugin.getConfig().getInt("game.max-players", 10);
    }

    public boolean isInGame(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean isCurrentPlayer(Player player) {
        return state == State.RUNNING
                && !players.isEmpty()
                && players.get(currentIndex).equals(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Join / Leave
    // ------------------------------------------------------------------

    // Adds a player to the room (the manager already checked everything).
    // announce = false is used for the host when the room is created.
    public void addPlayer(Player player, boolean announce) {
        players.add(player.getUniqueId());

        if (announce) {
            broadcast("messages.player-joined",
                    "%player%", player.getName(),
                    "%players%", String.valueOf(players.size()),
                    "%max%", String.valueOf(plugin.getConfig().getInt("game.max-players", 10)));
        }
    }

    public void leave(Player player) {
        if (!isInGame(player)) {
            return;
        }

        if (state == State.RUNNING) {
            // Leaving in the middle of a match = eliminated
            eliminate(player.getUniqueId(), "messages.eliminated-left");
            return;
        }

        players.remove(player.getUniqueId());
        MessageUtil.send(player, "messages.you-left");
        broadcast("messages.player-left", "%player%", player.getName());

        // The host left: the next player becomes the new host
        if (player.getUniqueId().equals(owner) && !players.isEmpty()) {
            owner = players.get(0);
            broadcast("messages.new-host", "%player%", getName(owner));
        }

        // Not enough players anymore, cancel the countdown
        if (state == State.STARTING && players.size() < plugin.getConfig().getInt("game.min-players", 2)) {
            cancelCountdown();
            state = State.WAITING;
            broadcast("messages.countdown-cancelled");
        }
    }

    // ------------------------------------------------------------------
    // Start / Stop
    // ------------------------------------------------------------------

    // Used by /wordchain start (host). Starts the countdown, the game starts when it reaches 0.
    public boolean start() {
        if (state != State.WAITING) {
            return false;
        }
        if (players.size() < plugin.getConfig().getInt("game.min-players", 2)) {
            return false;
        }
        startCountdown();
        return true;
    }

    private void startCountdown() {
        state = State.STARTING;
        countdown = plugin.getConfig().getInt("game.start-countdown", 10);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) {
                cancelCountdown();
                startGame();
                return;
            }
            // Only announce at 10, 5, 4, 3, 2, 1 so the chat doesn't get spammed
            if (countdown <= 5 || countdown % 10 == 0) {
                broadcast("messages.countdown", "%seconds%", String.valueOf(countdown));
            }
            countdown--;
        }, 0L, 20L);
    }

    private void startGame() {
        state = State.RUNNING;
        usedWords.clear();
        participants.clear();
        participants.addAll(players);

        // Everyone starts with the same amount of lives
        lives.clear();
        int startLives = Math.max(1, plugin.getConfig().getInt("game.lives", 3));
        for (UUID uuid : players) {
            lives.put(uuid, startLives);
        }

        Collections.shuffle(players); // random turn order
        currentIndex = 0;
        round = 1;
        requiredLetter = 0;

        broadcast("messages.game-started");
        beginTurn();
    }

    // Used by /wordchain stop
    public void stop() {
        if (state == State.WAITING && players.isEmpty()) {
            return;
        }
        broadcastToParticipantsAndPlayers("messages.game-stopped");
        reset();
    }

    // Used when the server shuts down / reloads (no messages)
    public void shutdown() {
        reset();
    }

    private void reset() {
        cancelCountdown();
        cancelTurnTask();
        players.clear();
        participants.clear();
        lives.clear();
        usedWords.clear();
        currentIndex = 0;
        round = 1;
        requiredLetter = 0;
        state = State.WAITING;

        // The room is closed after the game / stop
        manager.removeRoom(this);
    }

    // ------------------------------------------------------------------
    // Turns
    // ------------------------------------------------------------------

    private void beginTurn() {
        Player current = Bukkit.getPlayer(players.get(currentIndex));
        String name = getName(players.get(currentIndex));

        timeLeft = getTurnTime();
        turnTries = Math.max(1, plugin.getConfig().getInt("game.turn-health", 3));

        broadcast("messages.your-turn-broadcast",
                "%player%", name,
                "%letter%", getLetterText(),
                "%time%", String.valueOf(timeLeft),
                "%lives%", String.valueOf(getLives(players.get(currentIndex))),
                "%tries%", String.valueOf(turnTries));
        if (current != null) {
            MessageUtil.send(current, "messages.your-turn",
                    "%letter%", getLetterText(),
                    "%time%", String.valueOf(timeLeft),
                    "%lives%", String.valueOf(getLives(players.get(currentIndex))),
                    "%tries%", String.valueOf(turnTries));
        }
        updateActionBar();

        // Runs every second: count down, and eliminate the player when time is up
        turnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            timeLeft--;
            if (timeLeft <= 0) {
                loseLife(players.get(currentIndex), "messages.life-lost-timeout", "messages.eliminated-timeout");
                return;
            }
            updateActionBar();
        }, 20L, 20L);
    }

    // The turn time gets shorter every round, but never below min-turn-time
    private int getTurnTime() {
        FileConfiguration config = plugin.getConfig();
        int time = config.getInt("game.turn-time", 20)
                - (round - 1) * config.getInt("game.time-decrease-per-round", 1);
        return Math.max(time, config.getInt("game.min-turn-time", 8));
    }

    private void nextTurn() {
        cancelTurnTask();
        currentIndex++;
        if (currentIndex >= players.size()) {
            currentIndex = 0;
            round++;
        }
        beginTurn();
    }

    private void updateActionBar() {
        String name = getName(players.get(currentIndex));
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.actionBar(player, "messages.actionbar",
                        "%player%", name,
                        "%letter%", getLetterText(),
                        "%time%", String.valueOf(timeLeft),
                        "%lives%", String.valueOf(getLives(players.get(currentIndex))),
                        "%tries%", String.valueOf(turnTries));
            }
        }
    }

    // -1 game life (time ran out, or no tries left this turn).
    // At 0 lives the player is eliminated, otherwise the next player goes.
    private void loseLife(UUID uuid, String lostKey, String eliminatedKey) {
        int left = getLives(uuid) - 1;
        lives.put(uuid, left);

        if (left <= 0) {
            eliminate(uuid, eliminatedKey);
            return;
        }

        broadcast(lostKey,
                "%player%", getName(uuid),
                "%lives%", String.valueOf(left));
        nextTurn();
    }

    private int getLives(UUID uuid) {
        return lives.getOrDefault(uuid, 0);
    }

    // ------------------------------------------------------------------
    // Words
    // ------------------------------------------------------------------

    // Called (on the main thread) when the player whose turn it is types something in chat
    public void handleWord(Player player, String input) {
        if (!isCurrentPlayer(player)) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String word = input.trim().toLowerCase();

        // 1. Only letters a-z
        if (!word.matches("[a-z]+")) {
            wrongAnswer(player, "messages.invalid-characters");
            return;
        }
        // 2. Long enough
        int minLength = config.getInt("game.min-word-length", 3);
        if (word.length() < minLength) {
            wrongAnswer(player, "messages.too-short", "%min%", String.valueOf(minLength));
            return;
        }
        // 3. Starts with the right letter
        if (requiredLetter != 0 && word.charAt(0) != requiredLetter) {
            wrongAnswer(player, "messages.wrong-letter", "%letter%", getLetterText());
            return;
        }
        // 4. Not used before
        if (usedWords.contains(word)) {
            wrongAnswer(player, "messages.already-used", "%word%", word);
            return;
        }
        // 5. Real English word
        if (!dictionary.contains(word)) {
            wrongAnswer(player, "messages.not-a-word", "%word%", word);
            return;
        }

        // Word accepted!
        usedWords.add(word);
        requiredLetter = word.charAt(word.length() - 1);

        broadcast("messages.word-accepted",
                "%player%", player.getName(),
                "%word%", word,
                "%letter%", getLetterText());

        nextTurn();
    }

    // A wrong word costs 1 try of this turn. When no tries are left the player loses 1 game life.
    private void wrongAnswer(Player player, String path, String... placeholders) {
        MessageUtil.send(player, path, placeholders);

        turnTries--;
        if (turnTries <= 0) {
            loseLife(player.getUniqueId(), "messages.life-lost-wrong", "messages.eliminated-wrong");
            return;
        }

        MessageUtil.send(player, "messages.tries-left", "%tries%", String.valueOf(turnTries));
        updateActionBar();
    }

    // ------------------------------------------------------------------
    // Eliminate / End
    // ------------------------------------------------------------------

    private void eliminate(UUID uuid, String messageKey) {
        int index = players.indexOf(uuid);
        if (index == -1) {
            return;
        }
        boolean wasCurrent = (index == currentIndex);

        // Tell everyone BEFORE removing so the eliminated player also sees it
        broadcast(messageKey, "%player%", getName(uuid));
        players.remove(index);
        manager.unmap(uuid); // the eliminated player is free to join another room

        // Only one player left (or nobody) -> game over
        if (players.size() <= 1) {
            endGame();
            return;
        }

        // Keep currentIndex pointing at the same player after the removal
        if (index < currentIndex) {
            currentIndex--;
        }

        // If it was the eliminated player's turn, the next player takes over
        if (wasCurrent) {
            cancelTurnTask();
            if (currentIndex >= players.size()) {
                currentIndex = 0;
                round++;
            }
            beginTurn();
        }
    }

    private void endGame() {
        cancelTurnTask();

        if (players.size() == 1) {
            UUID winnerId = players.get(0);
            String winner = getName(winnerId);

            broadcastToParticipantsAndPlayers("messages.winner",
                    "%player%", winner,
                    "%words%", String.valueOf(usedWords.size()),
                    "%rounds%", String.valueOf(round));

            // Run the reward commands from config.yml as console
            for (String command : plugin.getConfig().getStringList("rewards.winner-commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", winner));
            }
        } else {
            broadcastToParticipantsAndPlayers("messages.no-winner");
        }

        reset();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelTurnTask() {
        if (turnTask != null) {
            turnTask.cancel();
            turnTask = null;
        }
    }

    private String getLetterText() {
        if (requiredLetter == 0) {
            return plugin.getConfig().getString("messages.any-letter", "any");
        }
        return String.valueOf(requiredLetter).toUpperCase();
    }

    public String getName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    // Message for everyone who is currently in the game
    private void broadcast(String path, String... placeholders) {
        for (UUID uuid : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.send(player, path, placeholders);
            }
        }
    }

    // Same as broadcast, but also includes players who were already eliminated
    private void broadcastToParticipantsAndPlayers(String path, String... placeholders) {
        Set<UUID> everyone = new HashSet<>(participants);
        everyone.addAll(players);
        for (UUID uuid : everyone) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.send(player, path, placeholders);
            }
        }
    }
}