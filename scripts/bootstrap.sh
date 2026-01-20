#!/bin/bash

# Bootstrap script to create S3 bucket for Terraform state
# Run once before first deployment

set -e

BUCKET_NAME="s3-apse1-shared-terraform-state"
REGION="ap-southeast-1"

echo "Creating S3 bucket for Terraform state..."
echo "Bucket: $BUCKET_NAME"
echo "Region: $REGION"
echo ""

# Create S3 bucket
if aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
    echo "Bucket already exists"
else
    # For regions other than us-east-1, need LocationConstraint
    if [ "$REGION" == "us-east-1" ]; then
        aws s3api create-bucket --bucket "$BUCKET_NAME" --region "$REGION"
    else
        aws s3api create-bucket \
            --bucket "$BUCKET_NAME" \
            --region "$REGION" \
            --create-bucket-configuration LocationConstraint="$REGION"
    fi
    echo "Bucket created"
fi

# Enable versioning (important for state recovery)
echo "Enabling versioning..."
aws s3api put-bucket-versioning \
    --bucket "$BUCKET_NAME" \
    --versioning-configuration Status=Enabled

echo ""
echo "Done! You can now run: ./scripts/deploy.sh <env>"
