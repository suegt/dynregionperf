package com.dynregionperf.budget;

import com.dynregionperf.cluster.DensityClusterer;
import com.dynregionperf.cluster.HotRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic view distance adjustment system
 * Adjusts player view distance based on region temperature and performance
 */
public class ViewDistanceAdapter {
    private final DensityClusterer clusterer;
    private final Map<UUID, Integer> playerViewDistances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastViewDistanceUpdate = new ConcurrentHashMap<>();
    
    // View distance ranges
    private final int hotMin, hotMax;
    private final int normalMin, normalMax;
    private final int coldMin, coldMax;
    
    // Performance-based adjustments
    private double performanceMultiplier = 1.0;
    private long lastPerformanceUpdate = 0;
    
    public ViewDistanceAdapter(DensityClusterer clusterer, 
                              int hotMin, int hotMax,
                              int normalMin, int normalMax,
                              int coldMin, int coldMax) {
        this.clusterer = clusterer;
        this.hotMin = hotMin;
        this.hotMax = hotMax;
        this.normalMin = normalMin;
        this.normalMax = normalMax;
        this.coldMin = coldMin;
        this.coldMax = coldMax;
    }
    
    /**
     * Update view distances for all players based on current hot regions
     */
    public void updateViewDistances(List<Player> players, List<HotRegion> hotRegions) {
        long currentTime = System.currentTimeMillis();
        
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            
            Location playerLoc = player.getLocation();
            UUID playerId = player.getUniqueId();
            
            // Determine region temperature
            RegionTemperature temperature = getRegionTemperature(playerLoc, hotRegions);
            
            // Calculate target view distance
            int targetViewDistance = calculateTargetViewDistance(temperature);
            
            // Apply performance multiplier
            targetViewDistance = (int) (targetViewDistance * performanceMultiplier);
            
            // Clamp to valid range
            targetViewDistance = Math.max(2, Math.min(32, targetViewDistance));
            
            // Update if different from current
            Integer currentViewDistance = playerViewDistances.get(playerId);
            if (currentViewDistance == null || !currentViewDistance.equals(targetViewDistance)) {
                updatePlayerViewDistance(player, targetViewDistance);
                playerViewDistances.put(playerId, targetViewDistance);
                lastViewDistanceUpdate.put(playerId, currentTime);
            }
        }
        
        // Clean up offline players
        playerViewDistances.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            return players.stream().noneMatch(p -> p.getUniqueId().equals(playerId));
        });
        
        lastViewDistanceUpdate.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            return players.stream().noneMatch(p -> p.getUniqueId().equals(playerId));
        });
    }
    
    /**
     * Determine the temperature of the region containing a location
     */
    private RegionTemperature getRegionTemperature(Location location, List<HotRegion> hotRegions) {
        if (hotRegions.isEmpty()) {
            return RegionTemperature.COLD;
        }
        
        // Check if location is in any hot region
        for (HotRegion region : hotRegions) {
            if (clusterer.isLocationInHotRegion(location, List.of(region))) {
                return RegionTemperature.HOT;
            }
        }
        
        // Check distance to nearest hot region
        double minDistance = Double.MAX_VALUE;
        for (HotRegion region : hotRegions) {
            if (!region.world.equals(location.getWorld().getName())) continue;
            
            // Calculate distance to region center
            double regionCenterX = (region.minX + region.maxX) / 2.0 * clusterer.getGridSize();
            double regionCenterZ = (region.minZ + region.maxZ) / 2.0 * clusterer.getGridSize();
            
            double distance = Math.sqrt(
                Math.pow(location.getX() - regionCenterX, 2) + 
                Math.pow(location.getZ() - regionCenterZ, 2)
            );
            
            minDistance = Math.min(minDistance, distance);
        }
        
        // If close to hot region, consider it normal temperature
        if (minDistance < clusterer.getGridSize() * 2) {
            return RegionTemperature.NORMAL;
        }
        
        return RegionTemperature.COLD;
    }
    
    /**
     * Calculate target view distance based on region temperature
     */
    private int calculateTargetViewDistance(RegionTemperature temperature) {
        switch (temperature) {
            case HOT:
                return hotMin + (int) (Math.random() * (hotMax - hotMin + 1));
            case NORMAL:
                return normalMin + (int) (Math.random() * (normalMax - normalMin + 1));
            case COLD:
                return coldMin + (int) (Math.random() * (coldMax - coldMin + 1));
            default:
                return normalMin;
        }
    }
    
    /**
     * Update a player's view distance
     */
    private void updatePlayerViewDistance(Player player, int viewDistance) {
        try {
            // Use Paper API to set view distance
            player.setViewDistance(viewDistance);
        } catch (Exception e) {
            // Fallback: try to set simulation distance if available
            try {
                player.setSimulationDistance(Math.min(viewDistance, 10));
            } catch (Exception ex) {
                // If both fail, we can't adjust view distance
                // This might happen on older Paper versions or other server software
            }
        }
    }
    
    /**
     * Update performance multiplier based on server performance
     */
    public void updatePerformanceMultiplier(double currentMspt, double targetMspt, double currentTps, double minTps) {
        long currentTime = System.currentTimeMillis();
        
        // Only update every 5 seconds to avoid rapid changes
        if (currentTime - lastPerformanceUpdate < 5000) {
            return;
        }
        
        lastPerformanceUpdate = currentTime;
        
        // Calculate performance ratio
        double msptRatio = currentMspt / targetMspt;
        double tpsRatio = currentTps / minTps;
        
        // Use the worse of the two ratios
        double performanceRatio = Math.max(msptRatio, 1.0 / tpsRatio);
        
        // Adjust multiplier based on performance
        if (performanceRatio > 1.2) {
            // Performance is poor, reduce view distances
            performanceMultiplier = Math.max(0.7, performanceMultiplier - 0.1);
        } else if (performanceRatio < 0.8) {
            // Performance is good, can increase view distances
            performanceMultiplier = Math.min(1.0, performanceMultiplier + 0.1);
        }
        
        // Gradual adjustment towards 1.0 if performance is stable
        if (performanceRatio >= 0.9 && performanceRatio <= 1.1) {
            if (performanceMultiplier < 1.0) {
                performanceMultiplier = Math.min(1.0, performanceMultiplier + 0.05);
            } else if (performanceMultiplier > 1.0) {
                performanceMultiplier = Math.max(1.0, performanceMultiplier - 0.05);
            }
        }
    }
    
    /**
     * Get current view distance for a player
     */
    public int getPlayerViewDistance(UUID playerId) {
        return playerViewDistances.getOrDefault(playerId, 8);
    }
    
    /**
     * Get current performance multiplier
     */
    public double getPerformanceMultiplier() {
        return performanceMultiplier;
    }
    
    /**
     * Reset view distances to default for all players
     */
    public void resetAllViewDistances(List<Player> players) {
        for (Player player : players) {
            updatePlayerViewDistance(player, 8); // Default view distance
            playerViewDistances.put(player.getUniqueId(), 8);
        }
    }
    
    /**
     * Force set view distance for a specific player (for boost command)
     */
    public void setPlayerViewDistance(Player player, int viewDistance) {
        viewDistance = Math.max(2, Math.min(32, viewDistance));
        updatePlayerViewDistance(player, viewDistance);
        playerViewDistances.put(player.getUniqueId(), viewDistance);
    }
    
    /**
     * Get statistics about current view distance distribution
     */
    public ViewDistanceStats getViewDistanceStats() {
        int hotCount = 0, normalCount = 0, coldCount = 0;
        int totalViewDistance = 0;
        
        for (Integer viewDistance : playerViewDistances.values()) {
            totalViewDistance += viewDistance;
            
            if (viewDistance <= hotMax) {
                hotCount++;
            } else if (viewDistance <= normalMax) {
                normalCount++;
            } else {
                coldCount++;
            }
        }
        
        int playerCount = playerViewDistances.size();
        double averageViewDistance = playerCount > 0 ? (double) totalViewDistance / playerCount : 0.0;
        
        return new ViewDistanceStats(hotCount, normalCount, coldCount, averageViewDistance, performanceMultiplier);
    }
    
    /**
     * Region temperature enum
     */
    public enum RegionTemperature {
        HOT, NORMAL, COLD
    }
    
    /**
     * View distance statistics
     */
    public static class ViewDistanceStats {
        public final int hotRegionPlayers;
        public final int normalRegionPlayers;
        public final int coldRegionPlayers;
        public final double averageViewDistance;
        public final double performanceMultiplier;
        
        public ViewDistanceStats(int hotRegionPlayers, int normalRegionPlayers, int coldRegionPlayers,
                               double averageViewDistance, double performanceMultiplier) {
            this.hotRegionPlayers = hotRegionPlayers;
            this.normalRegionPlayers = normalRegionPlayers;
            this.coldRegionPlayers = coldRegionPlayers;
            this.averageViewDistance = averageViewDistance;
            this.performanceMultiplier = performanceMultiplier;
        }
        
        @Override
        public String toString() {
            return String.format("ViewDistanceStats{hot=%d, normal=%d, cold=%d, avg=%.1f, multiplier=%.2f}",
                hotRegionPlayers, normalRegionPlayers, coldRegionPlayers, averageViewDistance, performanceMultiplier);
        }
    }
}
