package com.dynregionperf.cluster;

import java.util.Set;

/**
 * Hot region representation
 * Represents a cluster of high-density grid cells
 */
public class HotRegion {
    public final int clusterId;
    public final String world;
    public final Set<DensityClusterer.GridCell> cells;
    public final int totalPlayers;
    public final int minX, maxX, minZ, maxZ;
    
    public HotRegion(int clusterId, String world, Set<DensityClusterer.GridCell> cells) {
        this.clusterId = clusterId;
        this.world = world;
        this.cells = new java.util.HashSet<>(cells);
        this.totalPlayers = cells.stream().mapToInt(cell -> cell.playerCount).sum();
        
        // Calculate bounding box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (DensityClusterer.GridCell cell : cells) {
            minX = Math.min(minX, cell.x);
            maxX = Math.max(maxX, cell.x);
            minZ = Math.min(minZ, cell.z);
            maxZ = Math.max(maxZ, cell.z);
        }
        
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }
    
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }
    
    public double getPlayerDensity() {
        return (double) totalPlayers / getArea();
    }
    
    @Override
    public String toString() {
        return String.format("HotRegion{id=%d, world='%s', players=%d, area=%d, bounds=(%d,%d)-(%d,%d)}", 
            clusterId, world, totalPlayers, getArea(), minX, minZ, maxX, maxZ);
    }
}
