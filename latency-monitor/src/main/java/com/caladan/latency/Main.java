/*
 * Main entry point for the Network Latency Monitor application.
 */
package com.caladan.latency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        // Parse configuration from environment variables or command line
        String targetHost = getConfig("TARGET_HOST", args, 0, null);
        int targetPort = Integer.parseInt(getConfig("TARGET_PORT", args, 1, "22"));
        double ratePerSecond = Double.parseDouble(getConfig("RATE_PER_SECOND", args, 2, "10"));
        int serverPort = Integer.parseInt(getConfig("SERVER_PORT", args, 3, "8080"));
        
        // Validate required config
        if (targetHost == null || targetHost.isEmpty()) {
            System.err.println("Error: TARGET_HOST is required");
            System.err.println();
            printUsage();
            System.exit(1);
        }
        
        logger.info("=== Network Latency Monitor ===");
        logger.info("Target: {}:{}", targetHost, targetPort);
        logger.info("Rate: {} probes/second", ratePerSecond);
        logger.info("Server port: {}", serverPort);
        
        // Create and start the latency monitor
        LatencyMonitor monitor = new LatencyMonitor(targetHost, targetPort, ratePerSecond);
        monitor.start();
        
        // Create and start the metrics server
        MetricsServer server = new MetricsServer(serverPort, monitor);
        try {
            server.start();
            
            logger.info("=================================");
            logger.info("Dashboard: http://localhost:{}/", serverPort);
            logger.info("Metrics:   http://localhost:{}/metrics", serverPort);
            logger.info("Health:    http://localhost:{}/health", serverPort);
            logger.info("=================================");
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                monitor.stop();
                try {
                    server.stop();
                } catch (Exception e) {
                    logger.error("Error stopping server", e);
                }
            }));
            
            // Wait for server to finish
            server.join();
            
        } catch (Exception e) {
            logger.error("Error starting server", e);
            monitor.stop();
            System.exit(1);
        }
    }
    
    /**
     * Get configuration value from environment variable or command line argument.
     */
    private static String getConfig(String envName, String[] args, int argIndex, String defaultValue) {
        // Check environment variable first
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        
        // Check command line argument
        if (args != null && args.length > argIndex) {
            String arg = args[argIndex];
            // Handle --key=value format
            if (arg.startsWith("--")) {
                int eqIndex = arg.indexOf('=');
                if (eqIndex > 0) {
                    return arg.substring(eqIndex + 1);
                }
            } else {
                return arg;
            }
        }
        
        return defaultValue;
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar latency-monitor.jar <target-host> [target-port] [rate] [server-port]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  target-host   Target server hostname or IP (required)");
        System.out.println("  target-port   Target port to connect to (default: 22)");
        System.out.println("  rate          Measurements per second (default: 10)");
        System.out.println("  server-port   HTTP server port (default: 8080)");
        System.out.println();
        System.out.println("Environment variables:");
        System.out.println("  TARGET_HOST     Target server hostname or IP");
        System.out.println("  TARGET_PORT     Target port (default: 22)");
        System.out.println("  RATE_PER_SECOND Measurements per second (default: 10)");
        System.out.println("  SERVER_PORT     HTTP server port (default: 8080)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar latency-monitor.jar 10.0.2.120");
        System.out.println("  java -jar latency-monitor.jar 10.0.2.120 22 10 8080");
        System.out.println("  TARGET_HOST=10.0.2.120 java -jar latency-monitor.jar");
    }
}
