# Backend configuration for staging environment
# Usage: terraform init -backend-config=../../environments/staging/backend-config.tfvars

bucket  = "caladan-terraform-state"
key     = "staging/foundation/terraform.tfstate"
region  = "us-east-1"
encrypt = true

# S3 native state locking (no DynamoDB needed)
use_lockfile = true
