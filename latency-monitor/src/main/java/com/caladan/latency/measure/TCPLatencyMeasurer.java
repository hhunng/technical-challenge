/*
 * TCP Latency Measurer - measures network latency via TCP connection.
 * Uses socket connection time (TCP handshake) as the latency measurement.
 */
package com.caladan.latency.measure;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TCPLatencyMeasurer {
    
    private final String targetHost;
    private final int targetPort;
    private final int timeoutMs;
    
    public TCPLatencyMeasurer(String targetHost, int targetPort, int timeoutMs) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.timeoutMs = timeoutMs;
    }
    
    public TCPLatencyMeasurer(String targetHost, int targetPort) {
        this(targetHost, targetPort, 5000); // 5 second default timeout
    }
    
    /**
     * Measure TCP connection latency.
     * 
     * @return MeasurementResult with success status and optional error message
     */
    public MeasurementResult measure() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
            return new MeasurementResult(true, null);
        } catch (IOException e) {
            return new MeasurementResult(false, e.getMessage());
        }
    }
    
    public String getTargetHost() {
        return targetHost;
    }
    
    public int getTargetPort() {
        return targetPort;
    }
    
    /**
     * Result of a measurement attempt.
     */
    public static class MeasurementResult {
        public final boolean success;
        public final String error;
        
        public MeasurementResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }
}
