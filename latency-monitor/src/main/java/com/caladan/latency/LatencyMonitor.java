/*
 * LatencyMonitor - Main orchestration class for network latency monitoring.
 * Runs periodic measurements with coordinated omission correction.
 */
package com.caladan.latency;

import com.caladan.latency.measure.TCPLatencyMeasurer;
import com.caladan.latency.util.Pacer;
import com.caladan.latency.util.Timer;
import com.caladan.latency.util.TimingInterval;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LatencyMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(LatencyMonitor.class);
    
    private final TCPLatencyMeasurer measurer;
    private final double ratePerSecond;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread monitorThread;
    
    // Cumulative histograms for all-time statistics
    private final Histogram cumulativeServiceTimes;
    private final Histogram cumulativeResponseTimes;
    
    // Counters
    private final AtomicLong totalMeasurements = new AtomicLong(0);
    private final AtomicLong successfulMeasurements = new AtomicLong(0);
    private final AtomicLong failedMeasurements = new AtomicLong(0);
    
    // Start time
    private long startTimeMs;
    
    public LatencyMonitor(String targetHost, int targetPort, double ratePerSecond) {
        this.measurer = new TCPLatencyMeasurer(targetHost, targetPort);
        this.ratePerSecond = ratePerSecond;
        
        // High dynamic range histograms: 1ns to 1 hour, 3 significant digits
        this.cumulativeServiceTimes = new Histogram(1, 3_600_000_000_000L, 3);
        this.cumulativeResponseTimes = new Histogram(1, 3_600_000_000_000L, 3);
    }
    
    /**
     * Start the monitoring thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTimeMs = System.currentTimeMillis();
            monitorThread = new Thread(this::measurementLoop, "LatencyMonitor");
            monitorThread.setDaemon(true);
            monitorThread.start();
            logger.info("Started latency monitoring to {}:{} at {} ops/sec",
                measurer.getTargetHost(), measurer.getTargetPort(), ratePerSecond);
        }
    }
    
    /**
     * Stop the monitoring thread.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (monitorThread != null) {
                monitorThread.interrupt();
                try {
                    monitorThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("Stopped latency monitoring");
        }
    }
    
    /**
     * Main measurement loop.
     */
    private void measurementLoop() {
        Pacer pacer = new Pacer(ratePerSecond);
        pacer.setInitialStartTime(System.nanoTime());
        Timer timer = new Timer();
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Get intended start time (for coordinated omission correction)
                long expectedStart = pacer.expectedStartTimeNsec();
                timer.expectedStart(expectedStart);
                
                // Wait until scheduled time
                pacer.acquire(1);
                
                // Start timing
                timer.start();
                
                // Perform measurement
                TCPLatencyMeasurer.MeasurementResult result = measurer.measure();
                
                // Stop timing
                timer.stop();
                
                // Update counters
                totalMeasurements.incrementAndGet();
                if (result.success) {
                    successfulMeasurements.incrementAndGet();
                } else {
                    failedMeasurements.incrementAndGet();
                    logger.debug("Measurement failed: {}", result.error);
                }
                
                // Get snapshot and add to cumulative histograms
                TimingInterval snapshot = timer.getSnapshot();
                synchronized (this) {
                    cumulativeServiceTimes.add(snapshot.getServiceTimesHistogram());
                    cumulativeResponseTimes.add(snapshot.getResponseTimesHistogram());
                }
                
            } catch (Exception e) {
                if (running.get()) {
                    logger.error("Error in measurement loop", e);
                }
            }
        }
        
        timer.close();
    }
    
    /**
     * Get current statistics.
     */
    public synchronized Statistics getStatistics() {
        return new Statistics(
            measurer.getTargetHost(),
            measurer.getTargetPort(),
            ratePerSecond,
            totalMeasurements.get(),
            successfulMeasurements.get(),
            failedMeasurements.get(),
            System.currentTimeMillis() - startTimeMs,
            running.get(),
            // Service times (actual measurement duration)
            cumulativeServiceTimes.getMean() / 1000.0,           // ns to us
            cumulativeServiceTimes.getValueAtPercentile(50) / 1000.0,
            cumulativeServiceTimes.getValueAtPercentile(95) / 1000.0,
            cumulativeServiceTimes.getValueAtPercentile(99) / 1000.0,
            cumulativeServiceTimes.getValueAtPercentile(99.9) / 1000.0,
            cumulativeServiceTimes.getMaxValue() / 1000.0,
            cumulativeServiceTimes.getMinNonZeroValue() / 1000.0,
            // Response times (coordinated omission corrected)
            cumulativeResponseTimes.getMean() / 1000.0,
            cumulativeResponseTimes.getValueAtPercentile(50) / 1000.0,
            cumulativeResponseTimes.getValueAtPercentile(95) / 1000.0,
            cumulativeResponseTimes.getValueAtPercentile(99) / 1000.0,
            cumulativeResponseTimes.getValueAtPercentile(99.9) / 1000.0,
            cumulativeResponseTimes.getMaxValue() / 1000.0,
            cumulativeResponseTimes.getMinNonZeroValue() / 1000.0
        );
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Statistics snapshot.
     */
    public static class Statistics {
        public final String targetHost;
        public final int targetPort;
        public final double ratePerSecond;
        public final long totalMeasurements;
        public final long successfulMeasurements;
        public final long failedMeasurements;
        public final long uptimeMs;
        public final boolean running;
        
        // Service times (microseconds)
        public final double serviceMeanUs;
        public final double serviceP50Us;
        public final double serviceP95Us;
        public final double serviceP99Us;
        public final double serviceP999Us;
        public final double serviceMaxUs;
        public final double serviceMinUs;
        
        // Response times - coordinated omission corrected (microseconds)
        public final double responseMeanUs;
        public final double responseP50Us;
        public final double responseP95Us;
        public final double responseP99Us;
        public final double responseP999Us;
        public final double responseMaxUs;
        public final double responseMinUs;
        
        public Statistics(String targetHost, int targetPort, double ratePerSecond,
                         long totalMeasurements, long successfulMeasurements, long failedMeasurements,
                         long uptimeMs, boolean running,
                         double serviceMeanUs, double serviceP50Us, double serviceP95Us,
                         double serviceP99Us, double serviceP999Us, double serviceMaxUs, double serviceMinUs,
                         double responseMeanUs, double responseP50Us, double responseP95Us,
                         double responseP99Us, double responseP999Us, double responseMaxUs, double responseMinUs) {
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.ratePerSecond = ratePerSecond;
            this.totalMeasurements = totalMeasurements;
            this.successfulMeasurements = successfulMeasurements;
            this.failedMeasurements = failedMeasurements;
            this.uptimeMs = uptimeMs;
            this.running = running;
            this.serviceMeanUs = serviceMeanUs;
            this.serviceP50Us = serviceP50Us;
            this.serviceP95Us = serviceP95Us;
            this.serviceP99Us = serviceP99Us;
            this.serviceP999Us = serviceP999Us;
            this.serviceMaxUs = serviceMaxUs;
            this.serviceMinUs = serviceMinUs;
            this.responseMeanUs = responseMeanUs;
            this.responseP50Us = responseP50Us;
            this.responseP95Us = responseP95Us;
            this.responseP99Us = responseP99Us;
            this.responseP999Us = responseP999Us;
            this.responseMaxUs = responseMaxUs;
            this.responseMinUs = responseMinUs;
        }
        
        public double errorRate() {
            if (totalMeasurements == 0) return 0;
            return (failedMeasurements * 100.0) / totalMeasurements;
        }
    }
}
