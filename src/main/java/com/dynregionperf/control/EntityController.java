package com.dynregionperf.control;

import com.dynregionperf.cluster.DensityClusterer;
import com.dynregionperf.cluster.HotRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regional entity controls and limits system
 * Manages entity spawning, despawning, and limits based on region temperature
 */
public class EntityController {
    private final DensityClusterer clusterer;
    private final Map<String, EntityLimits> entityLimits = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEntityCleanup = new ConcurrentHashMap<>();
    
    // Entity type categories
    private final Set<EntityType> mobTypes = new HashSet<>();
    private final Set<EntityType> animalTypes = new HashSet<>();
    private final Set<EntityType> projectileTypes = new HashSet<>();
    
    // Configuration
    private final int coldMobCap, coldAnimalCap, coldProjectileCap;
    private final double randomTickScaleHot, randomTickScaleCold;
    
    public static class EntityLimits {
        public final int maxMobs;
        public final int maxAnimals;
        public final int maxProjectiles;
        public final double randomTickScale;
        
        public EntityLimits(int maxMobs, int maxAnimals, int maxProjectiles, double randomTickScale) {
            this.maxMobs = maxMobs;
            this.maxAnimals = maxAnimals;
            this.maxProjectiles = maxProjectiles;
            this.randomTickScale = randomTickScale;
        }
    }
    
    public EntityController(DensityClusterer clusterer,
                           int coldMobCap, int coldAnimalCap, int coldProjectileCap,
                           double randomTickScaleHot, double randomTickScaleCold) {
        this.clusterer = clusterer;
        this.coldMobCap = coldMobCap;
        this.coldAnimalCap = coldAnimalCap;
        this.coldProjectileCap = coldProjectileCap;
        this.randomTickScaleHot = randomTickScaleHot;
        this.randomTickScaleCold = randomTickScaleCold;
        
        initializeEntityTypes();
    }
    
    /**
     * Initialize entity type categories
     */
    private void initializeEntityTypes() {
        // Mob types
        mobTypes.addAll(Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME, EntityType.MAGMA_CUBE,
            EntityType.BLAZE, EntityType.GHAST, EntityType.PIGLIN, EntityType.HOGLIN,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER_SKELETON, EntityType.STRAY,
            EntityType.HUSK, EntityType.DROWNED, EntityType.PHANTOM, EntityType.VEX,
            EntityType.VINDICATOR, EntityType.EVOKER, EntityType.PILLAGER, EntityType.RAVAGER,
            EntityType.WARDEN, EntityType.ALLAY, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN,
            EntityType.SHULKER, EntityType.ENDERMITE, EntityType.SILVERFISH, EntityType.CAVE_SPIDER,
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.GIANT
        ));
        
        // Animal types
        animalTypes.addAll(Arrays.asList(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA,
            EntityType.RABBIT, EntityType.FOX, EntityType.PANDA,
            EntityType.POLAR_BEAR, EntityType.WOLF, EntityType.CAT, EntityType.OCELOT,
            EntityType.PARROT, EntityType.TURTLE, EntityType.DOLPHIN, EntityType.SQUID,
            EntityType.GLOW_SQUID, EntityType.AXOLOTL, EntityType.GOAT, EntityType.FROG,
            EntityType.TADPOLE, EntityType.CAMEL, EntityType.SNIFFER, EntityType.BEE,
            EntityType.BAT, EntityType.MUSHROOM_COW, EntityType.IRON_GOLEM
        ));
        
        // Projectile types
        projectileTypes.addAll(Arrays.asList(
            EntityType.ARROW, EntityType.SPECTRAL_ARROW,
            EntityType.TRIDENT, EntityType.SNOWBALL, EntityType.EGG, EntityType.ENDER_PEARL,
            EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.DRAGON_FIREBALL, EntityType.WITHER_SKULL,
            EntityType.SHULKER_BULLET, EntityType.LLAMA_SPIT, EntityType.FISHING_HOOK
        ));
    }
    
    /**
     * Update entity limits for all regions
     */
    public void updateEntityLimits(List<HotRegion> hotRegions, Collection<World> worlds) {
        long currentTime = System.currentTimeMillis();
        
        // Update limits for hot regions
        for (HotRegion region : hotRegions) {
            String regionKey = getRegionKey(region);
            entityLimits.put(regionKey, new EntityLimits(
                Integer.MAX_VALUE, // No limits for hot regions
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                randomTickScaleHot
            ));
        }
        
        // Update limits for cold regions (world-wide)
        for (World world : worlds) {
            String worldKey = "cold:" + world.getName();
            entityLimits.put(worldKey, new EntityLimits(
                coldMobCap,
                coldAnimalCap,
                coldProjectileCap,
                randomTickScaleCold
            ));
        }
        
        // Clean up old region limits
        entityLimits.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            boolean isHotRegion = hotRegions.stream()
                .anyMatch(region -> getRegionKey(region).equals(key));
            boolean isColdWorld = worlds.stream()
                .anyMatch(world -> key.equals("cold:" + world.getName()));
            
            return !isHotRegion && !isColdWorld;
        });
    }
    
    /**
     * Apply entity limits and cleanup
     */
    public void applyEntityLimits(List<HotRegion> hotRegions, Collection<World> worlds) {
        long currentTime = System.currentTimeMillis();
        
        for (World world : worlds) {
            String worldKey = "cold:" + world.getName();
            Long lastCleanup = lastEntityCleanup.get(worldKey);
            
            // Only cleanup every 10 seconds
            if (lastCleanup != null && currentTime - lastCleanup < 10000) {
                continue;
            }
            
            lastEntityCleanup.put(worldKey, currentTime);
            
            // Get all entities in the world
            Collection<Entity> entities = world.getEntities();
            
            // Count entities by type
            Map<EntityType, Integer> entityCounts = new HashMap<>();
            Map<EntityType, List<Entity>> entitiesByType = new HashMap<>();
            
            for (Entity entity : entities) {
                EntityType type = entity.getType();
                entityCounts.merge(type, 1, Integer::sum);
                entitiesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(entity);
            }
            
            // Apply limits for cold regions
            applyLimitsToWorld(world, hotRegions, entityCounts, entitiesByType);
        }
    }
    
    /**
     * Apply entity limits to a specific world
     */
    private void applyLimitsToWorld(World world, List<HotRegion> hotRegions,
                                   Map<EntityType, Integer> entityCounts,
                                   Map<EntityType, List<Entity>> entitiesByType) {
        
        // Count entities in hot regions vs cold regions
        Map<EntityType, Integer> hotRegionCounts = new HashMap<>();
        Map<EntityType, Integer> coldRegionCounts = new HashMap<>();
        
        for (Map.Entry<EntityType, List<Entity>> entry : entitiesByType.entrySet()) {
            EntityType type = entry.getKey();
            List<Entity> entities = entry.getValue();
            
            int hotCount = 0, coldCount = 0;
            
            for (Entity entity : entities) {
                Location loc = entity.getLocation();
                boolean inHotRegion = clusterer.isLocationInHotRegion(loc, hotRegions);
                
                if (inHotRegion) {
                    hotCount++;
                } else {
                    coldCount++;
                }
            }
            
            hotRegionCounts.put(type, hotCount);
            coldRegionCounts.put(type, coldCount);
        }
        
        // Apply limits to cold region entities
        String worldKey = "cold:" + world.getName();
        EntityLimits limits = entityLimits.get(worldKey);
        
        if (limits != null) {
            // Apply mob limits
            applyLimitsToEntityType(world, entitiesByType, mobTypes, coldRegionCounts, limits.maxMobs, hotRegions);
            
            // Apply animal limits
            applyLimitsToEntityType(world, entitiesByType, animalTypes, coldRegionCounts, limits.maxAnimals, hotRegions);
            
            // Apply projectile limits
            applyLimitsToEntityType(world, entitiesByType, projectileTypes, coldRegionCounts, limits.maxProjectiles, hotRegions);
        }
    }
    
    /**
     * Apply limits to a specific entity type category
     */
    private void applyLimitsToEntityType(World world, Map<EntityType, List<Entity>> entitiesByType,
                                       Set<EntityType> entityTypes, Map<EntityType, Integer> coldRegionCounts,
                                       int maxLimit, List<HotRegion> hotRegions) {
        
        int totalColdEntities = 0;
        List<Entity> coldEntities = new ArrayList<>();
        
        for (EntityType type : entityTypes) {
            if (!entityTypes.contains(type)) continue;
            
            List<Entity> entities = entitiesByType.get(type);
            if (entities == null) continue;
            
            for (Entity entity : entities) {
                Location loc = entity.getLocation();
                boolean inHotRegion = clusterer.isLocationInHotRegion(loc, hotRegions);
                
                if (!inHotRegion) {
                    totalColdEntities++;
                    coldEntities.add(entity);
                }
            }
        }
        
        // If we exceed the limit, remove excess entities
        if (totalColdEntities > maxLimit) {
            int toRemove = totalColdEntities - maxLimit;
            
            // Sort by distance from players (remove furthest first)
            coldEntities.sort((e1, e2) -> {
                double dist1 = getDistanceToNearestPlayer(e1, world);
                double dist2 = getDistanceToNearestPlayer(e2, world);
                return Double.compare(dist2, dist1); // Furthest first
            });
            
            // Remove excess entities
            for (int i = 0; i < toRemove && i < coldEntities.size(); i++) {
                Entity entity = coldEntities.get(i);
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
    }
    
    /**
     * Get distance to nearest player
     */
    private double getDistanceToNearestPlayer(Entity entity, World world) {
        double minDistance = Double.MAX_VALUE;
        
        for (Player player : world.getPlayers()) {
            double distance = entity.getLocation().distance(player.getLocation());
            minDistance = Math.min(minDistance, distance);
        }
        
        return minDistance == Double.MAX_VALUE ? 0 : minDistance;
    }
    
    /**
     * Apply random tick scaling to regions
     */
    public void applyRandomTickScaling(List<HotRegion> hotRegions, Collection<World> worlds) {
        // Note: Random tick scaling is typically handled at the server level
        // This method is a placeholder for potential future implementation
        // or integration with server-specific APIs
        
        for (World world : worlds) {
            // Check if world has any hot regions
            boolean hasHotRegions = hotRegions.stream()
                .anyMatch(region -> region.world.equals(world.getName()));
            
            if (hasHotRegions) {
                // Hot regions get normal random tick rate
                // This would require server-specific implementation
            } else {
                // Cold regions get reduced random tick rate
                // This would require server-specific implementation
            }
        }
    }
    
    /**
     * Get entity limits for a region
     */
    public EntityLimits getEntityLimits(String regionKey) {
        return entityLimits.get(regionKey);
    }
    
    /**
     * Get entity statistics for a world
     */
    public EntityStats getEntityStats(World world, List<HotRegion> hotRegions) {
        Collection<Entity> entities = world.getEntities();
        
        int totalMobs = 0, totalAnimals = 0, totalProjectiles = 0;
        int hotMobs = 0, hotAnimals = 0, hotProjectiles = 0;
        
        for (Entity entity : entities) {
            EntityType type = entity.getType();
            Location loc = entity.getLocation();
            boolean inHotRegion = clusterer.isLocationInHotRegion(loc, hotRegions);
            
            if (mobTypes.contains(type)) {
                totalMobs++;
                if (inHotRegion) hotMobs++;
            } else if (animalTypes.contains(type)) {
                totalAnimals++;
                if (inHotRegion) hotAnimals++;
            } else if (projectileTypes.contains(type)) {
                totalProjectiles++;
                if (inHotRegion) hotProjectiles++;
            }
        }
        
        return new EntityStats(totalMobs, totalAnimals, totalProjectiles,
                             hotMobs, hotAnimals, hotProjectiles);
    }
    
    /**
     * Get region key for tracking
     */
    private String getRegionKey(HotRegion region) {
        return "hot:" + region.world + ":" + region.clusterId;
    }
    
    /**
     * Entity statistics
     */
    public static class EntityStats {
        public final int totalMobs, totalAnimals, totalProjectiles;
        public final int hotMobs, hotAnimals, hotProjectiles;
        
        public EntityStats(int totalMobs, int totalAnimals, int totalProjectiles,
                          int hotMobs, int hotAnimals, int hotProjectiles) {
            this.totalMobs = totalMobs;
            this.totalAnimals = totalAnimals;
            this.totalProjectiles = totalProjectiles;
            this.hotMobs = hotMobs;
            this.hotAnimals = hotAnimals;
            this.hotProjectiles = hotProjectiles;
        }
        
        public int getColdMobs() { return totalMobs - hotMobs; }
        public int getColdAnimals() { return totalAnimals - hotAnimals; }
        public int getColdProjectiles() { return totalProjectiles - hotProjectiles; }
        
        @Override
        public String toString() {
            return String.format("EntityStats{mobs=%d(%d hot), animals=%d(%d hot), projectiles=%d(%d hot)}",
                totalMobs, hotMobs, totalAnimals, hotAnimals, totalProjectiles, hotProjectiles);
        }
    }
}
