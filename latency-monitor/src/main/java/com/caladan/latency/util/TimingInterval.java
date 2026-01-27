/*
 * TimingInterval represents metrics collected over a time interval.
 * Based on Apache Cassandra's stress tool TimingInterval implementation.
 */
package com.caladan.latency.util;

import org.HdrHistogram.Histogram;

public final class TimingInterval {
    
    // Time bounds (nanoseconds)
    private final long start;
    private final long end;
    
    // Operation count
    public final long operationCount;
    
    // Histograms
    public final Histogram responseTimesHistogram;
    public final Histogram serviceTimesHistogram;
    
    /**
     * Create an empty timing interval.
     */
    public TimingInterval(long time) {
        this.start = time;
        this.end = time;
        this.operationCount = 0;
        this.responseTimesHistogram = new Histogram(3);
        this.serviceTimesHistogram = new Histogram(3);
    }
    
    /**
     * Create a timing interval with data.
     */
    public TimingInterval(long start, long end, long operationCount,
                          Histogram responseTimesHistogram, Histogram serviceTimesHistogram) {
        this.start = start;
        this.end = Math.max(end, start);
        this.operationCount = operationCount;
        this.responseTimesHistogram = responseTimesHistogram;
        this.serviceTimesHistogram = serviceTimesHistogram;
    }
    
    // === Rate calculations ===
    
    /**
     * Operations per second.
     */
    public double opRate() {
        long duration = end - start;
        if (duration <= 0) return 0;
        return operationCount / (duration * 0.000000001d);
    }
    
    /**
     * Run time in milliseconds.
     */
    public long runTimeMs() {
        return (end - start) / 1_000_000;
    }
    
    // === Response time metrics (coordinated omission corrected) ===
    
    public double responseMeanLatencyUs() {
        return responseTimesHistogram.getMean() / 1000.0;
    }
    
    public double responseMedianLatencyUs() {
        return responseTimesHistogram.getValueAtPercentile(50.0) / 1000.0;
    }
    
    public double responsePercentileLatencyUs(double percentile) {
        return responseTimesHistogram.getValueAtPercentile(percentile) / 1000.0;
    }
    
    public double responseMaxLatencyUs() {
        return responseTimesHistogram.getMaxValue() / 1000.0;
    }
    
    public double responseMinLatencyUs() {
        return responseTimesHistogram.getMinValue() / 1000.0;
    }
    
    // === Service time metrics (actual measurement duration) ===
    
    public double serviceMeanLatencyUs() {
        return serviceTimesHistogram.getMean() / 1000.0;
    }
    
    public double serviceMedianLatencyUs() {
        return serviceTimesHistogram.getValueAtPercentile(50.0) / 1000.0;
    }
    
    public double servicePercentileLatencyUs(double percentile) {
        return serviceTimesHistogram.getValueAtPercentile(percentile) / 1000.0;
    }
    
    public double serviceMaxLatencyUs() {
        return serviceTimesHistogram.getMaxValue() / 1000.0;
    }
    
    public double serviceMinLatencyUs() {
        return serviceTimesHistogram.getMinValue() / 1000.0;
    }
    
    // === Accessors ===
    
    public long startNanos() {
        return start;
    }
    
    public long endNanos() {
        return end;
    }
    
    public Histogram getResponseTimesHistogram() {
        return responseTimesHistogram;
    }
    
    public Histogram getServiceTimesHistogram() {
        return serviceTimesHistogram;
    }
}
