package com.dynregionperf.integration.folia;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Folia integration layer for region-threaded operations
 * Provides Folia-specific scheduling and region management
 */
public class FoliaIntegration {
    private static boolean foliaAvailable = false;
    
    static {
        try {
            // Check if Folia is available
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaAvailable = true;
        } catch (ClassNotFoundException e) {
            // Folia not available
        }
    }
    
    /**
     * Check if Folia is available
     */
    public static boolean isFoliaAvailable() {
        return foliaAvailable;
    }
    
    /**
     * Schedule a task to run on the region thread for a specific location
     */
    public static BukkitTask scheduleRegionTask(org.bukkit.plugin.Plugin plugin, Location location, Runnable task) {
        if (!foliaAvailable) {
            // Fallback to main thread
            return Bukkit.getScheduler().runTask(plugin, task);
        }
        
        try {
            // Use Folia's region scheduler
            Bukkit.getRegionScheduler().run(plugin, location, (scheduledTask) -> task.run());
            return new BukkitRunnable() {
                @Override
                public void run() {
                    // Placeholder task
                }
            }.runTask(plugin);
        } catch (Exception e) {
            // Fallback to main thread if region scheduling fails
            return Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Schedule a delayed task to run on the region thread for a specific location
     */
    public static BukkitTask scheduleRegionTaskDelayed(org.bukkit.plugin.Plugin plugin, Location location, 
                                                      Runnable task, long delayTicks) {
        if (!foliaAvailable) {
            // Fallback to main thread
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
        
        try {
            // Use Folia's region scheduler
            Bukkit.getRegionScheduler().runDelayed(plugin, location, (scheduledTask) -> task.run(), delayTicks);
            return new BukkitRunnable() {
                @Override
                public void run() {
                    // Placeholder task
                }
            }.runTaskLater(plugin, delayTicks);
        } catch (Exception e) {
            // Fallback to main thread if region scheduling fails
            return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * Schedule a repeating task to run on the region thread for a specific location
     */
    public static BukkitTask scheduleRegionTaskRepeating(org.bukkit.plugin.Plugin plugin, Location location, 
                                                        Runnable task, long delayTicks, long periodTicks) {
        if (!foliaAvailable) {
            // Fallback to main thread
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
        
        try {
            // Use Folia's region scheduler
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, (scheduledTask) -> task.run(), 
                delayTicks, periodTicks);
            return new BukkitRunnable() {
                @Override
                public void run() {
                    // Placeholder task
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        } catch (Exception e) {
            // Fallback to main thread if region scheduling fails
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }
    
    /**
     * Execute a task asynchronously and return a CompletableFuture
     */
    public static <T> CompletableFuture<T> executeAsync(org.bukkit.plugin.Plugin plugin, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        if (!foliaAvailable) {
            // Fallback to async scheduler
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    T result = task.get();
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                // Use Folia's global scheduler for async tasks
                Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> {
                    try {
                        T result = task.get();
                        future.complete(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                // Fallback to async scheduler
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        T result = task.get();
                        future.complete(result);
                    } catch (Exception e2) {
                        future.completeExceptionally(e2);
                    }
                });
            }
        }
        
        return future;
    }
    
    /**
     * Execute a task on the main thread and return a CompletableFuture
     */
    public static <T> CompletableFuture<T> executeSync(org.bukkit.plugin.Plugin plugin, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * Get the region for a specific location
     */
    public static String getRegionForLocation(Location location) {
        if (!foliaAvailable) {
            return "main";
        }
        
        try {
            // This would require Folia-specific implementation
            // For now, return a region identifier based on chunk coordinates
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            return location.getWorld().getName() + "_" + (chunkX >> 5) + "_" + (chunkZ >> 5);
        } catch (Exception e) {
            return "main";
        }
    }
    
    /**
     * Check if a location is in a specific region
     */
    public static boolean isLocationInRegion(Location location, String regionId) {
        if (!foliaAvailable) {
            return true; // All locations are in main region
        }
        
        String locationRegion = getRegionForLocation(location);
        return locationRegion.equals(regionId);
    }
    
    /**
     * Get all regions for a world
     */
    public static java.util.Set<String> getRegionsForWorld(World world) {
        if (!foliaAvailable) {
            return java.util.Set.of("main");
        }
        
        try {
            // This would require Folia-specific implementation
            // For now, return a set with a single region
            return java.util.Set.of(world.getName() + "_main");
        } catch (Exception e) {
            return java.util.Set.of("main");
        }
    }
    
    /**
     * Schedule a task to run on all regions for a world
     */
    public static void scheduleWorldTask(org.bukkit.plugin.Plugin plugin, World world, Runnable task) {
        if (!foliaAvailable) {
            // Fallback to main thread
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        
        try {
            // Use Folia's global scheduler
            Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> task.run());
        } catch (Exception e) {
            // Fallback to main thread
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Get Folia configuration recommendations
     */
    public static String getConfigurationRecommendations() {
        if (!foliaAvailable) {
            return "Folia is not available. Region-threading features are disabled.";
        }
        
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("Folia detected! Recommended configuration:\n");
        recommendations.append("- region-threading: enabled\n");
        recommendations.append("- async-chunk-loading: enabled\n");
        recommendations.append("- async-chunk-saving: enabled\n");
        recommendations.append("- region-size: 32x32 chunks\n");
        recommendations.append("- max-region-load: 1000\n");
        recommendations.append("- max-region-save: 1000\n");
        
        return recommendations.toString();
    }
    
    /**
     * Check if a task is running on a region thread
     */
    public static boolean isRunningOnRegionThread() {
        if (!foliaAvailable) {
            return false;
        }
        
        try {
            // This would require Folia-specific implementation
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the current region thread name
     */
    public static String getCurrentRegionThreadName() {
        if (!foliaAvailable) {
            return "main";
        }
        
        try {
            // This would require Folia-specific implementation
            return Thread.currentThread().getName();
        } catch (Exception e) {
            return "main";
        }
    }
}
