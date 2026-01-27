#!/bin/bash
#
# Deploy latency-monitor to EC2 instance via AWS SSM
#
# Usage: ./deploy.sh <instance-id> <target-ip>
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
REGION="${AWS_REGION:-us-east-1}"
S3_BUCKET="${S3_BUCKET:-caladan-terraform-state-dev}"
S3_KEY="deployments/latency-monitor.jar"

# Parse arguments
INSTANCE_ID="$1"
TARGET_IP="$2"
TARGET_PORT="${3:-22}"
RATE_PER_SECOND="${4:-10}"

if [ -z "$INSTANCE_ID" ] || [ -z "$TARGET_IP" ]; then
    echo "Usage: $0 <instance-id> <target-ip> [target-port] [rate-per-second]"
    echo ""
    echo "Arguments:"
    echo "  instance-id      EC2 Instance ID to deploy to"
    echo "  target-ip        Private IP of target server to measure latency to"
    echo "  target-port      Target port (default: 22)"
    echo "  rate-per-second  Measurement rate (default: 10)"
    echo ""
    echo "Example:"
    echo "  $0 i-0f2c34b5eac5bf06f 10.0.2.120"
    exit 1
fi

echo "========================================="
echo "  Network Latency Monitor Deployment"
echo "========================================="
echo ""

# Step 1: Build the JAR
echo -e "${YELLOW}[1/5] Building Java application...${NC}"
cd "$(dirname "$0")/.."
mvn clean package -DskipTests -q

if [ ! -f "target/latency-monitor-1.0.0.jar" ]; then
    echo -e "${RED}Error: Build failed - JAR not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Build complete${NC}"

# Step 2: Upload JAR to S3
echo -e "${YELLOW}[2/5] Uploading to S3...${NC}"
aws s3 cp target/latency-monitor-1.0.0.jar "s3://${S3_BUCKET}/${S3_KEY}" --region "$REGION"
echo -e "${GREEN}✓ Uploaded to s3://${S3_BUCKET}/${S3_KEY}${NC}"

# Step 3: Upload install script
INSTALL_SCRIPT=$(cat <<'SCRIPT'
#!/bin/bash
set -e

S3_BUCKET="$1"
S3_KEY="$2"
TARGET_HOST="$3"
TARGET_PORT="$4"
RATE_PER_SECOND="$5"
REGION="$6"

echo "Installing Network Latency Monitor..."

# Install Java 17 (Amazon Corretto)
if ! command -v java &> /dev/null; then
    echo "Installing Java..."
    sudo yum install -y java-17-amazon-corretto-headless || \
    sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless
fi

# Create application directory
sudo mkdir -p /opt/latency-monitor
cd /opt/latency-monitor

# Download JAR from S3
echo "Downloading application..."
sudo aws s3 cp "s3://${S3_BUCKET}/${S3_KEY}" /opt/latency-monitor/latency-monitor.jar --region "$REGION"
sudo chmod 644 /opt/latency-monitor/latency-monitor.jar

# Create systemd service
echo "Creating systemd service..."
sudo tee /etc/systemd/system/latency-monitor.service > /dev/null <<EOF
[Unit]
Description=Network Latency Monitor
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/latency-monitor
Environment="TARGET_HOST=${TARGET_HOST}"
Environment="TARGET_PORT=${TARGET_PORT}"
Environment="RATE_PER_SECOND=${RATE_PER_SECOND}"
Environment="SERVER_PORT=8080"
ExecStart=/usr/bin/java -jar /opt/latency-monitor/latency-monitor.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Reload and start service
echo "Starting service..."
sudo systemctl daemon-reload
sudo systemctl enable latency-monitor
sudo systemctl restart latency-monitor

# Wait for service to start
sleep 3

# Check status
if sudo systemctl is-active --quiet latency-monitor; then
    echo "✓ Service started successfully"
else
    echo "✗ Service failed to start"
    sudo journalctl -u latency-monitor -n 20 --no-pager
    exit 1
fi

echo "Installation complete!"
SCRIPT
)

# Step 4: Execute installation via SSM
echo -e "${YELLOW}[3/5] Deploying to instance ${INSTANCE_ID}...${NC}"
COMMAND_ID=$(aws ssm send-command \
    --instance-ids "$INSTANCE_ID" \
    --document-name "AWS-RunShellScript" \
    --parameters "{\"commands\":[\"$INSTALL_SCRIPT\",\"bash -s $S3_BUCKET $S3_KEY $TARGET_IP $TARGET_PORT $RATE_PER_SECOND $REGION\"]}" \
    --region "$REGION" \
    --query 'Command.CommandId' \
    --output text)

if [ -z "$COMMAND_ID" ]; then
    echo -e "${RED}Error: Failed to send SSM command${NC}"
    exit 1
fi
echo "Command ID: $COMMAND_ID"

# Step 5: Wait for completion
echo -e "${YELLOW}[4/5] Waiting for installation to complete...${NC}"
for i in {1..60}; do
    STATUS=$(aws ssm list-command-invocations \
        --command-id "$COMMAND_ID" \
        --details \
        --region "$REGION" \
        --query 'CommandInvocations[0].Status' \
        --output text 2>/dev/null || echo "Pending")
    
    case "$STATUS" in
        Success)
            echo -e "${GREEN}✓ Deployment successful${NC}"
            break
            ;;
        Failed|Cancelled|TimedOut)
            echo -e "${RED}✗ Deployment failed with status: $STATUS${NC}"
            aws ssm list-command-invocations \
                --command-id "$COMMAND_ID" \
                --details \
                --region "$REGION" \
                --query 'CommandInvocations[0].CommandPlugins[0].Output' \
                --output text
            exit 1
            ;;
        *)
            echo -n "."
            sleep 5
            ;;
    esac
done

# Get instance details
echo -e "${YELLOW}[5/5] Getting instance details...${NC}"
PUBLIC_IP=$(aws ec2 describe-instances \
    --instance-ids "$INSTANCE_ID" \
    --region "$REGION" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' \
    --output text 2>/dev/null || echo "N/A")

PRIVATE_IP=$(aws ec2 describe-instances \
    --instance-ids "$INSTANCE_ID" \
    --region "$REGION" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' \
    --output text)

echo ""
echo "========================================="
echo -e "${GREEN}  ✓ Deployment Complete!${NC}"
echo "========================================="
echo ""
echo "📊 Access the application:"
if [ "$PUBLIC_IP" != "None" ] && [ "$PUBLIC_IP" != "N/A" ]; then
    echo "   Dashboard:  http://${PUBLIC_IP}:8080/"
    echo "   Metrics:    http://${PUBLIC_IP}:8080/metrics"
    echo "   Health:     http://${PUBLIC_IP}:8080/health"
else
    echo "   (Instance has no public IP - use SSM port forwarding)"
    echo "   aws ssm start-session --target $INSTANCE_ID --document-name AWS-StartPortForwardingSession --parameters '{\"portNumber\":[\"8080\"],\"localPortNumber\":[\"8080\"]}' --region $REGION"
fi
echo ""
echo "🔧 Manage the service:"
echo "   Connect: aws ssm start-session --target $INSTANCE_ID --region $REGION"
echo "   Status:  sudo systemctl status latency-monitor"
echo "   Logs:    sudo journalctl -u latency-monitor -f"
echo ""
echo "📍 Target: ${TARGET_IP}:${TARGET_PORT} at ${RATE_PER_SECOND} probes/sec"
echo ""
