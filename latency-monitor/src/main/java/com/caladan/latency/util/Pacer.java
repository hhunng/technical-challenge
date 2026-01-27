/*
 * Pacer for rate limiting with coordinated omission correction.
 * Based on Apache Cassandra's stress tool Pacer implementation.
 * Pre-calculates intended start times to enable accurate latency measurement.
 */
package com.caladan.latency.util;

public class Pacer {
    
    private long initialStartTime;
    private double throughputInUnitsPerNsec;
    private long unitsCompleted;
    
    // Catch-up state
    private boolean caughtUp = true;
    private long catchUpStartTime;
    private long unitsCompletedAtCatchUpStart;
    private double catchUpThroughputInUnitsPerNsec;
    private double catchUpRateMultiple;
    
    public Pacer(double unitsPerSec) {
        this(unitsPerSec, 3.0); // Default: catch up at 3x normal rate
    }
    
    public Pacer(double unitsPerSec, double catchUpRateMultiple) {
        setThroughput(unitsPerSec);
        setCatchupRateMultiple(catchUpRateMultiple);
        this.initialStartTime = System.nanoTime();
    }
    
    public void setInitialStartTime(long initialStartTime) {
        this.initialStartTime = initialStartTime;
    }
    
    public void setThroughput(double unitsPerSec) {
        this.throughputInUnitsPerNsec = unitsPerSec / 1_000_000_000.0;
        this.catchUpThroughputInUnitsPerNsec = catchUpRateMultiple * throughputInUnitsPerNsec;
    }
    
    public void setCatchupRateMultiple(double multiple) {
        this.catchUpRateMultiple = multiple;
        this.catchUpThroughputInUnitsPerNsec = catchUpRateMultiple * throughputInUnitsPerNsec;
    }
    
    /**
     * Get the expected (intended) start time for the next operation.
     * This is the key for coordinated omission correction.
     */
    public long expectedStartTimeNsec() {
        return initialStartTime + (long)(unitsCompleted / throughputInUnitsPerNsec);
    }
    
    /**
     * Calculate nanoseconds until next scheduled send.
     * Returns 0 if should send immediately.
     */
    public long nsecToNextSend() {
        long now = System.nanoTime();
        long nextStartTime = expectedStartTimeNsec();
        
        boolean sendNow = true;
        
        if (nextStartTime > now) {
            // We are on pace - don't send yet
            caughtUp = true;
            sendNow = false;
        } else {
            // We are behind
            if (caughtUp) {
                // First fall-behind since last caught up
                caughtUp = false;
                catchUpStartTime = now;
                unitsCompletedAtCatchUpStart = unitsCompleted;
            }
            
            // Calculate catch-up timing
            long unitsCompletedSinceCatchUpStart = unitsCompleted - unitsCompletedAtCatchUpStart;
            nextStartTime = catchUpStartTime + 
                (long)(unitsCompletedSinceCatchUpStart / catchUpThroughputInUnitsPerNsec);
            
            if (nextStartTime > now) {
                sendNow = false;
            }
        }
        
        return sendNow ? 0 : (nextStartTime - now);
    }
    
    /**
     * Wait until next scheduled time and increment units.
     */
    public void acquire(long unitCount) {
        long nsecToNextSend = nsecToNextSend();
        if (nsecToNextSend > 0) {
            Timer.sleepNs(nsecToNextSend);
        }
        unitsCompleted += unitCount;
    }
    
    /**
     * Get units completed so far.
     */
    public long getUnitsCompleted() {
        return unitsCompleted;
    }
}
