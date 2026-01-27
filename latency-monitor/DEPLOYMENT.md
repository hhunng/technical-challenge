# Network Latency Monitor - Deployment Guide

## Prerequisites

1. **Java 17+** installed on target EC2 instance
2. **Maven 3.6+** installed locally for building
3. **AWS CLI** configured with appropriate permissions
4. **EC2 instances** already provisioned via Terraform

---

## Build the Application

```bash
cd /Users/hungle/technical-challenge/latency-monitor
mvn clean package -DskipTests
```

This creates: `target/latency-monitor-1.0.0.jar` (uber-jar with all dependencies)

---

## Deployment Options

### Option 1: Manual Deployment via SSM

```bash
# 1. Upload JAR to S3
aws s3 cp target/latency-monitor-1.0.0.jar s3://your-bucket/latency-monitor.jar

# 2. Connect to Instance 1 via SSM
aws ssm start-session --target <instance-1-id> --region us-east-1

# 3. On the instance, download and install
sudo yum install -y java-17-amazon-corretto-headless
sudo mkdir -p /opt/latency-monitor
cd /opt/latency-monitor
sudo aws s3 cp s3://your-bucket/latency-monitor.jar .

# 4. Run manually (for testing)
java -jar latency-monitor.jar <instance-2-private-ip> 22 10 8080
```

### Option 2: systemd Service (Recommended)

```bash
# On the EC2 instance, create the service file
sudo tee /etc/systemd/system/latency-monitor.service > /dev/null <<EOF
[Unit]
Description=Network Latency Monitor
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/latency-monitor
Environment="TARGET_HOST=<instance-2-private-ip>"
Environment="TARGET_PORT=22"
Environment="RATE_PER_SECOND=10"
Environment="SERVER_PORT=8080"
ExecStart=/usr/bin/java -jar /opt/latency-monitor/latency-monitor.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable latency-monitor
sudo systemctl start latency-monitor
```

### Option 3: Automated Script

```bash
cd latency-monitor/deploy
./deploy.sh <instance-1-id> <instance-2-private-ip>
```

---

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `TARGET_HOST` | (required) | IP/hostname of server to measure |
| `TARGET_PORT` | 22 | Port to connect to (SSH) |
| `RATE_PER_SECOND` | 10 | Measurements per second |
| `SERVER_PORT` | 8080 | HTTP metrics server port |

---

## Verify Deployment

```bash
# Check service status
sudo systemctl status latency-monitor

# View logs
sudo journalctl -u latency-monitor -f

# Test endpoints
curl http://localhost:8080/health
curl http://localhost:8080/metrics
```

---

## Access from Outside

**If instance has public IP:**
```
http://<public-ip>:8080/
```

**If instance is private (use SSM port forwarding):**
```bash
aws ssm start-session \
  --target <instance-id> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8080"],"localPortNumber":["8080"]}'

# Then access locally
open http://localhost:8080/
```

---

## Security Group Requirements

Ensure the security group allows:
- **Inbound TCP 8080** from your IP (for HTTP access)
- **Outbound TCP 22** to Instance 2's security group (for latency measurement)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Service won't start | Check logs: `journalctl -u latency-monitor -n 50` |
| Connection refused | Verify TARGET_HOST is reachable, check security groups |
| 100% error rate | Ensure Instance 2 has SSH (port 22) accessible |
| No public access | Use SSM port forwarding or add public IP |
