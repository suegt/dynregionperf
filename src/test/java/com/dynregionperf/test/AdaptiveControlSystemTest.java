package com.dynregionperf.test;

import com.dynregionperf.control.AdaptiveControlSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdaptiveControlSystem
 */
public class AdaptiveControlSystemTest {
    
    private AdaptiveControlSystem controlSystem;
    
    @BeforeEach
    public void setUp() {
        controlSystem = new AdaptiveControlSystem(45.0, 19.5);
    }
    
    @Test
    public void testInitialState() {
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        
        assertEquals(0, output.viewDistanceAdjustment);
        assertEquals(0, output.chunkBudgetAdjustment);
        assertEquals(0.0, output.randomTickRatioAdjustment, 0.01);
        assertFalse(output.aggressiveUnload);
    }
    
    @Test
    public void testGoodPerformance() {
        // Simulate good performance (low MSPT, high TPS)
        controlSystem.update(30.0, 20.0, 5, 1000);
        
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        
        // Should increase load (positive adjustments)
        assertTrue(output.viewDistanceAdjustment >= 0);
        assertTrue(output.chunkBudgetAdjustment >= 0);
        assertTrue(output.randomTickRatioAdjustment >= 0);
        assertFalse(output.aggressiveUnload);
    }
    
    @Test
    public void testPoorPerformance() {
        // Simulate poor performance (high MSPT, low TPS)
        controlSystem.update(60.0, 15.0, 10, 2000);
        
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        
        // Should decrease load (negative adjustments)
        assertTrue(output.viewDistanceAdjustment <= 0);
        assertTrue(output.chunkBudgetAdjustment <= 0);
        assertTrue(output.randomTickRatioAdjustment <= 0);
    }
    
    @Test
    public void testVeryPoorPerformance() {
        // Simulate very poor performance
        controlSystem.update(80.0, 10.0, 15, 3000);
        
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        
        // Should apply aggressive measures
        assertTrue(output.viewDistanceAdjustment <= -2);
        assertTrue(output.chunkBudgetAdjustment <= -4);
        assertTrue(output.aggressiveUnload);
    }
    
    @Test
    public void testPerformanceStats() {
        // Add multiple samples
        controlSystem.update(40.0, 19.0, 5, 1000);
        controlSystem.update(50.0, 18.0, 6, 1200);
        controlSystem.update(35.0, 20.0, 4, 900);
        
        AdaptiveControlSystem.PerformanceStats stats = controlSystem.getPerformanceStats();
        
        assertTrue(stats.avgMspt > 0);
        assertTrue(stats.avgTps > 0);
        assertTrue(stats.minMspt <= stats.maxMspt);
        assertTrue(stats.minTps <= stats.maxTps);
    }
    
    @Test
    public void testReset() {
        // Add some data
        controlSystem.update(50.0, 18.0, 5, 1000);
        
        // Reset
        controlSystem.reset();
        
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        assertEquals(0, output.viewDistanceAdjustment);
        assertEquals(0, output.chunkBudgetAdjustment);
        assertEquals(0.0, output.randomTickRatioAdjustment, 0.01);
        assertFalse(output.aggressiveUnload);
    }
    
    @Test
    public void testControlOutputClamping() {
        // Test extreme values to ensure clamping works
        controlSystem.update(200.0, 5.0, 100, 10000);
        
        AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
        
        // Should be clamped to reasonable ranges
        assertTrue(output.viewDistanceAdjustment >= -3);
        assertTrue(output.viewDistanceAdjustment <= 3);
        assertTrue(output.chunkBudgetAdjustment >= -10);
        assertTrue(output.chunkBudgetAdjustment <= 10);
        assertTrue(output.randomTickRatioAdjustment >= -0.5);
        assertTrue(output.randomTickRatioAdjustment <= 0.5);
    }
    
    @Test
    public void testUpdateFrequency() {
        long startTime = System.currentTimeMillis();
        
        // Multiple rapid updates should only process the first one
        controlSystem.update(50.0, 18.0, 5, 1000);
        controlSystem.update(60.0, 17.0, 6, 1100);
        controlSystem.update(70.0, 16.0, 7, 1200);
        
        long endTime = System.currentTimeMillis();
        
        // Should complete quickly since only first update is processed
        assertTrue(endTime - startTime < 1000); // Less than 1 second
    }
}
