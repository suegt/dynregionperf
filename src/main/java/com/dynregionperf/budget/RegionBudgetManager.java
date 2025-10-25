package com.dynregionperf.budget;

import com.dynregionperf.cluster.DensityClusterer;
import com.dynregionperf.cluster.HotRegion;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunk budget and ticket management system
 * Manages chunk loading/unloading based on player density and movement patterns
 */
public class RegionBudgetManager {
    private final int chunkBudgetPerHotRegionPerSec;
    private final DensityClusterer clusterer;
    
    // Budget tracking
    private final Map<String, AtomicInteger> regionBudgets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBudgetUpdate = new ConcurrentHashMap<>();
    
    // Chunk tracking
    private final Map<String, Set<Chunk>> loadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Set<Chunk>> pendingLoads = new ConcurrentHashMap<>();
    private final Map<String, Set<Chunk>> pendingUnloads = new ConcurrentHashMap<>();
    
    // Player movement tracking for predictive loading
    private final Map<UUID, PlayerMovementData> playerMovements = new ConcurrentHashMap<>();
    
    public static class PlayerMovementData {
        public Location lastLocation;
        public long lastUpdate;
        public double velocityX, velocityZ;
        public double predictedX, predictedZ;
        
        public PlayerMovementData(Location location) {
            this.lastLocation = location.clone();
            this.lastUpdate = System.currentTimeMillis();
            this.velocityX = 0.0;
            this.velocityZ = 0.0;
            this.predictedX = location.getX();
            this.predictedZ = location.getZ();
        }
        
        public void update(Location newLocation) {
            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - lastUpdate;
            
            if (deltaTime > 0) {
                double deltaX = newLocation.getX() - lastLocation.getX();
                double deltaZ = newLocation.getZ() - lastLocation.getZ();
                
                // Calculate velocity (blocks per second)
                velocityX = deltaX * 1000.0 / deltaTime;
                velocityZ = deltaZ * 1000.0 / deltaTime;
                
                // Predict future position (2 seconds ahead)
                predictedX = newLocation.getX() + velocityX * 2.0;
                predictedZ = newLocation.getZ() + velocityZ * 2.0;
            }
            
            this.lastLocation = newLocation.clone();
            this.lastUpdate = currentTime;
        }
        
        public Location getPredictedLocation(World world) {
            return new Location(world, predictedX, lastLocation.getY(), predictedZ);
        }
    }
    
    public RegionBudgetManager(int chunkBudgetPerHotRegionPerSec, DensityClusterer clusterer) {
        this.chunkBudgetPerHotRegionPerSec = chunkBudgetPerHotRegionPerSec;
        this.clusterer = clusterer;
    }
    
    /**
     * Update budgets for all hot regions
     */
    public void updateBudgets(List<HotRegion> hotRegions) {
        long currentTime = System.currentTimeMillis();
        
        for (HotRegion region : hotRegions) {
            String regionKey = getRegionKey(region);
            
            // Initialize budget if not exists
            if (!regionBudgets.containsKey(regionKey)) {
                regionBudgets.put(regionKey, new AtomicInteger(0));
                lastBudgetUpdate.put(regionKey, currentTime);
                continue;
            }
            
            // Add budget based on time elapsed
            long lastUpdate = lastBudgetUpdate.get(regionKey);
            long timeElapsed = currentTime - lastUpdate;
            
            if (timeElapsed >= 1000) { // At least 1 second
                int budgetToAdd = (int) (timeElapsed / 1000.0 * chunkBudgetPerHotRegionPerSec);
                regionBudgets.get(regionKey).addAndGet(budgetToAdd);
                lastBudgetUpdate.put(regionKey, currentTime);
            }
        }
        
        // Clean up old regions
        regionBudgets.entrySet().removeIf(entry -> {
            String regionKey = entry.getKey();
            boolean stillExists = hotRegions.stream()
                .anyMatch(region -> getRegionKey(region).equals(regionKey));
            
            if (!stillExists) {
                lastBudgetUpdate.remove(regionKey);
                loadedChunks.remove(regionKey);
                pendingLoads.remove(regionKey);
                pendingUnloads.remove(regionKey);
            }
            
            return !stillExists;
        });
    }
    
    /**
     * Process chunk loading/unloading for a region (optimized for FPS)
     */
    public void processRegionChunks(HotRegion region, Collection<Player> players) {
        String regionKey = getRegionKey(region);
        AtomicInteger budget = regionBudgets.get(regionKey);
        
        if (budget == null) return;
        
        Set<Chunk> currentLoaded = loadedChunks.computeIfAbsent(regionKey, k -> ConcurrentHashMap.newKeySet());
        Set<Chunk> pendingLoad = pendingLoads.computeIfAbsent(regionKey, k -> ConcurrentHashMap.newKeySet());
        Set<Chunk> pendingUnload = pendingUnloads.computeIfAbsent(regionKey, k -> ConcurrentHashMap.newKeySet());
        
        // Get chunks that should be loaded based on player positions and predictions
        Set<Chunk> requiredChunks = getRequiredChunks(region, players);
        
        // Mark chunks for unloading that are no longer needed
        for (Chunk chunk : currentLoaded) {
            if (!requiredChunks.contains(chunk) && !pendingLoad.contains(chunk)) {
                pendingUnload.add(chunk);
            }
        }
        
        // Process pending unloads first (free up budget)
        Iterator<Chunk> unloadIterator = pendingUnload.iterator();
        while (unloadIterator.hasNext() && budget.get() < chunkBudgetPerHotRegionPerSec) {
            Chunk chunk = unloadIterator.next();
            if (unloadChunk(chunk)) {
                currentLoaded.remove(chunk);
                unloadIterator.remove();
                budget.incrementAndGet(); // Unloading gives us budget back
            }
        }
        
        // Process pending loads with stricter limits to reduce FPS impact
        Iterator<Chunk> loadIterator = pendingLoad.iterator();
        int maxLoadsPerTick = Math.max(1, chunkBudgetPerHotRegionPerSec / 20); // Max loads per tick
        int loadsThisTick = 0;
        
        while (loadIterator.hasNext() && budget.get() > 0 && loadsThisTick < maxLoadsPerTick) {
            Chunk chunk = loadIterator.next();
            if (loadChunk(chunk)) {
                currentLoaded.add(chunk);
                loadIterator.remove();
                budget.decrementAndGet();
                loadsThisTick++;
            }
        }
        
        // Add new chunks that need loading (with priority system)
        List<Chunk> priorityChunks = new ArrayList<>();
        for (Chunk chunk : requiredChunks) {
            if (!currentLoaded.contains(chunk) && !pendingLoad.contains(chunk)) {
                // Prioritize chunks closer to players
                boolean isHighPriority = isChunkHighPriority(chunk, players);
                if (isHighPriority) {
                    priorityChunks.add(chunk);
                } else {
                    pendingLoad.add(chunk);
                }
            }
        }
        
        // Add high priority chunks first
        for (Chunk chunk : priorityChunks) {
            pendingLoad.add(chunk);
        }
    }
    
    /**
     * Check if a chunk is high priority (close to players)
     */
    private boolean isChunkHighPriority(Chunk chunk, Collection<Player> players) {
        Location chunkLoc = chunk.getBlock(0, 0, 0).getLocation();
        
        for (Player player : players) {
            if (player.getWorld().equals(chunk.getWorld())) {
                double distance = player.getLocation().distance(chunkLoc);
                if (distance < 64) { // Within 4 chunks
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Get chunks that should be loaded for a region
     */
    private Set<Chunk> getRequiredChunks(HotRegion region, Collection<Player> players) {
        Set<Chunk> requiredChunks = new HashSet<>();
        World world = null;
        
        // Find the world for this region
        for (Player player : players) {
            if (player.getWorld().getName().equals(region.world)) {
                world = player.getWorld();
                break;
            }
        }
        
        if (world == null) return requiredChunks;
        
        // Add chunks around players in this region
        for (Player player : players) {
            if (!player.getWorld().getName().equals(region.world)) continue;
            
            // Add chunks around current position
            addChunksAroundLocation(player.getLocation(), requiredChunks, 2);
            
            // Add chunks around predicted position
            PlayerMovementData movementData = playerMovements.get(player.getUniqueId());
            if (movementData != null) {
                Location predictedLoc = movementData.getPredictedLocation(world);
                addChunksAroundLocation(predictedLoc, requiredChunks, 1);
            }
        }
        
        return requiredChunks;
    }
    
    /**
     * Add chunks around a location to the set (optimized to reduce FPS impact)
     */
    private void addChunksAroundLocation(Location location, Set<Chunk> chunks, int radius) {
        World world = location.getWorld();
        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        
        // Only add chunks that are not already loaded to reduce unnecessary loading
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                // Check if chunk is already loaded before adding
                if (world.isChunkLoaded(x, z)) {
                    Chunk chunk = world.getChunkAt(x, z);
                    chunks.add(chunk);
                } else {
                    // Only add unloaded chunks to the set for potential loading
                    // This prevents unnecessary chunk loading operations
                    Chunk chunk = world.getChunkAt(x, z, false); // Don't generate if not exists
                    if (chunk != null) {
                        chunks.add(chunk);
                    }
                }
            }
        }
    }
    
    /**
     * Update player movement data
     */
    public void updatePlayerMovement(Player player) {
        Location currentLocation = player.getLocation();
        PlayerMovementData data = playerMovements.computeIfAbsent(
            player.getUniqueId(), 
            k -> new PlayerMovementData(currentLocation)
        );
        
        data.update(currentLocation);
    }
    
    /**
     * Remove player movement data
     */
    public void removePlayerMovement(UUID playerId) {
        playerMovements.remove(playerId);
    }
    
    /**
     * Load a chunk (async-safe)
     */
    private boolean loadChunk(Chunk chunk) {
        try {
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Unload a chunk (async-safe)
     */
    private boolean unloadChunk(Chunk chunk) {
        try {
            if (chunk.isLoaded()) {
                chunk.unload(true);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get region key for tracking
     */
    private String getRegionKey(HotRegion region) {
        return region.world + ":" + region.clusterId;
    }
    
    /**
     * Get current budget for a region
     */
    public int getBudgetForRegion(HotRegion region) {
        String regionKey = getRegionKey(region);
        AtomicInteger budget = regionBudgets.get(regionKey);
        return budget != null ? budget.get() : 0;
    }
    
    /**
     * Get loaded chunk count for a region
     */
    public int getLoadedChunkCount(HotRegion region) {
        String regionKey = getRegionKey(region);
        Set<Chunk> chunks = loadedChunks.get(regionKey);
        return chunks != null ? chunks.size() : 0;
    }
    
    /**
     * Get total loaded chunk count across all regions
     */
    public int getTotalLoadedChunkCount() {
        return loadedChunks.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
    
    /**
     * Force unload chunks in cold regions
     */
    public void aggressiveUnloadColdRegions(Collection<Player> players) {
        Set<String> activeWorlds = new HashSet<>();
        for (Player player : players) {
            activeWorlds.add(player.getWorld().getName());
        }
        
        for (String worldName : activeWorlds) {
            World world = players.stream()
                .filter(p -> p.getWorld().getName().equals(worldName))
                .findFirst()
                .map(Player::getWorld)
                .orElse(null);
            
            if (world == null) continue;
            
            // Get all loaded chunks in this world
            Chunk[] loadedChunks = world.getLoadedChunks();
            
            for (Chunk chunk : loadedChunks) {
                Location chunkLoc = chunk.getBlock(0, 0, 0).getLocation();
                
                // Check if any player is near this chunk
                boolean playerNearby = players.stream()
                    .filter(p -> p.getWorld().equals(world))
                    .anyMatch(p -> p.getLocation().distance(chunkLoc) < 128); // 8 chunks
                
                if (!playerNearby && chunk.isLoaded()) {
                    chunk.unload(true);
                }
            }
        }
    }
}
