# Backend configuration for prod environment
# Usage: terraform init -backend-config=../../environments/prod/backend-config.tfvars

bucket  = "caladan-terraform-state"
key     = "prod/foundation/terraform.tfstate"
region  = "us-east-1"
encrypt = true

# S3 native state locking (no DynamoDB needed)
use_lockfile = true
