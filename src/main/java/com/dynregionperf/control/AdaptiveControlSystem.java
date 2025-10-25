package com.dynregionperf.control;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * PID-lite adaptive control system for MSPT/TPS management
 * Uses a simplified PID controller to adjust performance parameters
 */
public class AdaptiveControlSystem {
    private final double targetMspt;
    private final double minTps;
    
    // PID parameters
    private final double kp = 0.1; // Proportional gain
    private final double ki = 0.01; // Integral gain
    private final double kd = 0.05; // Derivative gain
    
    // Control history
    private final ConcurrentLinkedQueue<ControlSample> samples = new ConcurrentLinkedQueue<>();
    private final int maxSamples = 20; // 10 seconds at 2-second intervals
    
    // Control outputs
    private final AtomicReference<ControlOutput> currentOutput = new AtomicReference<>(new ControlOutput());
    
    // Performance tracking
    private double lastMspt = 0.0;
    private double lastTps = 20.0;
    private long lastUpdate = 0;
    
    public static class ControlSample {
        public final long timestamp;
        public final double mspt;
        public final double tps;
        public final int hotRegions;
        public final int loadedChunks;
        
        public ControlSample(long timestamp, double mspt, double tps, int hotRegions, int loadedChunks) {
            this.timestamp = timestamp;
            this.mspt = mspt;
            this.tps = tps;
            this.hotRegions = hotRegions;
            this.loadedChunks = loadedChunks;
        }
    }
    
    public static class ControlOutput {
        public final int viewDistanceAdjustment;
        public final int chunkBudgetAdjustment;
        public final double randomTickRatioAdjustment;
        public final boolean aggressiveUnload;
        
        public ControlOutput() {
            this(0, 0, 0.0, false);
        }
        
        public ControlOutput(int viewDistanceAdjustment, int chunkBudgetAdjustment, 
                           double randomTickRatioAdjustment, boolean aggressiveUnload) {
            this.viewDistanceAdjustment = viewDistanceAdjustment;
            this.chunkBudgetAdjustment = chunkBudgetAdjustment;
            this.randomTickRatioAdjustment = randomTickRatioAdjustment;
            this.aggressiveUnload = aggressiveUnload;
        }
        
        @Override
        public String toString() {
            return String.format("ControlOutput{viewDist=%d, chunkBudget=%d, randomTick=%.2f, aggressiveUnload=%s}",
                viewDistanceAdjustment, chunkBudgetAdjustment, randomTickRatioAdjustment, aggressiveUnload);
        }
    }
    
    public AdaptiveControlSystem(double targetMspt, double minTps) {
        this.targetMspt = targetMspt;
        this.minTps = minTps;
    }
    
    /**
     * Update control system with new performance data
     */
    public void update(double currentMspt, double currentTps, int hotRegions, int loadedChunks) {
        long currentTime = System.currentTimeMillis();
        
        // Only update every 2 seconds
        if (currentTime - lastUpdate < 2000) {
            return;
        }
        
        lastUpdate = currentTime;
        
        // Add new sample
        samples.offer(new ControlSample(currentTime, currentMspt, currentTps, hotRegions, loadedChunks));
        
        // Remove old samples
        while (samples.size() > maxSamples) {
            samples.poll();
        }
        
        // Calculate control output
        ControlOutput output = calculateControlOutput(currentMspt, currentTps, hotRegions, loadedChunks);
        currentOutput.set(output);
        
        lastMspt = currentMspt;
        lastTps = currentTps;
    }
    
    /**
     * Calculate control output using PID-lite algorithm
     */
    private ControlOutput calculateControlOutput(double currentMspt, double currentTps, 
                                              int hotRegions, int loadedChunks) {
        
        // Calculate error signals
        double msptError = currentMspt - targetMspt;
        double tpsError = minTps - currentTps;
        
        // Use the worse error (higher priority)
        double primaryError = Math.max(msptError / targetMspt, tpsError / minTps);
        
        // Calculate PID terms
        double proportional = primaryError;
        double integral = calculateIntegral();
        double derivative = calculateDerivative();
        
        // PID output
        double pidOutput = kp * proportional + ki * integral + kd * derivative;
        
        // Clamp output to reasonable range
        pidOutput = Math.max(-1.0, Math.min(1.0, pidOutput));
        
        // Convert PID output to control actions
        return convertToControlActions(pidOutput, currentMspt, currentTps, hotRegions, loadedChunks);
    }
    
    /**
     * Calculate integral term (sum of errors over time)
     */
    private double calculateIntegral() {
        if (samples.size() < 2) return 0.0;
        
        double integral = 0.0;
        ControlSample[] sampleArray = samples.toArray(new ControlSample[0]);
        
        for (int i = 1; i < sampleArray.length; i++) {
            ControlSample prev = sampleArray[i - 1];
            ControlSample curr = sampleArray[i];
            
            double msptError = (curr.mspt - targetMspt) / targetMspt;
            double tpsError = (minTps - curr.tps) / minTps;
            double error = Math.max(msptError, tpsError);
            
            long dt = curr.timestamp - prev.timestamp;
            integral += error * dt / 1000.0; // Convert to seconds
        }
        
        return integral;
    }
    
    /**
     * Calculate derivative term (rate of change of error)
     */
    private double calculateDerivative() {
        if (samples.size() < 2) return 0.0;
        
        ControlSample[] sampleArray = samples.toArray(new ControlSample[0]);
        ControlSample latest = sampleArray[sampleArray.length - 1];
        ControlSample previous = sampleArray[sampleArray.length - 2];
        
        double latestMsptError = (latest.mspt - targetMspt) / targetMspt;
        double latestTpsError = (minTps - latest.tps) / minTps;
        double latestError = Math.max(latestMsptError, latestTpsError);
        
        double prevMsptError = (previous.mspt - targetMspt) / targetMspt;
        double prevTpsError = (minTps - previous.tps) / minTps;
        double prevError = Math.max(prevMsptError, prevTpsError);
        
        long dt = latest.timestamp - previous.timestamp;
        return (latestError - prevError) / (dt / 1000.0); // Convert to seconds
    }
    
    /**
     * Convert PID output to specific control actions
     */
    private ControlOutput convertToControlActions(double pidOutput, double currentMspt, 
                                                 double currentTps, int hotRegions, int loadedChunks) {
        
        int viewDistanceAdjustment = 0;
        int chunkBudgetAdjustment = 0;
        double randomTickRatioAdjustment = 0.0;
        boolean aggressiveUnload = false;
        
        // Determine control actions based on PID output
        if (pidOutput > 0.3) {
            // Performance is poor, reduce load
            viewDistanceAdjustment = -1;
            chunkBudgetAdjustment = -2;
            randomTickRatioAdjustment = -0.1;
            
            if (pidOutput > 0.6) {
                aggressiveUnload = true;
                viewDistanceAdjustment = -2;
                chunkBudgetAdjustment = -4;
                randomTickRatioAdjustment = -0.2;
            }
        } else if (pidOutput < -0.3) {
            // Performance is good, can increase load
            viewDistanceAdjustment = 1;
            chunkBudgetAdjustment = 2;
            randomTickRatioAdjustment = 0.1;
            
            if (pidOutput < -0.6) {
                viewDistanceAdjustment = 2;
                chunkBudgetAdjustment = 4;
                randomTickRatioAdjustment = 0.2;
            }
        }
        
        // Additional heuristics based on absolute values
        if (currentMspt > targetMspt * 1.5) {
            // Very poor MSPT, emergency measures
            viewDistanceAdjustment = Math.min(viewDistanceAdjustment, -2);
            chunkBudgetAdjustment = Math.min(chunkBudgetAdjustment, -6);
            aggressiveUnload = true;
        }
        
        if (currentTps < minTps * 0.8) {
            // Very poor TPS, emergency measures
            viewDistanceAdjustment = Math.min(viewDistanceAdjustment, -2);
            chunkBudgetAdjustment = Math.min(chunkBudgetAdjustment, -6);
            aggressiveUnload = true;
        }
        
        // Clamp adjustments to reasonable ranges
        viewDistanceAdjustment = Math.max(-3, Math.min(3, viewDistanceAdjustment));
        chunkBudgetAdjustment = Math.max(-10, Math.min(10, chunkBudgetAdjustment));
        randomTickRatioAdjustment = Math.max(-0.5, Math.min(0.5, randomTickRatioAdjustment));
        
        return new ControlOutput(viewDistanceAdjustment, chunkBudgetAdjustment, 
                               randomTickRatioAdjustment, aggressiveUnload);
    }
    
    /**
     * Get current control output
     */
    public ControlOutput getCurrentOutput() {
        return currentOutput.get();
    }
    
    /**
     * Get performance statistics
     */
    public PerformanceStats getPerformanceStats() {
        if (samples.isEmpty()) {
            return new PerformanceStats(0, 0, 0, 0, 0, 0);
        }
        
        ControlSample[] sampleArray = samples.toArray(new ControlSample[0]);
        
        double avgMspt = 0, avgTps = 0;
        double minMspt = Double.MAX_VALUE, maxMspt = 0;
        double minTps = Double.MAX_VALUE, maxTps = 0;
        
        for (ControlSample sample : sampleArray) {
            avgMspt += sample.mspt;
            avgTps += sample.tps;
            minMspt = Math.min(minMspt, sample.mspt);
            maxMspt = Math.max(maxMspt, sample.mspt);
            minTps = Math.min(minTps, sample.tps);
            maxTps = Math.max(maxTps, sample.tps);
        }
        
        int count = sampleArray.length;
        avgMspt /= count;
        avgTps /= count;
        
        return new PerformanceStats(avgMspt, avgTps, minMspt, maxMspt, minTps, maxTps);
    }
    
    /**
     * Reset control system
     */
    public void reset() {
        samples.clear();
        currentOutput.set(new ControlOutput());
        lastMspt = 0.0;
        lastTps = 20.0;
        lastUpdate = 0;
    }
    
    /**
     * Performance statistics
     */
    public static class PerformanceStats {
        public final double avgMspt, avgTps;
        public final double minMspt, maxMspt;
        public final double minTps, maxTps;
        
        public PerformanceStats(double avgMspt, double avgTps, double minMspt, double maxMspt, double minTps, double maxTps) {
            this.avgMspt = avgMspt;
            this.avgTps = avgTps;
            this.minMspt = minMspt;
            this.maxMspt = maxMspt;
            this.minTps = minTps;
            this.maxTps = maxTps;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceStats{avgMspt=%.1f, avgTps=%.1f, msptRange=[%.1f-%.1f], tpsRange=[%.1f-%.1f]}",
                avgMspt, avgTps, minMspt, maxMspt, minTps, maxTps);
        }
    }
}
