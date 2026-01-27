/*
 * Timer class for latency measurement with HdrHistogram.
 * Based on Apache Cassandra's stress tool Timer implementation.
 * Tracks both service time (actual) and response time (coordinated omission corrected).
 */
package com.caladan.latency.util;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

public final class Timer {
    
    // In-progress measurement timing
    private long sampleStartNanos;
    private long expectedStartNanos;
    
    // Thread-safe histogram recorders
    private final Recorder serviceTimesRecorder;
    private final Recorder responseTimesRecorder;
    
    // Aggregate counters
    private int opCount;
    private long upToDateAsOf;
    private long lastSnap;
    
    // Communication with reporting thread
    private volatile CountDownLatch reportRequest;
    volatile TimingInterval report;
    private volatile TimingInterval finalReport;
    
    public Timer() {
        // 3 significant digits of precision
        this.serviceTimesRecorder = new Recorder(3);
        this.responseTimesRecorder = new Recorder(3);
        this.lastSnap = System.nanoTime();
    }
    
    /**
     * Set the expected start time for coordinated omission correction.
     * Must be called before start().
     */
    public void expectedStart(long expectedStartNanos) {
        this.expectedStartNanos = expectedStartNanos;
    }
    
    /**
     * Start timing a measurement.
     */
    public void start() {
        sampleStartNanos = System.nanoTime();
    }
    
    /**
     * Stop timing and record the measurement.
     */
    public void stop() {
        maybeReport();
        long now = System.nanoTime();
        
        // Service time: actual measurement duration
        serviceTimesRecorder.recordValue(now - sampleStartNanos);
        
        // Response time: time from intended start (corrects coordinated omission)
        responseTimesRecorder.recordValue(now - expectedStartNanos);
        
        opCount++;
        upToDateAsOf = now;
    }
    
    /**
     * Check if timer is still running.
     */
    public boolean running() {
        return finalReport == null;
    }
    
    /**
     * Build a timing report from current histogram data.
     */
    private TimingInterval buildReport() {
        Histogram responseTimesHistogram = responseTimesRecorder.getIntervalHistogram();
        Histogram serviceTimesHistogram = serviceTimesRecorder.getIntervalHistogram();
        
        TimingInterval report = new TimingInterval(
            lastSnap,
            upToDateAsOf,
            opCount,
            responseTimesHistogram,
            serviceTimesHistogram
        );
        
        // Reset counters
        opCount = 0;
        lastSnap = upToDateAsOf;
        
        return report;
    }
    
    /**
     * Check if a report has been requested and fulfill it.
     */
    private void maybeReport() {
        if (reportRequest != null) {
            synchronized (this) {
                report = buildReport();
                reportRequest.countDown();
                reportRequest = null;
            }
        }
    }
    
    /**
     * Request a timing report.
     */
    public synchronized void requestReport(CountDownLatch signal) {
        if (finalReport != null) {
            report = finalReport;
            finalReport = new TimingInterval(0);
            signal.countDown();
        } else {
            reportRequest = signal;
        }
    }
    
    /**
     * Close the timer and generate final report.
     */
    public synchronized void close() {
        if (reportRequest == null) {
            finalReport = buildReport();
        } else {
            finalReport = new TimingInterval(0);
            report = buildReport();
            reportRequest.countDown();
            reportRequest = null;
        }
    }
    
    /**
     * Get current snapshot of histograms without resetting.
     */
    public synchronized TimingInterval getSnapshot() {
        Histogram responseTimesHistogram = responseTimesRecorder.getIntervalHistogram();
        Histogram serviceTimesHistogram = serviceTimesRecorder.getIntervalHistogram();
        
        return new TimingInterval(
            lastSnap,
            System.nanoTime(),
            opCount,
            responseTimesHistogram,
            serviceTimesHistogram
        );
    }
    
    /**
     * Sleep for specified nanoseconds with high precision.
     */
    public static void sleepNs(long ns) {
        long now = System.nanoTime();
        long deadline = now + ns;
        do {
            final long delta = deadline - now;
            LockSupport.parkNanos(delta);
        } while ((now = System.nanoTime()) < deadline);
    }
}
