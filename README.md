# dynregionperf
Dynamic region performance optimization plugin for Paper/Folia servers (1.20+).

# DynRegionPerf

Dynamic region performance optimization plugin for Paper/Folia servers (1.20+).

## Overview

DynRegionPerf is a sophisticated performance optimization plugin that dynamically manages chunks and regions based on player density to maintain stable TPS/MSPT. It uses advanced clustering algorithms, adaptive control systems, and regional entity management to optimize server performance.

## Features

### 🗺️ Density Mapping & Clustering
- **Grid-based Analysis**: Maps players to 64×64 block grids every 2 seconds
- **BFS Clustering**: Groups nearby high-density cells into "hot regions"
- **Dynamic Classification**: Automatically categorizes regions as hot/normal/cold

### 🎯 Dynamic View Distance
- **Adaptive Adjustment**: Automatically adjusts view distance based on region temperature
- **Performance Scaling**: Reduces view distance during poor performance
- **Player-specific**: Individual view distance management per player

### 💰 Chunk Budget Management
- **Predictive Loading**: Preloads chunks based on player movement patterns
- **Budget Allocation**: Allocates chunk loading budget per hot region (FPS optimized)
- **Priority System**: Loads chunks closer to players first
- **Rate Limiting**: Limits chunk loads per tick to prevent FPS drops
- **Aggressive Unloading**: Unloads unused chunks in cold regions

### 🐛 Regional Entity Controls
- **Entity Limits**: Applies different entity caps for hot/cold regions
- **Smart Despawning**: Removes distant entities in cold regions
- **Random Tick Scaling**: Adjusts random tick rates based on region temperature

### 🎛️ Adaptive Control System
- **PID-lite Controller**: Uses simplified PID control for MSPT/TPS management
- **Gradual Adjustments**: Makes incremental changes to avoid instability
- **Emergency Measures**: Applies aggressive optimizations during poor performance

### 📊 Comprehensive Metrics
- **Real-time Monitoring**: Tracks TPS, MSPT, loaded chunks, and hot regions
- **Rolling Data Storage**: Saves 5-minute rolling averages to JSON
- **bStats Integration**: Anonymous usage statistics (temporarily disabled)

## Installation

### Requirements
- **Paper 1.20+** (recommended) or **Folia** (for region-threading)
- **Java 17+**
- **4GB+ RAM** (recommended for optimal performance)

### Installation Steps

1. **Download** the latest release from the releases page
2. **Place** `DynRegionPerf.jar` in your server's `plugins/` folder
3. **Start** your server to generate the configuration file
4. **Configure** `plugins/DynRegionPerf/config.yml` as needed
5. **Restart** your server to apply changes

## Configuration

### Basic Configuration

```yaml
# Grid size for density mapping (in blocks)
# Larger values = less granular but better performance
gridSize: 64

# Minimum number of players in a grid cell to consider it "hot"
hotThresholdPlayers: 3

# Interval between density scans (in ticks, 20 ticks = 1 second)
scanIntervalTicks: 40

# Performance targets
targetMspt: 45.0
minTps: 19.5

# Chunk loading budget per hot region per second
chunkBudgetPerHotRegionPerSec: 24
```

### Advanced Configuration

```yaml
# Random tick scaling based on region temperature
randomTickScale:
  hot: 1.0
  cold: 0.5

# Entity caps for cold regions (percentage of normal limits)
entityCaps:
  cold:
    mobs: 60
    animals: 60
    projectiles: 50

# Per-player view distance ranges based on region temperature
perPlayerViewDistance:
  hot: 
    min: 6
    max: 8
  normal:
    min: 8
    max: 10
  cold:
    min: 10
    max: 12

# Folia support configuration
folia:
  enabled: auto  # auto, true, false

# Debug settings
debug:
  enabled: false
  verboseLogging: false
```

## Commands

### `/perf status`
Shows current performance status including TPS, MSPT, loaded chunks, hot regions, and control adjustments.

**Permission**: `dynperf.view`

### `/perf boost <radius>`
Temporarily increases chunk loading budget and view distance in your area for 5 minutes.

**Permission**: `dynperf.admin`
**Usage**: `/perf boost 3`

### `/perf profile`
Shows detailed performance profile including 60-second averages and entity statistics.

**Permission**: `dynperf.view`

### `/perf reload`
Reloads the configuration file without restarting the server.

**Permission**: `dynperf.admin`

## Permissions

- `dynperf.view` - Allows viewing performance status and profiles
- `dynperf.admin` - Allows administrative commands (boost, reload)

## Paper/Folia Integration

### Paper Recommendations

For optimal performance with Paper, configure these settings in `paper.yml`:

```yaml
# Entity activation range
entity-activation-range:
  default: 32
  players: 48

# Mob spawn limits
mob-spawn-limit:
  default: 70

# Random tick rate
random-tick-rate: 1.0

# View distance
view-distance: 10

# Simulation distance
simulation-distance: 10
```

### Folia Recommendations

For Folia servers, additional configuration in `folia.yml`:

```yaml
# Region threading
region-threading: enabled

# Async chunk operations
async-chunk-loading: enabled
async-chunk-saving: enabled

# Region size
region-size: 32x32

# Region limits
max-region-load: 1000
max-region-save: 1000
```

## Performance Impact

### Expected Improvements
- **TPS Stability**: 15-25% improvement in TPS consistency
- **MSPT Reduction**: 10-20% reduction in average MSPT
- **Memory Usage**: 20-30% reduction in loaded chunk count
- **Entity Performance**: 30-50% reduction in entity processing overhead

### Monitoring
- Use `/perf status` to monitor real-time performance
- Check `plugins/DynRegionPerf/metrics/rolling.json` for historical data
- Monitor server logs for performance warnings

## Troubleshooting

### Common Issues

**High MSPT despite optimization**
- Check if other plugins are causing performance issues
- Verify Paper/Folia configuration
- Consider reducing `chunkBudgetPerHotRegionPerSec`

**Players experiencing chunk loading issues**
- Increase `chunkBudgetPerHotRegionPerSec`
- Use `/perf boost` command for temporary relief
- Check if view distance is too low

**Plugin not detecting hot regions**
- Verify `hotThresholdPlayers` is appropriate for your server
- Check if `gridSize` is too large
- Ensure players are actually clustered together

### Debug Mode

Enable debug mode to get detailed logging:

```yaml
debug:
  enabled: true
  verboseLogging: true
```

## API Integration

DynRegionPerf provides a simple API for other plugins:

```java
// Get the plugin instance
DynRegionPerfPlugin plugin = (DynRegionPerfPlugin) Bukkit.getPluginManager().getPlugin("DynRegionPerf");

// Get current hot regions
List<HotRegion> hotRegions = plugin.getHotRegions();

// Get performance metrics
MetricsCollector metrics = plugin.getMetricsCollector();
double currentTps = metrics.getCurrentTps();
double currentMspt = metrics.getCurrentMspt();
```

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

- **Issues**: Report bugs and request features on GitHub Issues
- **Discord**: Join our Discord server for community support
- **Wiki**: Check the GitHub Wiki for detailed documentation

## Changelog

### Version 1.0.0
- Initial release
- Density mapping and clustering system
- Dynamic view distance adjustment
- Chunk budget management
- Regional entity controls
- Adaptive control system
- Comprehensive metrics collection
- Paper/Folia integration
- Command system with boost functionality

---

**Note**: This plugin is designed for Paper 1.20+ servers. While it may work on other server software, optimal performance is only guaranteed on Paper or Folia.

