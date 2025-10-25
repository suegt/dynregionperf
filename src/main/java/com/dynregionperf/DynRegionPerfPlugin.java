package com.dynregionperf;

import com.dynregionperf.budget.RegionBudgetManager;
import com.dynregionperf.budget.ViewDistanceAdapter;
import com.dynregionperf.cluster.DensityClusterer;
import com.dynregionperf.cluster.HotRegion;
import com.dynregionperf.commands.PerfCommand;
import com.dynregionperf.config.ConfigManager;
import com.dynregionperf.control.AdaptiveControlSystem;
import com.dynregionperf.control.EntityController;
import com.dynregionperf.integration.folia.FoliaIntegration;
import com.dynregionperf.integration.paper.PaperIntegration;
import com.dynregionperf.metrics.MetricsCollector;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for DynRegionPerf
 * Coordinates all performance optimization systems
 */
public class DynRegionPerfPlugin extends JavaPlugin implements Listener {
    
    // Core components
    private ConfigManager configManager;
    private DensityClusterer clusterer;
    private RegionBudgetManager budgetManager;
    private ViewDistanceAdapter viewDistanceAdapter;
    private EntityController entityController;
    private AdaptiveControlSystem controlSystem;
    private MetricsCollector metricsCollector;
    private PerfCommand perfCommand;
    
    // State tracking
    private List<HotRegion> hotRegions = new ArrayList<>();
    private Map<UUID, Long> lastPlayerMove = new ConcurrentHashMap<>();
    
    // Schedulers
    private BukkitTask densityScanTask;
    private BukkitTask adaptiveControlTask;
    private BukkitTask metricsTask;
    private BukkitTask cleanupTask;
    
    // Performance tracking
    private long lastDensityScan = 0;
    private long lastAdaptiveControl = 0;
    private long lastMetricsUpdate = 0;
    
    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        // Log server implementation
        getLogger().info("Server implementation: " + PaperIntegration.getServerImplementation());
        if (PaperIntegration.isPaperAvailable()) {
            getLogger().info("Paper integration enabled");
        }
        if (FoliaIntegration.isFoliaAvailable()) {
            getLogger().info("Folia integration enabled");
        }
        
        // Initialize core components
        initializeComponents();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        perfCommand = new PerfCommand(this, metricsCollector, controlSystem, 
                                   viewDistanceAdapter, entityController);
        getCommand("perf").setExecutor(perfCommand);
        getCommand("perf").setTabCompleter(perfCommand);
        
        // Start schedulers
        startSchedulers();
        
        // Initialize bStats
        initializeMetrics();
        
        getLogger().info("DynRegionPerf enabled successfully!");
        getLogger().info("Configuration recommendations:");
        getLogger().info(PaperIntegration.getConfigurationRecommendations());
        if (FoliaIntegration.isFoliaAvailable()) {
            getLogger().info(FoliaIntegration.getConfigurationRecommendations());
        }
    }
    
    @Override
    public void onDisable() {
        // Stop all schedulers
        stopSchedulers();
        
        // Reset view distances
        resetAllViewDistances();
        
        // Save final metrics
        if (metricsCollector != null) {
            metricsCollector.cleanupOldMetrics();
        }
        
        getLogger().info("DynRegionPerf disabled successfully!");
    }
    
    /**
     * Initialize all core components
     */
    private void initializeComponents() {
        // Initialize density clusterer
        clusterer = new DensityClusterer(
            configManager.getGridSize(),
            configManager.getHotThresholdPlayers()
        );
        
        // Initialize budget manager
        budgetManager = new RegionBudgetManager(
            configManager.getChunkBudgetPerHotRegionPerSec(),
            clusterer
        );
        
        // Initialize view distance adapter
        viewDistanceAdapter = new ViewDistanceAdapter(
            clusterer,
            configManager.getViewDistanceHotMin(),
            configManager.getViewDistanceHotMax(),
            configManager.getViewDistanceNormalMin(),
            configManager.getViewDistanceNormalMax(),
            configManager.getViewDistanceColdMin(),
            configManager.getViewDistanceColdMax()
        );
        
        // Initialize entity controller
        entityController = new EntityController(
            clusterer,
            configManager.getEntityCapColdMobs(),
            configManager.getEntityCapColdAnimals(),
            configManager.getEntityCapColdProjectiles(),
            configManager.getRandomTickScaleHot(),
            configManager.getRandomTickScaleCold()
        );
        
        // Initialize adaptive control system
        controlSystem = new AdaptiveControlSystem(
            configManager.getTargetMspt(),
            configManager.getMinTps()
        );
        
        // Initialize metrics collector
        metricsCollector = new MetricsCollector(this);
    }
    
    /**
     * Start all schedulers
     */
    private void startSchedulers() {
        
        // Density scan task (every 2 seconds)
        densityScanTask = new BukkitRunnable() {
            @Override
            public void run() {
                performDensityScan();
            }
        }.runTaskTimer(this, 80L, 80L); // 4 seconds for better FPS
        
        // Adaptive control task (every 5 seconds)
        adaptiveControlTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAdaptiveControl();
            }
        }.runTaskTimer(this, 100L, 100L); // 5 seconds
        
        // Metrics update task (every second)
        metricsTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateMetrics();
            }
        }.runTaskTimer(this, 20L, 20L); // 1 second
        
        // Cleanup task (every minute)
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimer(this, 1200L, 1200L); // 1 minute
    }
    
    /**
     * Stop all schedulers
     */
    private void stopSchedulers() {
        if (densityScanTask != null) {
            densityScanTask.cancel();
        }
        if (adaptiveControlTask != null) {
            adaptiveControlTask.cancel();
        }
        if (metricsTask != null) {
            metricsTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }
    
    /**
     * Perform density scan and clustering
     */
    private void performDensityScan() {
        long currentTime = System.currentTimeMillis();
        
        // Only scan every 4 seconds for better FPS
        if (currentTime - lastDensityScan < 4000) {
            return;
        }
        
        lastDensityScan = currentTime;
        
        try {
            // Get online players
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            
            if (players.isEmpty()) {
                hotRegions.clear();
                return;
            }
            
            // Create density map
            Map<DensityClusterer.GridCell, Integer> densityMap = clusterer.createDensityMap(players);
            
            // Cluster hot regions
            List<HotRegion> newHotRegions = clusterer.clusterHotRegions(densityMap);
            
            // Update hot regions
            hotRegions = newHotRegions;
            
            // Update budgets
            budgetManager.updateBudgets(hotRegions);
            
            // Update view distances
            viewDistanceAdapter.updateViewDistances(players, hotRegions);
            
            // Update entity limits
            entityController.updateEntityLimits(hotRegions, Bukkit.getWorlds());
            
            // Process chunk budgets for each region
            for (HotRegion region : hotRegions) {
                budgetManager.processRegionChunks(region, players);
            }
            
            // Apply entity controls
            entityController.applyEntityLimits(hotRegions, Bukkit.getWorlds());
            
            if (configManager.isDebugEnabled()) {
                getLogger().info("Density scan completed: " + hotRegions.size() + " hot regions found");
            }
            
        } catch (Exception e) {
            getLogger().severe("Error during density scan: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Perform adaptive control adjustments
     */
    private void performAdaptiveControl() {
        long currentTime = System.currentTimeMillis();
        
        // Only run every 5 seconds
        if (currentTime - lastAdaptiveControl < 5000) {
            return;
        }
        
        lastAdaptiveControl = currentTime;
        
        try {
            // Get current performance metrics
            double currentTps = metricsCollector.getCurrentTps();
            double currentMspt = metricsCollector.getCurrentMspt();
            int loadedChunks = metricsCollector.getLoadedChunkCount();
            int hotRegions = this.hotRegions.size();
            
            // Update control system
            controlSystem.update(currentMspt, currentTps, hotRegions, loadedChunks);
            
            // Get control output
            AdaptiveControlSystem.ControlOutput output = controlSystem.getCurrentOutput();
            
            // Apply control adjustments
            applyControlAdjustments(output);
            
            if (configManager.isDebugEnabled()) {
                getLogger().info("Adaptive control applied: " + output);
            }
            
        } catch (Exception e) {
            getLogger().severe("Error during adaptive control: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Apply control adjustments
     */
    private void applyControlAdjustments(AdaptiveControlSystem.ControlOutput output) {
        // Update view distance adapter performance multiplier
        viewDistanceAdapter.updatePerformanceMultiplier(
            metricsCollector.getCurrentMspt(),
            configManager.getTargetMspt(),
            metricsCollector.getCurrentTps(),
            configManager.getMinTps()
        );
        
        // Apply aggressive unload if needed
        if (output.aggressiveUnload) {
            budgetManager.aggressiveUnloadColdRegions(new ArrayList<>(Bukkit.getOnlinePlayers()));
        }
    }
    
    /**
     * Update metrics
     */
    private void updateMetrics() {
        long currentTime = System.currentTimeMillis();
        
        // Only update every second
        if (currentTime - lastMetricsUpdate < 1000) {
            return;
        }
        
        lastMetricsUpdate = currentTime;
        
        try {
            metricsCollector.updateMetrics(hotRegions);
        } catch (Exception e) {
            getLogger().severe("Error updating metrics: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Perform cleanup tasks
     */
    private void performCleanup() {
        try {
            // Clean up expired boosts
            if (perfCommand != null) {
                perfCommand.cleanupExpiredBoosts();
            }
            
            // Clean up old metrics
            metricsCollector.cleanupOldMetrics();
            
            // Clean up player movement data
            long currentTime = System.currentTimeMillis();
            lastPlayerMove.entrySet().removeIf(entry -> currentTime - entry.getValue() > 300000); // 5 minutes
            
        } catch (Exception e) {
            getLogger().severe("Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reset all view distances to default
     */
    private void resetAllViewDistances() {
        try {
            viewDistanceAdapter.resetAllViewDistances(new ArrayList<>(Bukkit.getOnlinePlayers()));
        } catch (Exception e) {
            getLogger().severe("Error resetting view distances: " + e.getMessage());
        }
    }
    
    /**
     * Initialize bStats metrics
     */
    private void initializeMetrics() {
        try {
            // bStats initialization disabled due to class loading issues
            // This can be re-enabled once the dependency is properly configured
            getLogger().info("bStats metrics disabled (optional feature)");
            
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats: " + e.getMessage());
        }
    }
    
    /**
     * Reload configuration
     */
    public void reloadConfig() {
        configManager.reloadConfig();
        
        // Reinitialize components with new configuration
        initializeComponents();
        
        getLogger().info("Configuration reloaded successfully!");
    }
    
    // Event handlers
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Update player movement data
        budgetManager.updatePlayerMovement(player);
        
        // Track last move time
        lastPlayerMove.put(playerId, System.currentTimeMillis());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        
        // Remove player movement data
        budgetManager.removePlayerMovement(playerId);
        lastPlayerMove.remove(playerId);
        
        // Remove any active boosts
        if (perfCommand != null) {
            perfCommand.removeBoost(playerId);
        }
    }
    
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        // Initialize world-specific settings
        World world = event.getWorld();
        
        if (configManager.isDebugEnabled()) {
            getLogger().info("World initialized: " + world.getName());
        }
    }
    
    // Getters for other components
    
    public List<HotRegion> getHotRegions() {
        return new ArrayList<>(hotRegions);
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DensityClusterer getClusterer() {
        return clusterer;
    }
    
    public RegionBudgetManager getBudgetManager() {
        return budgetManager;
    }
    
    public ViewDistanceAdapter getViewDistanceAdapter() {
        return viewDistanceAdapter;
    }
    
    public EntityController getEntityController() {
        return entityController;
    }
    
    public AdaptiveControlSystem getControlSystem() {
        return controlSystem;
    }
    
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
}
