package com.dynregionperf.commands;

import com.dynregionperf.DynRegionPerfPlugin;
import com.dynregionperf.budget.ViewDistanceAdapter;
import com.dynregionperf.control.AdaptiveControlSystem;
import com.dynregionperf.control.EntityController;
import com.dynregionperf.metrics.MetricsCollector;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command system for DynRegionPerf plugin
 * Handles /perf status, boost, and profile commands
 */
public class PerfCommand implements CommandExecutor, TabCompleter {
    private final DynRegionPerfPlugin plugin;
    private final MetricsCollector metricsCollector;
    private final AdaptiveControlSystem controlSystem;
    private final ViewDistanceAdapter viewDistanceAdapter;
    private final EntityController entityController;
    
    // Boost tracking
    private final Map<UUID, BoostData> activeBoosts = new ConcurrentHashMap<>();
    
    public static class BoostData {
        public final long startTime;
        public final int radius;
        public final int originalViewDistance;
        
        public BoostData(long startTime, int radius, int originalViewDistance) {
            this.startTime = startTime;
            this.radius = radius;
            this.originalViewDistance = originalViewDistance;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > 300000; // 5 minutes
        }
    }
    
    public PerfCommand(DynRegionPerfPlugin plugin, MetricsCollector metricsCollector,
                      AdaptiveControlSystem controlSystem, ViewDistanceAdapter viewDistanceAdapter,
                      EntityController entityController) {
        this.plugin = plugin;
        this.metricsCollector = metricsCollector;
        this.controlSystem = controlSystem;
        this.viewDistanceAdapter = viewDistanceAdapter;
        this.entityController = entityController;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                return handleStatusCommand(sender, args);
            case "boost":
                return handleBoostCommand(sender, args);
            case "profile":
                return handleProfileCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    /**
     * Handle /perf status command
     */
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynperf.view")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        // Get current performance data
        double currentTps = metricsCollector.getCurrentTps();
        double currentMspt = metricsCollector.getCurrentMspt();
        int loadedChunks = metricsCollector.getLoadedChunkCount();
        int hotRegions = plugin.getHotRegions().size();
        
        // Get control system output
        AdaptiveControlSystem.ControlOutput controlOutput = controlSystem.getCurrentOutput();
        
        // Get view distance stats
        ViewDistanceAdapter.ViewDistanceStats viewStats = viewDistanceAdapter.getViewDistanceStats();
        
        // Send status message
        sender.sendMessage(ChatColor.GOLD + "=== DynRegionPerf Status ===");
        sender.sendMessage(ChatColor.YELLOW + "TPS: " + ChatColor.WHITE + String.format("%.2f", currentTps));
        sender.sendMessage(ChatColor.YELLOW + "MSPT: " + ChatColor.WHITE + String.format("%.2f", currentMspt));
        sender.sendMessage(ChatColor.YELLOW + "Loaded Chunks: " + ChatColor.WHITE + loadedChunks);
        sender.sendMessage(ChatColor.YELLOW + "Hot Regions: " + ChatColor.WHITE + hotRegions);
        
        sender.sendMessage(ChatColor.GOLD + "=== Control Adjustments ===");
        sender.sendMessage(ChatColor.YELLOW + "View Distance: " + ChatColor.WHITE + 
            (controlOutput.viewDistanceAdjustment >= 0 ? "+" : "") + controlOutput.viewDistanceAdjustment);
        sender.sendMessage(ChatColor.YELLOW + "Chunk Budget: " + ChatColor.WHITE + 
            (controlOutput.chunkBudgetAdjustment >= 0 ? "+" : "") + controlOutput.chunkBudgetAdjustment);
        sender.sendMessage(ChatColor.YELLOW + "Random Tick Ratio: " + ChatColor.WHITE + 
            (controlOutput.randomTickRatioAdjustment >= 0 ? "+" : "") + String.format("%.2f", controlOutput.randomTickRatioAdjustment));
        sender.sendMessage(ChatColor.YELLOW + "Aggressive Unload: " + ChatColor.WHITE + controlOutput.aggressiveUnload);
        
        sender.sendMessage(ChatColor.GOLD + "=== View Distance Stats ===");
        sender.sendMessage(ChatColor.YELLOW + "Hot Region Players: " + ChatColor.WHITE + viewStats.hotRegionPlayers);
        sender.sendMessage(ChatColor.YELLOW + "Normal Region Players: " + ChatColor.WHITE + viewStats.normalRegionPlayers);
        sender.sendMessage(ChatColor.YELLOW + "Cold Region Players: " + ChatColor.WHITE + viewStats.coldRegionPlayers);
        sender.sendMessage(ChatColor.YELLOW + "Average View Distance: " + ChatColor.WHITE + String.format("%.1f", viewStats.averageViewDistance));
        sender.sendMessage(ChatColor.YELLOW + "Performance Multiplier: " + ChatColor.WHITE + String.format("%.2f", viewStats.performanceMultiplier));
        
        return true;
    }
    
    /**
     * Handle /perf boost command
     */
    private boolean handleBoostCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynperf.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /perf boost <radius>");
            return true;
        }
        
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
            if (radius < 1 || radius > 10) {
                sender.sendMessage(ChatColor.RED + "Radius must be between 1 and 10.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid radius. Must be a number.");
            return true;
        }
        
        // Check if player already has an active boost
        UUID playerId = player.getUniqueId();
        BoostData existingBoost = activeBoosts.get(playerId);
        
        if (existingBoost != null && !existingBoost.isExpired()) {
            sender.sendMessage(ChatColor.YELLOW + "You already have an active boost. It will be replaced.");
        }
        
        // Get current view distance
        int currentViewDistance = viewDistanceAdapter.getPlayerViewDistance(playerId);
        
        // Apply boost (increase view distance)
        int boostedViewDistance = Math.min(32, currentViewDistance + radius * 2);
        viewDistanceAdapter.setPlayerViewDistance(player, boostedViewDistance);
        
        // Store boost data
        activeBoosts.put(playerId, new BoostData(System.currentTimeMillis(), radius, currentViewDistance));
        
        sender.sendMessage(ChatColor.GREEN + "Performance boost applied! View distance increased by " + 
            (boostedViewDistance - currentViewDistance) + " chunks for 5 minutes.");
        
        return true;
    }
    
    /**
     * Handle /perf profile command
     */
    private boolean handleProfileCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynperf.view")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        // Get performance statistics
        AdaptiveControlSystem.PerformanceStats perfStats = controlSystem.getPerformanceStats();
        
        sender.sendMessage(ChatColor.GOLD + "=== Performance Profile (Last 60s) ===");
        sender.sendMessage(ChatColor.YELLOW + "Average TPS: " + ChatColor.WHITE + String.format("%.2f", perfStats.avgTps));
        sender.sendMessage(ChatColor.YELLOW + "Average MSPT: " + ChatColor.WHITE + String.format("%.2f", perfStats.avgMspt));
        sender.sendMessage(ChatColor.YELLOW + "TPS Range: " + ChatColor.WHITE + 
            String.format("%.2f - %.2f", perfStats.minTps, perfStats.maxTps));
        sender.sendMessage(ChatColor.YELLOW + "MSPT Range: " + ChatColor.WHITE + 
            String.format("%.2f - %.2f", perfStats.minMspt, perfStats.maxMspt));
        
        // Get entity statistics
        if (sender instanceof Player) {
            Player player = (Player) sender;
            EntityController.EntityStats entityStats = entityController.getEntityStats(
                player.getWorld(), plugin.getHotRegions());
            
            sender.sendMessage(ChatColor.GOLD + "=== Entity Statistics ===");
            sender.sendMessage(ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + 
                entityStats.totalMobs + " (" + entityStats.hotMobs + " hot, " + entityStats.getColdMobs() + " cold)");
            sender.sendMessage(ChatColor.YELLOW + "Animals: " + ChatColor.WHITE + 
                entityStats.totalAnimals + " (" + entityStats.hotAnimals + " hot, " + entityStats.getColdAnimals() + " cold)");
            sender.sendMessage(ChatColor.YELLOW + "Projectiles: " + ChatColor.WHITE + 
                entityStats.totalProjectiles + " (" + entityStats.hotProjectiles + " hot, " + entityStats.getColdProjectiles() + " cold)");
        }
        
        return true;
    }
    
    /**
     * Handle /perf reload command
     */
    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynperf.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        try {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "DynRegionPerf configuration reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== DynRegionPerf Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/perf status" + ChatColor.WHITE + " - Show current performance status");
        sender.sendMessage(ChatColor.YELLOW + "/perf boost <radius>" + ChatColor.WHITE + " - Boost performance in your area");
        sender.sendMessage(ChatColor.YELLOW + "/perf profile" + ChatColor.WHITE + " - Show detailed performance profile");
        
        if (sender.hasPermission("dynperf.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/perf reload" + ChatColor.WHITE + " - Reload configuration");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("status", "boost", "profile");
            if (sender.hasPermission("dynperf.admin")) {
                subCommands = Arrays.asList("status", "boost", "profile", "reload");
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boost")) {
            // Tab complete radius values
            for (int i = 1; i <= 10; i++) {
                completions.add(String.valueOf(i));
            }
        }
        
        return completions;
    }
    
    /**
     * Clean up expired boosts
     */
    public void cleanupExpiredBoosts() {
        activeBoosts.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Get active boost for a player
     */
    public BoostData getActiveBoost(UUID playerId) {
        BoostData boost = activeBoosts.get(playerId);
        if (boost != null && boost.isExpired()) {
            activeBoosts.remove(playerId);
            return null;
        }
        return boost;
    }
    
    /**
     * Remove boost for a player
     */
    public void removeBoost(UUID playerId) {
        BoostData boost = activeBoosts.remove(playerId);
        if (boost != null) {
            // Restore original view distance
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                viewDistanceAdapter.setPlayerViewDistance(player, boost.originalViewDistance);
            }
        }
    }
}
