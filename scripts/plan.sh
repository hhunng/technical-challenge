#!/bin/bash

# Plan terraform changes
# Usage: ./plan.sh <env> [layer]

ENV=$1
LAYER=${2:-all}

if [ -z "$ENV" ]; then
    echo "Usage: ./plan.sh <env> [layer]"
    exit 1
fi

if [ ! -d "environments/$ENV" ]; then
    echo "Error: environment $ENV not found"
    exit 1
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

plan_layer() {
    local layer=$1
    echo ""
    echo "=== Planning $layer ==="
    
    cd "$ROOT/layers/$layer"
    
    if [ ! -d ".terraform" ]; then
        terraform init -backend-config=../../environments/$ENV/backend-config.tfvars
    fi
    
    terraform plan -var="environment=$ENV"
}

if [ "$LAYER" == "all" ]; then
    plan_layer foundation
    plan_layer application
elif [ "$LAYER" == "foundation" ] || [ "$LAYER" == "application" ]; then
    plan_layer $LAYER
else
    echo "Invalid layer: $LAYER"
    exit 1
fi
