package com.dynregionperf.test;

import com.dynregionperf.cluster.DensityClusterer;
import com.dynregionperf.cluster.HotRegion;
import com.dynregionperf.cluster.DensityClusterer.GridCell;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DensityClusterer
 */
public class DensityClustererTest {
    
    @Mock
    private World mockWorld;
    
    private DensityClusterer clusterer;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        clusterer = new DensityClusterer(64, 3);
        
        when(mockWorld.getName()).thenReturn("world");
    }
    
    @Test
    public void testGetGridCell() {
        // Test grid cell calculation
        Location loc1 = new Location(mockWorld, 0, 64, 0);
        GridCell cell1 = clusterer.getGridCell(loc1);
        assertEquals(0, cell1.x);
        assertEquals(0, cell1.z);
        assertEquals("world", cell1.world);
        
        Location loc2 = new Location(mockWorld, 100, 64, 200);
        GridCell cell2 = clusterer.getGridCell(loc2);
        assertEquals(1, cell2.x); // 100 / 64 = 1
        assertEquals(3, cell2.z); // 200 / 64 = 3
    }
    
    @Test
    public void testCreateDensityMap() {
        // Create mock players
        List<org.bukkit.entity.Player> players = new ArrayList<>();
        
        // Add players in the same grid cell
        for (int i = 0; i < 5; i++) {
            org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getLocation()).thenReturn(new Location(mockWorld, i * 10, 64, 0));
            players.add(player);
        }
        
        Map<DensityClusterer.GridCell, Integer> densityMap = clusterer.createDensityMap(players);
        
        // All players should be in the same grid cell (0,0)
        assertEquals(1, densityMap.size());
        assertEquals(5, densityMap.values().iterator().next());
    }
    
    @Test
    public void testClusterHotRegions() {
        // Create density map with hot cells
        Map<GridCell, Integer> densityMap = new HashMap<>();
        
        // Add hot cells (3+ players each)
        GridCell cell1 = new GridCell(0, 0, "world");
        GridCell cell2 = new GridCell(1, 0, "world");
        GridCell cell3 = new GridCell(0, 1, "world");
        
        densityMap.put(cell1, 5);
        densityMap.put(cell2, 4);
        densityMap.put(cell3, 3);
        
        // Add cold cell (2 players)
        GridCell cell4 = new GridCell(2, 2, "world");
        densityMap.put(cell4, 2);
        
        List<HotRegion> regions = clusterer.clusterHotRegions(densityMap);
        
        // Should create one hot region with 3 cells
        assertEquals(1, regions.size());
        assertEquals(3, regions.get(0).cells.size());
        assertEquals(12, regions.get(0).totalPlayers); // 5 + 4 + 3
    }
    
    @Test
    public void testIsLocationInHotRegion() {
        // Create a hot region
        Set<GridCell> cells = new HashSet<>();
        cells.add(new GridCell(0, 0, "world"));
        cells.add(new GridCell(1, 0, "world"));
        
        HotRegion region = new HotRegion(0, "world", cells);
        List<HotRegion> regions = Arrays.asList(region);
        
        // Test locations inside the region
        Location insideLoc = new Location(mockWorld, 50, 64, 10);
        assertTrue(clusterer.isLocationInHotRegion(insideLoc, regions));
        
        // Test locations outside the region
        Location outsideLoc = new Location(mockWorld, 200, 64, 200);
        assertFalse(clusterer.isLocationInHotRegion(outsideLoc, regions));
    }
    
    @Test
    public void testGetGridDistance() {
        Location loc1 = new Location(mockWorld, 0, 64, 0);
        Location loc2 = new Location(mockWorld, 128, 64, 128);
        
        double distance = clusterer.getGridDistance(loc1, loc2);
        
        // Distance should be sqrt(2^2 + 2^2) = sqrt(8) ≈ 2.83
        assertEquals(2.0, distance, 0.1);
    }
    
    @Test
    public void testHotRegionProperties() {
        Set<GridCell> cells = new HashSet<>();
        cells.add(new GridCell(0, 0, "world"));
        cells.add(new GridCell(1, 0, "world"));
        cells.add(new GridCell(0, 1, "world"));
        
        // Set player counts
        for (GridCell cell : cells) {
            cell.playerCount = 3;
        }
        
        HotRegion region = new HotRegion(0, "world", cells);
        
        assertEquals(0, region.minX);
        assertEquals(1, region.maxX);
        assertEquals(0, region.minZ);
        assertEquals(1, region.maxZ);
        assertEquals(4, region.getArea()); // 2x2 grid
        assertEquals(9, region.totalPlayers); // 3 players per cell
        assertEquals(2.25, region.getPlayerDensity(), 0.01); // 9 players / 4 area
    }
}
