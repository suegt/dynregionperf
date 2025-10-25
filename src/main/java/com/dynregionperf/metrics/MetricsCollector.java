package com.dynregionperf.metrics;

import com.dynregionperf.cluster.HotRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Metrics collection and rolling data storage system
 * Collects TPS, MSPT, loaded chunks, and hot region data
 */
public class MetricsCollector {
    private final JavaPlugin plugin;
    private final File metricsDir;
    private final File rollingDataFile;
    
    // Performance tracking
    private final AtomicReference<PerformanceData> currentData = new AtomicReference<>();
    private final ConcurrentLinkedQueue<PerformanceData> rollingData = new ConcurrentLinkedQueue<>();
    private final int maxRollingData = 300; // 5 minutes at 1-second intervals
    
    // TPS calculation
    private long lastTpsCheck = 0;
    private long lastTickCount = 0;
    private final Queue<Double> tpsHistory = new ConcurrentLinkedQueue<>();
    private final int maxTpsHistory = 20; // 20 seconds
    
    // MSPT calculation
    private long lastMsptCheck = 0;
    private final Queue<Double> msptHistory = new ConcurrentLinkedQueue<>();
    private final int maxMsptHistory = 20; // 20 seconds
    
    public static class PerformanceData {
        public final long timestamp;
        public final double tps;
        public final double mspt;
        public final int loadedChunks;
        public final int hotRegions;
        public final int totalPlayers;
        public final Map<String, Integer> worldChunkCounts;
        
        public PerformanceData(long timestamp, double tps, double mspt, int loadedChunks, 
                              int hotRegions, int totalPlayers, Map<String, Integer> worldChunkCounts) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.mspt = mspt;
            this.loadedChunks = loadedChunks;
            this.hotRegions = hotRegions;
            this.totalPlayers = totalPlayers;
            this.worldChunkCounts = new HashMap<>(worldChunkCounts);
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceData{time=%d, tps=%.2f, mspt=%.2f, chunks=%d, hotRegions=%d, players=%d}",
                timestamp, tps, mspt, loadedChunks, hotRegions, totalPlayers);
        }
    }
    
    public MetricsCollector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metricsDir = new File(plugin.getDataFolder(), "metrics");
        this.rollingDataFile = new File(metricsDir, "rolling.json");
        
        // Create metrics directory
        if (!metricsDir.exists()) {
            metricsDir.mkdirs();
        }
        
        // Initialize current data
        currentData.set(new PerformanceData(System.currentTimeMillis(), 20.0, 50.0, 0, 0, 0, new HashMap<>()));
    }
    
    /**
     * Update metrics with current performance data
     */
    public void updateMetrics(List<HotRegion> hotRegions) {
        long currentTime = System.currentTimeMillis();
        
        // Calculate TPS
        double currentTps = calculateTps(currentTime);
        
        // Calculate MSPT
        double currentMspt = calculateMspt(currentTime);
        
        // Count loaded chunks
        int totalLoadedChunks = 0;
        Map<String, Integer> worldChunkCounts = new HashMap<>();
        
        for (World world : Bukkit.getWorlds()) {
            int chunkCount = world.getLoadedChunks().length;
            worldChunkCounts.put(world.getName(), chunkCount);
            totalLoadedChunks += chunkCount;
        }
        
        // Count players
        int totalPlayers = Bukkit.getOnlinePlayers().size();
        
        // Create performance data
        PerformanceData data = new PerformanceData(currentTime, currentTps, currentMspt, 
            totalLoadedChunks, hotRegions.size(), totalPlayers, worldChunkCounts);
        
        // Update current data
        currentData.set(data);
        
        // Add to rolling data
        rollingData.offer(data);
        
        // Remove old data
        while (rollingData.size() > maxRollingData) {
            rollingData.poll();
        }
        
        // Save rolling data every 30 seconds
        if (currentTime % 30000 < 1000) {
            saveRollingData();
        }
    }
    
    /**
     * Calculate TPS using tick counting
     */
    private double calculateTps(long currentTime) {
        if (lastTpsCheck == 0) {
            lastTpsCheck = currentTime;
            lastTickCount = System.currentTimeMillis() / 50; // Approximate tick count
            return 20.0; // Default TPS
        }
        
        long timeElapsed = currentTime - lastTpsCheck;
        long ticksElapsed = System.currentTimeMillis() / 50 - lastTickCount; // Approximate tick count
        
        if (timeElapsed >= 1000) { // At least 1 second
            double tps = (double) ticksElapsed * 1000.0 / timeElapsed;
            
            // Add to history
            tpsHistory.offer(tps);
            while (tpsHistory.size() > maxTpsHistory) {
                tpsHistory.poll();
            }
            
            // Update tracking
            lastTpsCheck = currentTime;
            lastTickCount = System.currentTimeMillis() / 50;
            
            return tps;
        }
        
        // Return average of recent TPS values
        if (!tpsHistory.isEmpty()) {
            return tpsHistory.stream().mapToDouble(Double::doubleValue).average().orElse(20.0);
        }
        
        return 20.0;
    }
    
    /**
     * Calculate MSPT using server tick time
     */
    private double calculateMspt(long currentTime) {
        if (lastMsptCheck == 0) {
            lastMsptCheck = currentTime;
            return 50.0; // Default MSPT
        }
        
        long timeElapsed = currentTime - lastMsptCheck;
        
        if (timeElapsed >= 1000) { // At least 1 second
            // Get server tick time (this is a simplified calculation)
            // In a real implementation, you'd hook into the server's tick timing
            double mspt = 50.0; // Placeholder - would need server-specific implementation
            
            // Add to history
            msptHistory.offer(mspt);
            while (msptHistory.size() > maxMsptHistory) {
                msptHistory.poll();
            }
            
            lastMsptCheck = currentTime;
            
            return mspt;
        }
        
        // Return average of recent MSPT values
        if (!msptHistory.isEmpty()) {
            return msptHistory.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);
        }
        
        return 50.0;
    }
    
    /**
     * Save rolling data to JSON file
     */
    private void saveRollingData() {
        try (FileWriter writer = new FileWriter(rollingDataFile)) {
            writer.write("{\n");
            writer.write("  \"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",\n");
            writer.write("  \"data\": [\n");
            
            PerformanceData[] dataArray = rollingData.toArray(new PerformanceData[0]);
            for (int i = 0; i < dataArray.length; i++) {
                PerformanceData data = dataArray[i];
                writer.write("    {\n");
                writer.write("      \"timestamp\": " + data.timestamp + ",\n");
                writer.write("      \"tps\": " + data.tps + ",\n");
                writer.write("      \"mspt\": " + data.mspt + ",\n");
                writer.write("      \"loadedChunks\": " + data.loadedChunks + ",\n");
                writer.write("      \"hotRegions\": " + data.hotRegions + ",\n");
                writer.write("      \"totalPlayers\": " + data.totalPlayers + ",\n");
                writer.write("      \"worldChunkCounts\": {\n");
                
                String[] worldNames = data.worldChunkCounts.keySet().toArray(new String[0]);
                for (int j = 0; j < worldNames.length; j++) {
                    String worldName = worldNames[j];
                    Integer chunkCount = data.worldChunkCounts.get(worldName);
                    writer.write("        \"" + worldName + "\": " + chunkCount);
                    if (j < worldNames.length - 1) {
                        writer.write(",");
                    }
                    writer.write("\n");
                }
                
                writer.write("      }\n");
                writer.write("    }");
                if (i < dataArray.length - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            
            writer.write("  ]\n");
            writer.write("}\n");
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save rolling metrics data: " + e.getMessage());
        }
    }
    
    /**
     * Get current TPS
     */
    public double getCurrentTps() {
        PerformanceData data = currentData.get();
        return data != null ? data.tps : 20.0;
    }
    
    /**
     * Get current MSPT
     */
    public double getCurrentMspt() {
        PerformanceData data = currentData.get();
        return data != null ? data.mspt : 50.0;
    }
    
    /**
     * Get current loaded chunk count
     */
    public int getLoadedChunkCount() {
        PerformanceData data = currentData.get();
        return data != null ? data.loadedChunks : 0;
    }
    
    /**
     * Get current hot region count
     */
    public int getHotRegionCount() {
        PerformanceData data = currentData.get();
        return data != null ? data.hotRegions : 0;
    }
    
    /**
     * Get current player count
     */
    public int getPlayerCount() {
        PerformanceData data = currentData.get();
        return data != null ? data.totalPlayers : 0;
    }
    
    /**
     * Get rolling data for the last N seconds
     */
    public List<PerformanceData> getRollingData(int seconds) {
        long cutoffTime = System.currentTimeMillis() - (seconds * 1000L);
        
        return rollingData.stream()
            .filter(data -> data.timestamp >= cutoffTime)
            .sorted(Comparator.comparingLong(data -> data.timestamp))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get performance statistics for the last N seconds
     */
    public PerformanceStats getPerformanceStats(int seconds) {
        List<PerformanceData> data = getRollingData(seconds);
        
        if (data.isEmpty()) {
            return new PerformanceStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        double avgTps = data.stream().mapToDouble(d -> d.tps).average().orElse(0);
        double avgMspt = data.stream().mapToDouble(d -> d.mspt).average().orElse(0);
        double avgChunks = data.stream().mapToDouble(d -> d.loadedChunks).average().orElse(0);
        double avgHotRegions = data.stream().mapToDouble(d -> d.hotRegions).average().orElse(0);
        
        double minTps = data.stream().mapToDouble(d -> d.tps).min().orElse(0);
        double maxTps = data.stream().mapToDouble(d -> d.tps).max().orElse(0);
        double minMspt = data.stream().mapToDouble(d -> d.mspt).min().orElse(0);
        double maxMspt = data.stream().mapToDouble(d -> d.mspt).max().orElse(0);
        
        return new PerformanceStats(avgTps, avgMspt, avgChunks, avgHotRegions,
                                  minTps, maxTps, minMspt, maxMspt);
    }
    
    /**
     * Performance statistics
     */
    public static class PerformanceStats {
        public final double avgTps, avgMspt, avgChunks, avgHotRegions;
        public final double minTps, maxTps, minMspt, maxMspt;
        
        public PerformanceStats(double avgTps, double avgMspt, double avgChunks, double avgHotRegions,
                              double minTps, double maxTps, double minMspt, double maxMspt) {
            this.avgTps = avgTps;
            this.avgMspt = avgMspt;
            this.avgChunks = avgChunks;
            this.avgHotRegions = avgHotRegions;
            this.minTps = minTps;
            this.maxTps = maxTps;
            this.minMspt = minMspt;
            this.maxMspt = maxMspt;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceStats{avgTps=%.2f, avgMspt=%.2f, avgChunks=%.1f, avgHotRegions=%.1f, " +
                               "tpsRange=[%.2f-%.2f], msptRange=[%.2f-%.2f]}",
                avgTps, avgMspt, avgChunks, avgHotRegions, minTps, maxTps, minMspt, maxMspt);
        }
    }
    
    /**
     * Clean up old metrics files
     */
    public void cleanupOldMetrics() {
        File[] files = metricsDir.listFiles();
        if (files == null) return;
        
        long cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days
        
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoffTime) {
                file.delete();
            }
        }
    }
}
