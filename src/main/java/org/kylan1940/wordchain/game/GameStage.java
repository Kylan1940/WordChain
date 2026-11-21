package org.kylan1940.wordchain.game;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import org.kylan1940.wordchain.WordChain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The "stage" of a running game: a third person view of the game.
 *
 *  - Every player gets a seat placed in a circle: a figure with the player's own skin
 *    (Mannequin entity, Minecraft 1.21.9+), or on older versions a small figure with their head.
 *  - Every player is put in spectator mode and watches through a camera (an invisible armor stand).
 *  - The camera glides to the player whose turn it is.
 *  - Above the player: the required letter (current turn only) and every word they typed
 *    this turn in white, wrong or right.
 *
 * When the game is over everyone gets their old game mode and location back.
 */
public class GameStage {

    // All stages that exist right now (so we can clean up when the plugin is disabled)
    private static final Set<GameStage> ALL = new HashSet<>();

    // One seat per player
    private static class Seat {
        private UUID uuid;
        private String name;
        private Location location;
        private Entity body;
        private boolean skin; // true = real skin (Mannequin), false = armor stand with head
        private TextDisplay text;
        private final List<String> attempts = new ArrayList<>();
        private boolean eliminated;
    }

    // What a player was before the game
    private static class Saved {
        private GameMode mode;
        private Location location;
    }

    private final WordChain plugin;
    private final List<Player> players;
    private final Location center;

    private final Map<UUID, Seat> seats = new LinkedHashMap<>();
    private final Map<UUID, Saved> viewers = new HashMap<>();

    private ArmorStand camera;
    private BukkitTask keeperTask;
    private BukkitTask moveTask;
    private UUID current;           // whose turn it is
    private String letterText = ""; // the letter shown above the current player
    private boolean firstShot = true;
    private boolean stopped = false;

    public GameStage(WordChain plugin, List<Player> players, Location center) {
        this.plugin = plugin;
        this.players = players;
        this.center = center;
    }

    // Closes every stage (plugin disable)
    public static void stopAll() {
        for (GameStage stage : new ArrayList<>(ALL)) {
            stage.stop();
        }
    }

    // ------------------------------------------------------------------
    // Start / Stop
    // ------------------------------------------------------------------

    public void start() {
        ALL.add(this);
        World world = center.getWorld();
        double radius = cfg().getDouble("stage.radius", 5.0);

        // 1. A seat for every player, in a circle, all facing the center
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            double angle = 2 * Math.PI * i / players.size();

            Location seatLocation = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Seat seat = new Seat();
            seat.uuid = player.getUniqueId();
            seat.name = player.getName();
            seat.location = seatLocation;
            seat.body = spawnSkinBody(world, seatLocation, player);
            seat.skin = seat.body != null;
            if (seat.body == null) {
                seat.body = spawnBody(world, seatLocation, player, i);
            }
            seat.text = spawnText(world, seatLocation, seat.skin);
            seats.put(seat.uuid, seat);
            updateText(seat, false);
        }

        // 2. The camera: an invisible armor stand
        camera = world.spawn(center, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setPersistent(false);
        });

        // 3. Everyone watches through the camera (spectator mode)
        for (Player player : players) {
            Saved saved = new Saved();
            saved.mode = player.getGameMode();
            saved.location = player.getLocation();
            viewers.put(player.getUniqueId(), saved);

            player.teleport(center);
            player.setGameMode(GameMode.SPECTATOR);
        }

        // 4. Keep everyone looking through the camera (they can press sneak to leave it)
        keeperTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : viewers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                if (player.getSpectatorTarget() != camera) {
                    player.setSpectatorTarget(camera);
                }
            }
        }, 2L, 5L);
    }

    // Removes everything and gives every player their old game mode and location back
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        ALL.remove(this);

        if (keeperTask != null) {
            keeperTask.cancel();
        }
        if (moveTask != null) {
            moveTask.cancel();
        }

        for (UUID uuid : new ArrayList<>(viewers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restore(player);
            }
        }
        viewers.clear();

        for (Seat seat : seats.values()) {
            seat.body.remove();
            seat.text.remove();
        }
        seats.clear();
        if (camera != null) {
            camera.remove();
        }
    }

    // Game over: look at the winner for a moment, then stop
    public void finish(UUID winner, int delayTicks) {
        current = null;
        for (Seat seat : seats.values()) {
            updateText(seat, false);
        }
        Seat seat = seats.get(winner);
        if (seat != null) {
            moveCamera(shot(seat), cfg().getInt("stage.move-ticks", 25));
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::stop, Math.max(1, delayTicks));
    }

    // Gives ONE player their old game mode and location back (when they leave the game)
    public void restore(Player player) {
        Saved saved = viewers.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        player.setSpectatorTarget(null);
        player.setGameMode(saved.mode);
        player.teleport(saved.location);
    }

    // ------------------------------------------------------------------
    // Game events (called by WordChainGame)
    // ------------------------------------------------------------------

    // A new turn: the camera glides to this player and the letter appears above them
    public void showTurn(UUID uuid, String letter) {
        Seat seat = seats.get(uuid);
        if (seat == null) {
            return;
        }

        // The previous player keeps their last words above them, but loses the letter
        Seat previous = current != null ? seats.get(current) : null;
        if (previous != null) {
            updateText(previous, false);
        }

        current = uuid;
        letterText = letter;
        seat.attempts.clear();
        updateText(seat, true);

        Location shot = shot(seat);
        if (firstShot) {
            camera.teleport(shot);
            firstShot = false;
        } else {
            moveCamera(shot, cfg().getInt("stage.move-ticks", 25));
        }
    }

    // Every word the player types shows up above them in white (wrong or not)
    public void addAttempt(UUID uuid, String word) {
        Seat seat = seats.get(uuid);
        if (seat == null) {
            return;
        }
        // No color codes from players, and keep it short
        word = word.replaceAll("[&§]", "");
        if (word.length() > 20) {
            word = word.substring(0, 20);
        }
        seat.attempts.add(word);
        if (seat.attempts.size() > 6) {
            seat.attempts.remove(0);
        }
        updateText(seat, uuid.equals(current));
    }

    public void markEliminated(UUID uuid) {
        Seat seat = seats.get(uuid);
        if (seat != null) {
            seat.eliminated = true;
            updateText(seat, false);
        }
    }

    // ------------------------------------------------------------------
    // Camera
    // ------------------------------------------------------------------

    // Where the camera stands to film this seat: in front of the player, looking at them
    private Location shot(Seat seat) {
        double distance = cfg().getDouble("stage.camera-distance", 3.5);
        double height = cfg().getDouble("stage.camera-height", 1.4);
        double lookHeight = cfg().getDouble("stage.look-height", 1.3);
        if (seat.skin) {
            lookHeight += cfg().getDouble("stage.standing-offset", 0.5); // a standing figure is taller
        }

        Vector toCenter = center.toVector().subtract(seat.location.toVector()).setY(0).normalize();
        Location camera = seat.location.clone().add(toCenter.multiply(distance));
        camera.setY(center.getY() + height);

        Vector look = seat.location.toVector().add(new Vector(0, lookHeight, 0));
        camera.setDirection(look.subtract(camera.toVector()));
        return camera;
    }

    // Glides the camera to the target (position AND rotation) in a number of ticks
    private void moveCamera(Location target, int ticks) {
        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }
        if (ticks <= 0) {
            camera.teleport(target);
            return;
        }

        Location from = camera.getLocation();
        int[] step = {0};

        moveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            step[0]++;
            double t = (double) step[0] / ticks;
            if (t >= 1) {
                camera.teleport(target);
                moveTask.cancel();
                moveTask = null;
                return;
            }

            double e = t * t * (3 - 2 * t); // smooth start and end
            Location now = new Location(target.getWorld(),
                    from.getX() + (target.getX() - from.getX()) * e,
                    from.getY() + (target.getY() - from.getY()) * e,
                    from.getZ() + (target.getZ() - from.getZ()) * e);

            // Turn the short way around
            double yawDiff = ((target.getYaw() - from.getYaw() + 180) % 360 + 360) % 360 - 180;
            now.setYaw((float) (from.getYaw() + yawDiff * e));
            now.setPitch((float) (from.getPitch() + (target.getPitch() - from.getPitch()) * e));

            camera.teleport(now);
        }, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Seats and text
    // ------------------------------------------------------------------

    private static final Color[] COLORS = {
            Color.RED, Color.BLUE, Color.LIME, Color.YELLOW, Color.ORANGE,
            Color.PURPLE, Color.AQUA, Color.FUCHSIA, Color.WHITE, Color.GREEN
    };

    // A figure with the player's REAL skin, using the Mannequin entity (Minecraft 1.21.9+).
    // The Mannequin API does not exist on older versions, so it is used through reflection.
    // Returns null if it is not available, then spawnBody() is used instead.
    private Entity spawnSkinBody(World world, Location seatLocation, Player player) {
        if (!cfg().getBoolean("stage.use-player-skin", true)) {
            return null;
        }

        Entity entity = null;
        try {
            EntityType type = EntityType.valueOf("MANNEQUIN");

            Location location = seatLocation.clone();
            location.setDirection(center.toVector().subtract(seatLocation.toVector()).setY(0));

            entity = world.spawnEntity(location, type);

            Class<?> mannequin = Class.forName("org.bukkit.entity.Mannequin");
            mannequin.getMethod("setPlayerProfile", PlayerProfile.class).invoke(entity, player.getPlayerProfile());
            mannequin.getMethod("setImmovable", boolean.class).invoke(entity, true);
            mannequin.getMethod("setHideDescription", boolean.class).invoke(entity, true);

            entity.setGravity(false);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            return entity;
        } catch (Exception | LinkageError e) {
            // Old server version (no MANNEQUIN) or the API changed
            if (entity != null) {
                entity.remove();
            }
            return null;
        }
    }

    // Fallback: a small sitting figure: the player's head + colored clothes
    private ArmorStand spawnBody(World world, Location seatLocation, Player player, int index) {
        Location location = seatLocation.clone();
        location.setY(location.getY() - 0.65); // sit on the floor
        location.setDirection(center.toVector().subtract(seatLocation.toVector()).setY(0));

        Color color = COLORS[index % COLORS.length];

        return world.spawn(location, ArmorStand.class, stand -> {
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setLeftLegPose(new EulerAngle(Math.toRadians(-90), 0, 0));
            stand.setRightLegPose(new EulerAngle(Math.toRadians(-90), 0, 0));

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skull = (SkullMeta) head.getItemMeta();
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);

            stand.getEquipment().setHelmet(head);
            stand.getEquipment().setChestplate(leather(Material.LEATHER_CHESTPLATE, color));
            stand.getEquipment().setLeggings(leather(Material.LEATHER_LEGGINGS, color));
            stand.getEquipment().setBoots(leather(Material.LEATHER_BOOTS, color));
        });
    }

    private ItemStack leather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    // The floating text above a seat. It always turns to face the viewers.
    private TextDisplay spawnText(World world, Location seatLocation, boolean skin) {
        double height = cfg().getDouble("stage.text-height", 1.9);
        if (skin) {
            height += cfg().getDouble("stage.standing-offset", 0.5); // a standing figure is taller
        }
        Location location = seatLocation.clone().add(0, height, 0);
        float scale = (float) cfg().getDouble("stage.text-scale", 1.5);

        return world.spawn(location, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    // Letter on top (current player only), then the words typed this turn in white, then the name
    private void updateText(Seat seat, boolean showLetter) {
        FileConfiguration config = cfg();
        List<String> lines = new ArrayList<>();

        if (showLetter) {
            lines.add(config.getString("stage.letter-format", "&6&l%letter%").replace("%letter%", letterText));
        }
        for (String word : seat.attempts) {
            lines.add(config.getString("stage.attempt-format", "&f%word%").replace("%word%", word));
        }
        String nameFormat = seat.eliminated
                ? config.getString("stage.eliminated-format", "&c&m%player%")
                : config.getString("stage.name-format", "&7%player%");
        lines.add(nameFormat.replace("%player%", seat.name));

        String text = ChatColor.translateAlternateColorCodes('&', String.join("\n", lines));
        seat.text.setText(text);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }
}