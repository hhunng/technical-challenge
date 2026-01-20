#!/bin/bash

# Destroy infrastructure
# Usage: ./destroy.sh <env>

ENV=$1

if [ -z "$ENV" ]; then
    echo "Usage: ./destroy.sh <env>"
    exit 1
fi

if [ ! -d "environments/$ENV" ]; then
    echo "Error: environment $ENV not found"
    exit 1
fi

echo "WARNING: This will destroy $ENV environment!"
read -p "Continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Cancelled"
    exit 0
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

destroy_layer() {
    local layer=$1
    echo ""
    echo "=== Destroying $layer ==="
    
    cd "$ROOT/layers/$layer"
    
    if [ -d ".terraform" ]; then
        terraform destroy -var="environment=$ENV" -auto-approve
    else
        echo "$layer not initialized, skipping"
    fi
}

# Destroy in reverse order
destroy_layer application
destroy_layer foundation

echo ""
echo "Environment $ENV destroyed"
