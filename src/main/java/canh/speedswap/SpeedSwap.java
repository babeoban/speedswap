package canh.speedswap;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class SpeedSwap extends JavaPlugin implements CommandExecutor, Listener {

    private boolean gameRunning = false;
    private final List<Player> players = new ArrayList<>();
    private BukkitRunnable swapTask;
    private final int timerSeconds = 60;
    
    private Scoreboard board;
    private Objective objective;

    @Override
    public void onEnable() {
        getCommand("speedswap").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        setupScoreboard();
        getLogger().info("SpeedSwap 1.16.1 enabled with Improved Portal Linking!");
    }

    @Override
    public void onDisable() {
        stopGame();
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        board = manager.getNewScoreboard();
        objective = board.registerNewObjective("speedswap", "dummy", ChatColor.GOLD + "" + ChatColor.BOLD + "SpeedSwap");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("speedswap.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }

        if (args.length == 0) return false;

        if (args[0].equalsIgnoreCase("start")) {
            if (gameRunning) {
                sender.sendMessage(ChatColor.RED + "A game is already running!");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /speedswap start <p1> <p2> [p3...] [seeds...]");
                return true;
            }

            List<Player> tempPlayers = new ArrayList<>();
            List<Long> seeds = new ArrayList<>();

            for (int i = 1; i < args.length; i++) {
                Player p = Bukkit.getPlayer(args[i]);
                if (p != null) {
                    tempPlayers.add(p);
                } else {
                    try {
                        seeds.add(Long.parseLong(args[i]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Unknown player or invalid seed: " + args[i]);
                        return true;
                    }
                }
            }

            if (tempPlayers.size() < 2) {
                sender.sendMessage(ChatColor.RED + "You need at least 2 players to start!");
                return true;
            }

            players.clear();
            players.addAll(tempPlayers);

            startGame(seeds);
            sender.sendMessage(ChatColor.GREEN + "SpeedSwap started with " + players.size() + " players!");
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            stopGame();
            sender.sendMessage(ChatColor.YELLOW + "SpeedSwap stopped.");
            return true;
        }

        return false;
    }

    private void startGame(List<Long> seeds) {
        gameRunning = true;
        Random random = new Random();
        List<World> createdOverworlds = new ArrayList<>();

        Bukkit.broadcastMessage(ChatColor.YELLOW + "SpeedSwap: Generating " + (players.size() * 3) + " worlds. Please wait...");

        for (int i = 0; i < players.size(); i++) {
            Long seed = (i < seeds.size()) ? seeds.get(i) : random.nextLong();
            createdOverworlds.add(createWorldSet(players.get(i).getName(), seed));
        }

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            World world = createdOverworlds.get(i);
            
            p.teleport(world.getSpawnLocation());
            p.setScoreboard(board);
            p.sendMessage(ChatColor.GREEN + "All worlds ready! Starting game...");
        }

        // 3. Start the timer logic
        startSwapTimer();
    }

    private World createWorldSet(String playerName, long seed) {
        String baseName = "SS_" + playerName;
        World overworld = Bukkit.createWorld(new WorldCreator(baseName).seed(seed));
        Bukkit.createWorld(new WorldCreator(baseName + "_nether").environment(World.Environment.NETHER).seed(seed));
        Bukkit.createWorld(new WorldCreator(baseName + "_the_end").environment(World.Environment.THE_END).seed(seed));
        return overworld;
    }

    private void startSwapTimer() {
        swapTask = new BukkitRunnable() {
            int count = timerSeconds;

            @Override
            public void run() {
                for (Player p : players) {
                    if (!p.isOnline()) {
                        Bukkit.broadcastMessage(ChatColor.RED + p.getName() + " disconnected. Stopping SpeedSwap.");
                        stopGame();
                        return;
                    }
                }

                updateScoreboard(count);

                if (count <= 0) {
                    performSwap();
                    count = timerSeconds;
                }
                count--;
            }
        };
        swapTask.runTaskTimer(this, 0L, 20L);
    }

    private void updateScoreboard(int seconds) {
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        Score timeTitle = objective.getScore(ChatColor.WHITE + "Next Swap in:");
        timeTitle.setScore(2);

        String timeStr = ChatColor.YELLOW + String.format("%02d:%02d", seconds / 60, seconds % 60);
        Score timeValue = objective.getScore(timeStr);
        timeValue.setScore(1);
    }

    private void performSwap() {
        int size = players.size();
        PlayerData[] states = new PlayerData[size];

        for (int i = 0; i < size; i++) {
            states[i] = new PlayerData(players.get(i));
        }

        for (int i = 0; i < size; i++) {
            int targetStateIndex = (i + 1) % size;
            states[targetStateIndex].apply(players.get(i));
            players.get(i).sendMessage(ChatColor.AQUA + "Swapped with " + players.get(targetStateIndex).getName() + "!");
        }
    }

    private static class PlayerData {
        Location loc;
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack[] extra;
        double health;
        int food;
        float saturation;
        int level;
        float exp;
        Collection<PotionEffect> effects;
        int fire;
        Location respawnLoc;

        PlayerData(Player p) {
            this.loc = p.getLocation();
            this.contents = p.getInventory().getStorageContents();
            this.armor = p.getInventory().getArmorContents();
            this.extra = p.getInventory().getExtraContents();
            this.health = p.getHealth();
            this.food = p.getFoodLevel();
            this.saturation = p.getSaturation();
            this.level = p.getLevel();
            this.exp = p.getExp();
            this.effects = p.getActivePotionEffects();
            this.fire = p.getFireTicks();
            this.respawnLoc = p.getBedSpawnLocation();
        }

        void apply(Player p) {
            p.teleport(loc);
            p.getInventory().setStorageContents(contents);
            p.getInventory().setArmorContents(armor);
            p.getInventory().setExtraContents(extra);
            
            double maxHealth = 20.0;
            if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            }
            p.setHealth(Math.min(health, maxHealth));
            
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.setLevel(level);
            p.setExp(exp);
            p.setFireTicks(fire);
            p.setBedSpawnLocation(respawnLoc, true);

            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
            p.addPotionEffects(effects);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!gameRunning) return;
        Player p = event.getPlayer();
        if (!players.contains(p)) return;

        if (event.isBedSpawn()) return;

        World diedIn = p.getWorld();
        String worldName = diedIn.getName();
        if (!worldName.startsWith("SS_")) return;

        String[] parts = worldName.split("_");
        if (parts.length < 2) return;
        String baseName = parts[0] + "_" + parts[1];

        World overworld = Bukkit.getWorld(baseName);
        if (overworld != null) {
            event.setRespawnLocation(overworld.getSpawnLocation());
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (!gameRunning) return;
        Player p = event.getPlayer();
        if (!players.contains(p)) return;

        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;

        String worldName = fromWorld.getName();
        if (!worldName.startsWith("SS_")) return;

        String[] parts = worldName.split("_");
        if (parts.length < 2) return;
        String baseName = parts[0] + "_" + parts[1];

        Location fromLoc = event.getFrom();
        Location toLoc = null;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (worldName.endsWith("_nether")) {
                World target = Bukkit.getWorld(baseName);
                if (target != null) {
                    toLoc = new Location(target, fromLoc.getX() * 8, fromLoc.getY(), fromLoc.getZ() * 8);
                }
            } else {
                World target = Bukkit.getWorld(baseName + "_nether");
                if (target != null) {
                    toLoc = new Location(target, fromLoc.getX() / 8, fromLoc.getY(), fromLoc.getZ() / 8);
                }
            }
            if (toLoc != null) {
                event.setTo(toLoc);
                event.setCanCreatePortal(true);
                event.setSearchRadius(128);
            }
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (worldName.endsWith("_the_end")) {
                World target = Bukkit.getWorld(baseName);
                if (target != null) {
                    toLoc = target.getSpawnLocation();
                }
            } else {
                World target = Bukkit.getWorld(baseName + "_the_end");
                if (target != null) {
                    toLoc = new Location(target, 100.5, 50, 0.5, 90f, 0f);
                }
            }
            if (toLoc != null) {
                event.setTo(toLoc);
            }
        }
    }

    private void stopGame() {
        gameRunning = false;
        if (swapTask != null) {
            swapTask.cancel();
            swapTask = null;
        }
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : players) {
            if (p.isOnline()) p.setScoreboard(mainBoard);
        }
        players.clear();
    }
}