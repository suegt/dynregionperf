package com.dynregionperf.cluster;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Density clustering system using BFS/union-find algorithm
 * Groups nearby high-density grid cells into hot regions
 */
public class DensityClusterer {
    private final int gridSize;
    private final int hotThresholdPlayers;
    
    // Grid cell representation
    public static class GridCell {
        public final int x, z;
        public final String world;
        public int playerCount;
        public boolean isHot;
        public int clusterId;
        
        public GridCell(int x, int z, String world) {
            this.x = x;
            this.z = z;
            this.world = world;
            this.playerCount = 0;
            this.isHot = false;
            this.clusterId = -1;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GridCell gridCell = (GridCell) obj;
            return x == gridCell.x && z == gridCell.z && Objects.equals(world, gridCell.world);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z, world);
        }
        
        @Override
        public String toString() {
            return String.format("GridCell{x=%d, z=%d, world='%s', players=%d, hot=%s}", 
                x, z, world, playerCount, isHot);
        }
    }
    
    
    public DensityClusterer(int gridSize, int hotThresholdPlayers) {
        this.gridSize = gridSize;
        this.hotThresholdPlayers = hotThresholdPlayers;
    }
    
    /**
     * Get grid size
     */
    public int getGridSize() {
        return gridSize;
    }
    
    /**
     * Create density map from online players
     */
    public Map<GridCell, Integer> createDensityMap(Collection<Player> players) {
        Map<GridCell, Integer> densityMap = new ConcurrentHashMap<>();
        
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;
            
            Location loc = player.getLocation();
            GridCell cell = getGridCell(loc);
            densityMap.merge(cell, 1, Integer::sum);
        }
        
        return densityMap;
    }
    
    /**
     * Convert location to grid cell coordinates
     */
    public GridCell getGridCell(Location location) {
        int gridX = (int) Math.floor(location.getX() / gridSize);
        int gridZ = (int) Math.floor(location.getZ() / gridSize);
        return new GridCell(gridX, gridZ, location.getWorld().getName());
    }
    
    /**
     * Cluster hot cells using BFS algorithm
     */
    public List<HotRegion> clusterHotRegions(Map<GridCell, Integer> densityMap) {
        // Mark hot cells
        Map<GridCell, GridCell> cellMap = new HashMap<>();
        List<GridCell> hotCells = new ArrayList<>();
        
        for (Map.Entry<GridCell, Integer> entry : densityMap.entrySet()) {
            GridCell cell = entry.getKey();
            cell.playerCount = entry.getValue();
            cell.isHot = cell.playerCount >= hotThresholdPlayers;
            cell.clusterId = -1;
            cellMap.put(cell, cell);
            
            if (cell.isHot) {
                hotCells.add(cell);
            }
        }
        
        if (hotCells.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Cluster hot cells using BFS
        List<HotRegion> regions = new ArrayList<>();
        int currentClusterId = 0;
        
        for (GridCell startCell : hotCells) {
            if (startCell.clusterId != -1) continue; // Already processed
            
            Set<GridCell> clusterCells = new HashSet<>();
            Queue<GridCell> queue = new LinkedList<>();
            queue.offer(startCell);
            startCell.clusterId = currentClusterId;
            
            // BFS to find connected hot cells
            while (!queue.isEmpty()) {
                GridCell current = queue.poll();
                clusterCells.add(current);
                
                // Check 8 neighboring cells
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        
                        GridCell neighbor = new GridCell(
                            current.x + dx, 
                            current.z + dz, 
                            current.world
                        );
                        
                        GridCell existingNeighbor = cellMap.get(neighbor);
                        if (existingNeighbor != null && 
                            existingNeighbor.isHot && 
                            existingNeighbor.clusterId == -1) {
                            
                            existingNeighbor.clusterId = currentClusterId;
                            queue.offer(existingNeighbor);
                        }
                    }
                }
            }
            
            if (!clusterCells.isEmpty()) {
                regions.add(new HotRegion(currentClusterId, startCell.world, clusterCells));
                currentClusterId++;
            }
        }
        
        return regions;
    }
    
    /**
     * Check if a location is in a hot region
     */
    public boolean isLocationInHotRegion(Location location, List<HotRegion> hotRegions) {
        GridCell cell = getGridCell(location);
        
        for (HotRegion region : hotRegions) {
            if (!region.world.equals(cell.world)) continue;
            
            if (cell.x >= region.minX && cell.x <= region.maxX &&
                cell.z >= region.minZ && cell.z <= region.maxZ) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get the hot region containing a location, or null if not in any hot region
     */
    public HotRegion getHotRegionForLocation(Location location, List<HotRegion> hotRegions) {
        GridCell cell = getGridCell(location);
        
        for (HotRegion region : hotRegions) {
            if (!region.world.equals(cell.world)) continue;
            
            if (cell.x >= region.minX && cell.x <= region.maxX &&
                cell.z >= region.minZ && cell.z <= region.maxZ) {
                return region;
            }
        }
        
        return null;
    }
    
    /**
     * Calculate distance between two locations in grid cells
     */
    public double getGridDistance(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return Double.MAX_VALUE;
        }
        
        GridCell cell1 = getGridCell(loc1);
        GridCell cell2 = getGridCell(loc2);
        
        int dx = cell1.x - cell2.x;
        int dz = cell1.z - cell2.z;
        
        return Math.sqrt(dx * dx + dz * dz);
    }
}
