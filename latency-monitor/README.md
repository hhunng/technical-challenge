# Network Latency Monitor

A Java application that measures network latency between two servers with **coordinated omission correction**, inspired by Apache Cassandra's stress testing tool.

## Features

- **Accurate Latency Measurement**: Uses HdrHistogram for precise percentile calculations
- **Coordinated Omission Correction**: Tracks both service time and response time
- **Prometheus-Compatible Metrics**: Export metrics for monitoring systems
- **Embedded HTTP Server**: No external dependencies needed
- **Single JAR Deployment**: Easy to deploy and run

## Quick Start

### Build

```bash
mvn clean package
```

### Run Locally

```bash
# Test against localhost SSH
java -jar target/latency-monitor-1.0.0.jar localhost 22 10 8080

# Or use environment variables
TARGET_HOST=localhost TARGET_PORT=22 java -jar target/latency-monitor-1.0.0.jar
```

### Access Metrics

- Dashboard: http://localhost:8080/
- Prometheus: http://localhost:8080/metrics
- Health: http://localhost:8080/health
- JSON: http://localhost:8080/json

## Deploy to EC2

```bash
cd deploy
./deploy.sh <instance-id> <target-ip> [target-port] [rate]

# Example
./deploy.sh i-0f2c34b5eac5bf06f 10.0.2.120
```

## Configuration

| Environment Variable | CLI Argument | Default | Description |
|---------------------|--------------|---------|-------------|
| TARGET_HOST | arg 1 | (required) | Target server IP/hostname |
| TARGET_PORT | arg 2 | 22 | Target port |
| RATE_PER_SECOND | arg 3 | 10 | Measurements per second |
| SERVER_PORT | arg 4 | 8080 | HTTP server port |

## Metrics Explained

### Service Time vs Response Time

- **Service Time**: Actual duration of the TCP connection (what most monitors measure)
- **Response Time**: Time from intended start to completion (corrects coordinated omission)

Coordinated omission occurs when slow measurements delay subsequent measurements, causing the monitor to miss capturing the true latency distribution. By tracking "response time" from the intended start, we capture the user-perceived latency.

## Architecture

```
┌─────────────────────────────────────────┐
│  Main                                   │
│  ├── LatencyMonitor                     │
│  │   ├── TCPLatencyMeasurer            │
│  │   ├── Pacer (rate limiting + CO)    │
│  │   └── Timer (HdrHistogram Recorder) │
│  └── MetricsServer (Jetty HTTP)        │
└─────────────────────────────────────────┘
```

## Based On

This implementation follows patterns from [Apache Cassandra's stress tool](https://github.com/apache/cassandra), particularly:
- `Timer.java` - HdrHistogram Recorder usage
- `Pacer.java` - Coordinated omission correction
- `StressMetrics.java` - Metrics aggregation
