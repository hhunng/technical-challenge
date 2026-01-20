# Usage: terraform init -backend-config=../../environments/dev/backend-config.tfvars

bucket  = "caladan-terraform-state"
key     = "dev/foundation/terraform.tfstate"
region  = "ap-southeast-1"
encrypt = true

use_lockfile = true
