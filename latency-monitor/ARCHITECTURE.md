# Network Latency Monitor - Architecture & Code Analysis

## Overview

This Java application measures network latency between two EC2 instances with **coordinated omission correction**, following patterns from Apache Cassandra's stress testing tool.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EC2 Instance 1                                │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                         Main.java                              │  │
│  │  - Parse CLI args / environment variables                     │  │
│  │  - Create LatencyMonitor + MetricsServer                      │  │
│  │  - Register shutdown hook                                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│              ┌───────────────┴───────────────┐                      │
│              ▼                               ▼                      │
│  ┌─────────────────────┐         ┌─────────────────────────────┐   │
│  │   LatencyMonitor    │         │      MetricsServer          │   │
│  │  (Background Thread)│         │    (Jetty HTTP :8080)       │   │
│  │                     │         │                             │   │
│  │  ┌───────────────┐  │         │  GET /        → Dashboard   │   │
│  │  │    Pacer      │  │         │  GET /metrics → Prometheus  │   │
│  │  │ (Rate Limit)  │  │  ───▶   │  GET /health  → Health      │   │
│  │  └───────────────┘  │  Stats  │  GET /json    → JSON        │   │
│  │  ┌───────────────┐  │         └─────────────────────────────┘   │
│  │  │    Timer      │  │                                           │
│  │  │(HdrHistogram) │  │                                           │
│  │  └───────────────┘  │                                           │
│  │  ┌───────────────┐  │                                           │
│  │  │TCPLatency     │──┼──────────────────────────┐                │
│  │  │Measurer       │  │                          │                │
│  │  └───────────────┘  │                          ▼                │
│  └─────────────────────┘                ┌─────────────────┐        │
│                                         │  EC2 Instance 2 │        │
│                                         │   (Port 22)     │        │
│                                         └─────────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Component Deep Dive

### 1. Timer.java (HdrHistogram Recorder)

**Purpose:** Thread-safe latency recording with high precision.

**Key Pattern from Cassandra:**
```java
// Two recorders for coordinated omission correction
private final Recorder serviceTimesRecorder;   // Actual duration
private final Recorder responseTimesRecorder;  // From intended start

public void stop() {
    long now = System.nanoTime();
    serviceTimesRecorder.recordValue(now - sampleStartNanos);   // Actual
    responseTimesRecorder.recordValue(now - expectedStartNanos); // CO corrected
}
```

**Why HdrHistogram?**
- No bucketing errors (unlike fixed-bucket histograms)
- O(1) recording, O(1) percentile queries
- Used by Netflix, Cassandra, and latency-critical systems

---

### 2. Pacer.java (Coordinated Omission Correction)

**Purpose:** Pre-calculate intended start times to detect scheduling delays.

**The Problem (Coordinated Omission):**
```
Schedule: measure every 100ms
Reality:
  t=0:     Start measurement #1
  t=300:   #1 finishes (slow!) → Start #2
  t=350:   #2 finishes → Start #3

Without correction: Only record #1=300ms, #2=50ms, #3=50ms
With correction:    Record #1=300ms, but ALSO account for 
                    measurements that SHOULD have started at t=100, t=200
```

**Key Methods:**
```java
// Returns when the next measurement SHOULD start
public long expectedStartTimeNsec() {
    return initialStartTime + (unitsCompleted / throughputInUnitsPerNsec);
}

// Wait until scheduled + increment counter
public void acquire(long unitCount) {
    long nsecToNextSend = nsecToNextSend();
    if (nsecToNextSend > 0) Timer.sleepNs(nsecToNextSend);
    unitsCompleted += unitCount;
}
```

---

### 3. LatencyMonitor.java (Orchestration)

**Purpose:** Run the measurement loop in a background thread.

**Measurement Loop:**
```java
while (running) {
    // 1. Get intended start time (for CO correction)
    long expectedStart = pacer.expectedStartTimeNsec();
    timer.expectedStart(expectedStart);
    
    // 2. Wait until scheduled time
    pacer.acquire(1);
    
    // 3. Record actual start
    timer.start();
    
    // 4. Measure TCP connection latency
    result = measurer.measure();
    
    // 5. Record end time (calculates service + response time)
    timer.stop();
}
```

**Statistics Exposed:**
- Service time percentiles (p50, p95, p99, p999, max, min)
- Response time percentiles (coordinated omission corrected)
- Total/successful/failed counts
- Error rate

---

### 4. TCPLatencyMeasurer.java

**Purpose:** Measure network latency via TCP connection.

**Why TCP instead of ICMP ping?**
- Works through security groups (ping often blocked)
- Measures actual network path applications use
- No special permissions needed

```java
public MeasurementResult measure() {
    try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(targetHost, targetPort), timeoutMs);
        return new MeasurementResult(true, null);
    } catch (IOException e) {
        return new MeasurementResult(false, e.getMessage());
    }
}
```

---

### 5. MetricsServer.java (Jetty HTTP)

**Purpose:** Expose metrics via HTTP.

**Endpoints:**

| Endpoint | Content-Type | Description |
|----------|--------------|-------------|
| `/` | text/html | Auto-refreshing dashboard |
| `/metrics` | text/plain | Prometheus format |
| `/health` | text/plain | 200 OK or 503 |
| `/json` | application/json | Machine-readable |

**Prometheus Output Example:**
```
network_latency_service_time_microseconds{quantile="0.5"} 1234.5
network_latency_service_time_microseconds{quantile="0.99"} 5678.9
network_latency_response_time_microseconds{quantile="0.5"} 1240.0
```

---

## Data Flow

```
                    ┌─────────────────────────────────────────────┐
                    │             Measurement Cycle                │
                    └─────────────────────────────────────────────┘
                                          │
    ┌─────────────────────────────────────┼─────────────────────────────────┐
    │                                     │                                 │
    ▼                                     ▼                                 ▼
┌─────────┐                        ┌────────────┐                    ┌───────────┐
│  Pacer  │  expectedStartNsec()   │   Timer    │                    │TCPLatency │
│         │ ─────────────────────▶ │            │                    │ Measurer  │
│ acquire │                        │expectedStart│                   │           │
│   (1)   │                        │  start()   │ ─────────────────▶ │ measure() │
│         │                        │  stop()    │ ◀───────────────── │           │
└─────────┘                        └────────────┘                    └───────────┘
                                          │
                                          │ recordValue()
                                          ▼
                               ┌────────────────────┐
                               │   HdrHistogram     │
                               │  ┌──────────────┐  │
                               │  │serviceTimesRecorder│
                               │  └──────────────┘  │
                               │  ┌──────────────┐  │
                               │  │responseTimesRecorder│
                               │  └──────────────┘  │
                               └────────────────────┘
                                          │
                                          │ getStatistics()
                                          ▼
                               ┌────────────────────┐
                               │   MetricsServer    │
                               │   (Jetty HTTP)     │
                               └────────────────────┘
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **HdrHistogram** | Industry standard for accurate percentiles (Cassandra, Netflix) |
| **Dual histograms** | Separate service vs response time for CO correction |
| **Pacer pattern** | Pre-calculated schedule detects scheduling delays |
| **TCP measurement** | Works through firewalls, measures real network path |
| **Embedded Jetty** | Single JAR deployment, no external dependencies |
| **Environment variables** | 12-factor app, easy systemd configuration |
| **Prometheus format** | Standard for monitoring integration |

---

## Comparison: Service Time vs Response Time

| Metric | Description | Use Case |
|--------|-------------|----------|
| **Service Time** | Actual TCP connection duration | Raw network latency |
| **Response Time** | Time from intended start | User-perceived latency |

**Example:**
```
Intended schedule: 0ms, 100ms, 200ms, 300ms
Actual execution:
  - 0ms: start #1
  - 150ms: #1 completes (150ms actual)
  - 150ms: start #2 (should have been 100ms)
  - 200ms: #2 completes (50ms actual)

Service times:  150ms, 50ms
Response times: 150ms, 100ms (includes 50ms wait!)
```

The response time captures that measurement #2 was delayed by 50ms due to #1 being slow.

---

## File Structure Summary

```
latency-monitor/
├── pom.xml                           # Maven build config
├── DEPLOYMENT.md                     # Deployment guide
├── ARCHITECTURE.md                   # This file
└── src/main/java/com/caladan/latency/
    ├── Main.java                     # Entry point, config parsing
    ├── LatencyMonitor.java           # Background measurement thread
    ├── MetricsServer.java            # Jetty HTTP server
    ├── measure/
    │   └── TCPLatencyMeasurer.java   # TCP connection measurement
    └── util/
        ├── Timer.java                # HdrHistogram wrapper
        ├── TimingInterval.java       # Metrics aggregation
        └── Pacer.java                # Rate limiting + CO correction
```
