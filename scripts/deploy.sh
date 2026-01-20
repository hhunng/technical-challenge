#!/bin/bash

# Simple deploy script for terraform layers
# Usage: ./deploy.sh <env> [layer]

ENV=$1
LAYER=${2:-all}

if [ -z "$ENV" ]; then
    echo "Usage: ./deploy.sh <env> [layer]"
    echo "env: dev, staging, prod"
    echo "layer: foundation, application, all (default)"
    exit 1
fi

if [ ! -d "environments/$ENV" ]; then
    echo "Error: environment $ENV not found"
    exit 1
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

echo "Deploying to $ENV environment..."

deploy_layer() {
    local layer=$1
    echo ""
    echo "=== Deploying $layer layer ==="
    
    cd "$ROOT/layers/$layer"
    
    if [ ! -d ".terraform" ]; then
        terraform init -backend-config=../../environments/$ENV/backend-config.tfvars
    fi
    
    terraform plan -var="environment=$ENV"
    
    read -p "Apply? (yes/no): " confirm
    if [ "$confirm" == "yes" ]; then
        terraform apply -var="environment=$ENV" -auto-approve
    else
        echo "Skipped"
        exit 1
    fi
}

if [ "$LAYER" == "all" ]; then
    deploy_layer foundation
    deploy_layer application
elif [ "$LAYER" == "foundation" ] || [ "$LAYER" == "application" ]; then
    deploy_layer $LAYER
else
    echo "Invalid layer: $LAYER"
    exit 1
fi

echo ""
echo "Done!"
