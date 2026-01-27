/*
 * MetricsServer - Embedded Jetty HTTP server for exposing latency metrics.
 * Provides endpoints: / (dashboard), /metrics (Prometheus), /health
 */
package com.caladan.latency;

import com.caladan.latency.LatencyMonitor.Statistics;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class MetricsServer {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsServer.class);
    
    private final int port;
    private final LatencyMonitor monitor;
    private Server server;
    
    public MetricsServer(int port, LatencyMonitor monitor) {
        this.port = port;
        this.monitor = monitor;
    }
    
    /**
     * Start the HTTP server.
     */
    public void start() throws Exception {
        server = new Server(port);
        server.setHandler(new MetricsHandler());
        server.start();
        logger.info("Metrics server started on port {}", port);
    }
    
    /**
     * Stop the HTTP server.
     */
    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            logger.info("Metrics server stopped");
        }
    }
    
    /**
     * Wait for server to finish.
     */
    public void join() throws InterruptedException {
        if (server != null) {
            server.join();
        }
    }
    
    /**
     * HTTP request handler.
     */
    private class MetricsHandler extends AbstractHandler {
        
        @Override
        public void handle(String target, Request baseRequest, 
                          HttpServletRequest request, HttpServletResponse response) 
                throws IOException {
            
            baseRequest.setHandled(true);
            
            switch (target) {
                case "/":
                    handleDashboard(response);
                    break;
                case "/metrics":
                    handleMetrics(response);
                    break;
                case "/health":
                    handleHealth(response);
                    break;
                case "/json":
                    handleJson(response);
                    break;
                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().println("Not Found");
            }
        }
        
        /**
         * HTML dashboard endpoint.
         */
        private void handleDashboard(HttpServletResponse response) throws IOException {
            Statistics stats = monitor.getStatistics();
            
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            
            PrintWriter out = response.getWriter();
            out.println("<!DOCTYPE html>");
            out.println("<html><head>");
            out.println("<title>Network Latency Monitor</title>");
            out.println("<meta http-equiv='refresh' content='5'>");
            out.println("<style>");
            out.println("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
            out.println("       max-width: 900px; margin: 40px auto; padding: 20px; background: #0d1117; color: #c9d1d9; }");
            out.println("h1 { color: #58a6ff; border-bottom: 1px solid #30363d; padding-bottom: 10px; }");
            out.println("h2 { color: #8b949e; margin-top: 30px; }");
            out.println(".stats { background: #161b22; border-radius: 6px; padding: 20px; margin: 20px 0; }");
            out.println(".stat-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #21262d; }");
            out.println(".stat-label { color: #8b949e; }");
            out.println(".stat-value { color: #58a6ff; font-family: monospace; }");
            out.println(".good { color: #3fb950; }");
            out.println(".warn { color: #d29922; }");
            out.println(".error { color: #f85149; }");
            out.println("table { width: 100%; border-collapse: collapse; margin: 10px 0; }");
            out.println("th, td { padding: 10px; text-align: right; border-bottom: 1px solid #21262d; }");
            out.println("th { color: #8b949e; font-weight: normal; }");
            out.println("td { font-family: monospace; color: #58a6ff; }");
            out.println("th:first-child, td:first-child { text-align: left; }");
            out.println(".info-box { background: #1f6feb20; border: 1px solid #1f6feb; border-radius: 6px; padding: 15px; margin: 20px 0; }");
            out.println("</style></head><body>");
            
            out.println("<h1>🌐 Network Latency Monitor</h1>");
            
            // Target info
            out.println("<div class='stats'>");
            out.printf("<div class='stat-row'><span class='stat-label'>Target</span><span class='stat-value'>%s:%d</span></div>%n",
                stats.targetHost, stats.targetPort);
            out.printf("<div class='stat-row'><span class='stat-label'>Measurement Rate</span><span class='stat-value'>%.0f probes/second</span></div>%n",
                stats.ratePerSecond);
            out.printf("<div class='stat-row'><span class='stat-label'>Status</span><span class='stat-value %s'>%s</span></div>%n",
                stats.running ? "good" : "error", stats.running ? "Running" : "Stopped");
            out.printf("<div class='stat-row'><span class='stat-label'>Uptime</span><span class='stat-value'>%s</span></div>%n",
                formatDuration(stats.uptimeMs));
            out.println("</div>");
            
            // Measurement counts
            out.println("<h2>📊 Measurements</h2>");
            out.println("<div class='stats'>");
            out.printf("<div class='stat-row'><span class='stat-label'>Total</span><span class='stat-value'>%,d</span></div>%n",
                stats.totalMeasurements);
            out.printf("<div class='stat-row'><span class='stat-label'>Successful</span><span class='stat-value good'>%,d</span></div>%n",
                stats.successfulMeasurements);
            out.printf("<div class='stat-row'><span class='stat-label'>Failed</span><span class='stat-value %s'>%,d</span></div>%n",
                stats.failedMeasurements > 0 ? "error" : "", stats.failedMeasurements);
            out.printf("<div class='stat-row'><span class='stat-label'>Error Rate</span><span class='stat-value %s'>%.2f%%</span></div>%n",
                stats.errorRate() > 1 ? "error" : stats.errorRate() > 0 ? "warn" : "good", stats.errorRate());
            out.println("</div>");
            
            // Latency table
            out.println("<h2>⏱️ Latency (microseconds)</h2>");
            out.println("<table>");
            out.println("<tr><th>Metric</th><th>Service Time</th><th>Response Time*</th></tr>");
            out.printf("<tr><td>Mean</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceMeanUs, stats.responseMeanUs);
            out.printf("<tr><td>Median (p50)</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceP50Us, stats.responseP50Us);
            out.printf("<tr><td>p95</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceP95Us, stats.responseP95Us);
            out.printf("<tr><td>p99</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceP99Us, stats.responseP99Us);
            out.printf("<tr><td>p99.9</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceP999Us, stats.responseP999Us);
            out.printf("<tr><td>Max</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceMaxUs, stats.responseMaxUs);
            out.printf("<tr><td>Min</td><td>%.1f</td><td>%.1f</td></tr>%n", stats.serviceMinUs, stats.responseMinUs);
            out.println("</table>");
            
            // Info box
            out.println("<div class='info-box'>");
            out.println("<strong>* Coordinated Omission Correction</strong><br>");
            out.println("<small>Service Time = actual measurement duration | Response Time = time from intended start (includes scheduling delays)</small>");
            out.println("</div>");
            
            // Endpoints
            out.println("<h2>🔗 Endpoints</h2>");
            out.println("<div class='stats'>");
            out.println("<div class='stat-row'><span class='stat-label'><a href='/metrics' style='color:#58a6ff'>/metrics</a></span><span class='stat-value'>Prometheus format</span></div>");
            out.println("<div class='stat-row'><span class='stat-label'><a href='/health' style='color:#58a6ff'>/health</a></span><span class='stat-value'>Health check</span></div>");
            out.println("<div class='stat-row'><span class='stat-label'><a href='/json' style='color:#58a6ff'>/json</a></span><span class='stat-value'>JSON format</span></div>");
            out.println("</div>");
            
            out.println("<p style='color:#484f58;text-align:center;margin-top:40px;'>Auto-refreshing every 5 seconds</p>");
            out.println("</body></html>");
        }
        
        /**
         * Prometheus metrics endpoint.
         */
        private void handleMetrics(HttpServletResponse response) throws IOException {
            Statistics stats = monitor.getStatistics();
            
            response.setContentType("text/plain; version=0.0.4; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            
            PrintWriter out = response.getWriter();
            
            // Metadata
            out.println("# HELP network_latency_total Total number of latency measurements");
            out.println("# TYPE network_latency_total counter");
            out.printf("network_latency_total %d%n", stats.totalMeasurements);
            
            out.println("# HELP network_latency_successful Successful latency measurements");
            out.println("# TYPE network_latency_successful counter");
            out.printf("network_latency_successful %d%n", stats.successfulMeasurements);
            
            out.println("# HELP network_latency_failed Failed latency measurements");
            out.println("# TYPE network_latency_failed counter");
            out.printf("network_latency_failed %d%n", stats.failedMeasurements);
            
            out.println("# HELP network_latency_uptime_seconds Uptime in seconds");
            out.println("# TYPE network_latency_uptime_seconds gauge");
            out.printf("network_latency_uptime_seconds %.1f%n", stats.uptimeMs / 1000.0);
            
            // Service times (actual measurement)
            out.println("# HELP network_latency_service_time_microseconds Service time (actual measurement duration)");
            out.println("# TYPE network_latency_service_time_microseconds summary");
            out.printf("network_latency_service_time_microseconds{quantile=\"0\"} %.1f%n", stats.serviceMinUs);
            out.printf("network_latency_service_time_microseconds{quantile=\"0.5\"} %.1f%n", stats.serviceP50Us);
            out.printf("network_latency_service_time_microseconds{quantile=\"0.95\"} %.1f%n", stats.serviceP95Us);
            out.printf("network_latency_service_time_microseconds{quantile=\"0.99\"} %.1f%n", stats.serviceP99Us);
            out.printf("network_latency_service_time_microseconds{quantile=\"0.999\"} %.1f%n", stats.serviceP999Us);
            out.printf("network_latency_service_time_microseconds{quantile=\"1\"} %.1f%n", stats.serviceMaxUs);
            out.printf("network_latency_service_time_microseconds_sum %.1f%n", stats.serviceMeanUs * stats.totalMeasurements);
            out.printf("network_latency_service_time_microseconds_count %d%n", stats.totalMeasurements);
            
            // Response times (coordinated omission corrected)
            out.println("# HELP network_latency_response_time_microseconds Response time from intended start (corrects coordinated omission)");
            out.println("# TYPE network_latency_response_time_microseconds summary");
            out.printf("network_latency_response_time_microseconds{quantile=\"0\"} %.1f%n", stats.responseMinUs);
            out.printf("network_latency_response_time_microseconds{quantile=\"0.5\"} %.1f%n", stats.responseP50Us);
            out.printf("network_latency_response_time_microseconds{quantile=\"0.95\"} %.1f%n", stats.responseP95Us);
            out.printf("network_latency_response_time_microseconds{quantile=\"0.99\"} %.1f%n", stats.responseP99Us);
            out.printf("network_latency_response_time_microseconds{quantile=\"0.999\"} %.1f%n", stats.responseP999Us);
            out.printf("network_latency_response_time_microseconds{quantile=\"1\"} %.1f%n", stats.responseMaxUs);
            out.printf("network_latency_response_time_microseconds_sum %.1f%n", stats.responseMeanUs * stats.totalMeasurements);
            out.printf("network_latency_response_time_microseconds_count %d%n", stats.totalMeasurements);
            
            // Target info
            out.println("# HELP network_latency_target_info Target server information");
            out.println("# TYPE network_latency_target_info gauge");
            out.printf("network_latency_target_info{host=\"%s\",port=\"%d\"} 1%n", stats.targetHost, stats.targetPort);
        }
        
        /**
         * Health check endpoint.
         */
        private void handleHealth(HttpServletResponse response) throws IOException {
            boolean healthy = monitor.isRunning();
            
            response.setContentType("text/plain; charset=utf-8");
            response.setStatus(healthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().println(healthy ? "Healthy" : "Unhealthy");
        }
        
        /**
         * JSON endpoint.
         */
        private void handleJson(HttpServletResponse response) throws IOException {
            Statistics stats = monitor.getStatistics();
            
            response.setContentType("application/json; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            
            PrintWriter out = response.getWriter();
            out.println("{");
            out.printf("  \"target\": {\"host\": \"%s\", \"port\": %d},%n", stats.targetHost, stats.targetPort);
            out.printf("  \"ratePerSecond\": %.0f,%n", stats.ratePerSecond);
            out.printf("  \"running\": %b,%n", stats.running);
            out.printf("  \"uptimeMs\": %d,%n", stats.uptimeMs);
            out.println("  \"counts\": {");
            out.printf("    \"total\": %d,%n", stats.totalMeasurements);
            out.printf("    \"successful\": %d,%n", stats.successfulMeasurements);
            out.printf("    \"failed\": %d,%n", stats.failedMeasurements);
            out.printf("    \"errorRate\": %.4f%n", stats.errorRate() / 100);
            out.println("  },");
            out.println("  \"serviceTimeUs\": {");
            out.printf("    \"mean\": %.1f, \"p50\": %.1f, \"p95\": %.1f, \"p99\": %.1f, \"p999\": %.1f, \"max\": %.1f, \"min\": %.1f%n",
                stats.serviceMeanUs, stats.serviceP50Us, stats.serviceP95Us, stats.serviceP99Us, 
                stats.serviceP999Us, stats.serviceMaxUs, stats.serviceMinUs);
            out.println("  },");
            out.println("  \"responseTimeUs\": {");
            out.printf("    \"mean\": %.1f, \"p50\": %.1f, \"p95\": %.1f, \"p99\": %.1f, \"p999\": %.1f, \"max\": %.1f, \"min\": %.1f%n",
                stats.responseMeanUs, stats.responseP50Us, stats.responseP95Us, stats.responseP99Us,
                stats.responseP999Us, stats.responseMaxUs, stats.responseMinUs);
            out.println("  }");
            out.println("}");
        }
        
        private String formatDuration(long ms) {
            long seconds = ms / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
            if (hours > 0) return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
            if (minutes > 0) return String.format("%dm %ds", minutes, seconds % 60);
            return String.format("%ds", seconds);
        }
    }
}
