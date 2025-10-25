package com.dynregionperf.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Configuration manager for DynRegionPerf plugin
 * Handles loading and validation of configuration values
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<String, Object> defaults = new HashMap<>();
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDefaults();
    }
    
    /**
     * Setup default configuration values
     */
    private void setupDefaults() {
        defaults.put("gridSize", 64);
        defaults.put("hotThresholdPlayers", 3);
        defaults.put("scanIntervalTicks", 80);
        defaults.put("targetMspt", 45.0);
        defaults.put("minTps", 19.5);
        defaults.put("chunkBudgetPerHotRegionPerSec", 12);
        defaults.put("randomTickScale.hot", 1.0);
        defaults.put("randomTickScale.cold", 0.5);
        defaults.put("entityCaps.cold.mobs", 60);
        defaults.put("entityCaps.cold.animals", 60);
        defaults.put("entityCaps.cold.projectiles", 50);
        defaults.put("perPlayerViewDistance.hot.min", 6);
        defaults.put("perPlayerViewDistance.hot.max", 8);
        defaults.put("perPlayerViewDistance.normal.min", 8);
        defaults.put("perPlayerViewDistance.normal.max", 10);
        defaults.put("perPlayerViewDistance.cold.min", 10);
        defaults.put("perPlayerViewDistance.cold.max", 12);
        defaults.put("folia.enabled", "auto");
        defaults.put("debug.enabled", false);
        defaults.put("debug.verboseLogging", false);
    }
    
    /**
     * Load configuration from file
     */
    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Add missing default values
        boolean needsSave = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                needsSave = true;
            }
        }
        
        if (needsSave) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config.yml", e);
            }
        }
        
        validateConfig();
    }
    
    /**
     * Validate configuration values
     */
    private void validateConfig() {
        // Validate grid size
        int gridSize = getGridSize();
        if (gridSize < 16 || gridSize > 256) {
            plugin.getLogger().warning("Invalid gridSize: " + gridSize + ". Using default: 64");
            config.set("gridSize", 64);
        }
        
        // Validate hot threshold
        int hotThreshold = getHotThresholdPlayers();
        if (hotThreshold < 1) {
            plugin.getLogger().warning("Invalid hotThresholdPlayers: " + hotThreshold + ". Using default: 3");
            config.set("hotThresholdPlayers", 3);
        }
        
        // Validate scan interval
        int scanInterval = getScanIntervalTicks();
        if (scanInterval < 20 || scanInterval > 200) {
            plugin.getLogger().warning("Invalid scanIntervalTicks: " + scanInterval + ". Using default: 40");
            config.set("scanIntervalTicks", 40);
        }
        
        // Validate performance targets
        double targetMspt = getTargetMspt();
        if (targetMspt < 10.0 || targetMspt > 100.0) {
            plugin.getLogger().warning("Invalid targetMspt: " + targetMspt + ". Using default: 45.0");
            config.set("targetMspt", 45.0);
        }
        
        double minTps = getMinTps();
        if (minTps < 10.0 || minTps > 20.0) {
            plugin.getLogger().warning("Invalid minTps: " + minTps + ". Using default: 19.5");
            config.set("minTps", 19.5);
        }
    }
    
    // Configuration getters
    public int getGridSize() {
        return config.getInt("gridSize", 64);
    }
    
    public int getHotThresholdPlayers() {
        return config.getInt("hotThresholdPlayers", 3);
    }
    
    public int getScanIntervalTicks() {
        return config.getInt("scanIntervalTicks", 40);
    }
    
    public double getTargetMspt() {
        return config.getDouble("targetMspt", 45.0);
    }
    
    public double getMinTps() {
        return config.getDouble("minTps", 19.5);
    }
    
    public int getChunkBudgetPerHotRegionPerSec() {
        return config.getInt("chunkBudgetPerHotRegionPerSec", 24);
    }
    
    public double getRandomTickScaleHot() {
        return config.getDouble("randomTickScale.hot", 1.0);
    }
    
    public double getRandomTickScaleCold() {
        return config.getDouble("randomTickScale.cold", 0.5);
    }
    
    public int getEntityCapColdMobs() {
        return config.getInt("entityCaps.cold.mobs", 60);
    }
    
    public int getEntityCapColdAnimals() {
        return config.getInt("entityCaps.cold.animals", 60);
    }
    
    public int getEntityCapColdProjectiles() {
        return config.getInt("entityCaps.cold.projectiles", 50);
    }
    
    public int getViewDistanceHotMin() {
        return config.getInt("perPlayerViewDistance.hot.min", 6);
    }
    
    public int getViewDistanceHotMax() {
        return config.getInt("perPlayerViewDistance.hot.max", 8);
    }
    
    public int getViewDistanceNormalMin() {
        return config.getInt("perPlayerViewDistance.normal.min", 8);
    }
    
    public int getViewDistanceNormalMax() {
        return config.getInt("perPlayerViewDistance.normal.max", 10);
    }
    
    public int getViewDistanceColdMin() {
        return config.getInt("perPlayerViewDistance.cold.min", 10);
    }
    
    public int getViewDistanceColdMax() {
        return config.getInt("perPlayerViewDistance.cold.max", 12);
    }
    
    public String getFoliaEnabled() {
        return config.getString("folia.enabled", "auto");
    }
    
    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }
    
    public boolean isVerboseLogging() {
        return config.getBoolean("debug.verboseLogging", false);
    }
    
    /**
     * Reload configuration from file
     */
    public void reloadConfig() {
        loadConfig();
    }
}
