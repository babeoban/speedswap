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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.Collection;
import java.util.Random;

public class SpeedSwap extends JavaPlugin implements CommandExecutor, Listener {

    private boolean gameRunning = false;
    private Player player1;
    private Player player2;
    private BukkitRunnable swapTask;
    private final int timerSeconds = 60;
    
    private Scoreboard board;
    private Objective objective;

    @Override
    public void onEnable() {
        getCommand("speedswap").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        setupScoreboard();
        getLogger().info("SpeedSwap 1.16.1 enabled with Seed and Respawn swap support!");
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
                sender.sendMessage(ChatColor.RED + "Usage: /speedswap start <p1> <p2> [seed1] [seed2]");
                return true;
            }

            player1 = Bukkit.getPlayer(args[1]);
            player2 = Bukkit.getPlayer(args[2]);

            if (player1 == null || player2 == null) {
                sender.sendMessage(ChatColor.RED + "One or both players are offline!");
                return true;
            }

            Long seed1 = null;
            Long seed2 = null;

            if (args.length >= 5) {
                try {
                    seed1 = Long.parseLong(args[3]);
                    seed2 = Long.parseLong(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Seeds must be valid numbers!");
                    return true;
                }
            }

            startGame(seed1, seed2);
            sender.sendMessage(ChatColor.GREEN + "SpeedSwap started between " + player1.getName() + " and " + player2.getName());
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            stopGame();
            sender.sendMessage(ChatColor.YELLOW + "SpeedSwap stopped.");
            return true;
        }

        return false;
    }

    private void startGame(Long seed1, Long seed2) {
        gameRunning = true;

        player1.sendMessage(ChatColor.YELLOW + "Generating worlds and starting timer...");
        player2.sendMessage(ChatColor.YELLOW + "Generating worlds and starting timer...");

        World p1World = createWorldSet(player1.getName(), seed1);
        World p2World = createWorldSet(player2.getName(), seed2);

        player1.teleport(p1World.getSpawnLocation());
        player2.teleport(p2World.getSpawnLocation());

        player1.setScoreboard(board);
        player2.setScoreboard(board);

        startSwapTimer();
    }

    private World createWorldSet(String playerName, Long seed) {
        String baseName = "SS_" + playerName;
        long finalSeed = (seed != null) ? seed : new Random().nextLong();

        World overworld = Bukkit.createWorld(new WorldCreator(baseName).seed(finalSeed));
        Bukkit.createWorld(new WorldCreator(baseName + "_nether").environment(World.Environment.NETHER).seed(finalSeed));
        Bukkit.createWorld(new WorldCreator(baseName + "_the_end").environment(World.Environment.THE_END).seed(finalSeed));
        
        return overworld;
    }

    private void startSwapTimer() {
        swapTask = new BukkitRunnable() {
            int count = timerSeconds;

            @Override
            public void run() {
                if (!player1.isOnline() || !player2.isOnline()) {
                    Bukkit.broadcastMessage(ChatColor.RED + "A player disconnected. Stopping SpeedSwap.");
                    stopGame();
                    return;
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
        // State Capture for Player 1
        PlayerData state1 = new PlayerData(player1);
        // State Capture for Player 2
        PlayerData state2 = new PlayerData(player2);

        // Cross-Apply
        state2.apply(player1);
        state1.apply(player2);
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
        Location respawnLoc; // New field for respawn point

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
            this.respawnLoc = p.getBedSpawnLocation(); // Capture bed/anchor location
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
            
            // Apply the new respawn location
            p.setBedSpawnLocation(respawnLoc, true);

            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
            p.addPotionEffects(effects);
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (!gameRunning) return;
        Player p = event.getPlayer();
        if (p != player1 && p != player2) return;

        World current = event.getFrom().getWorld();
        if (current == null) return;

        String worldName = current.getName();
        if (!worldName.startsWith("SS_")) return;

        String baseName = worldName.split("_")[0] + "_" + worldName.split("_")[1];
        
        World target;
        if (event.getCause() == PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
            if (worldName.endsWith("_nether")) {
                target = Bukkit.getWorld(baseName);
            } else {
                target = Bukkit.getWorld(baseName + "_nether");
            }
        } else {
            if (worldName.endsWith("_the_end")) {
                target = Bukkit.getWorld(baseName);
            } else {
                target = Bukkit.getWorld(baseName + "_the_end");
            }
        }

        if (target != null) {
            event.setTo(target.getSpawnLocation());
        }
    }

    private void stopGame() {
        gameRunning = false;
        if (swapTask != null) {
            swapTask.cancel();
            swapTask = null;
        }
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (player1 != null && player1.isOnline()) player1.setScoreboard(mainBoard);
        if (player2 != null && player2.isOnline()) player2.setScoreboard(mainBoard);
    }
}