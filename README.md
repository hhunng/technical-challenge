# Caladan Enterprise Terraform Infrastructure
## Multi-Environment with Volatility-Based State Separation

Enterprise-level Terraform configuration supporting **multiple environments** (dev, staging, prod) using **YAML-based configuration** and **volatility-based state separation**.

## Architecture Overview

### Multi-Environment Structure

```
environments/
├── dev/
│   ├── values.yaml              # Dev configuration
│   └── backend-config.tfvars    # Dev backend
├── staging/
│   ├── values.yaml              # Staging configuration
│   └── backend-config.tfvars    # Staging backend
└── prod/
    ├── values.yaml              # Prod configuration
    └── backend-config.tfvars    # Prod backend
```

### 2-Layer Architecture (Per Environment)

Each environment has **2 layers based on change frequency**:

**Foundation Layer** (stable - changes rarely):
- VPC, subnets, VPC endpoints, security groups
- IAM role and instance profile
- State: `{env}/foundation/terraform.tfstate`

**Application Layer** (volatile - changes frequently):
- EC2 instances
- State: `{env}/application/terraform.tfstate`

## Project Structure

```
technical-challenge/
├── environments/
│   ├── dev/
│   │   ├── values.yaml
│   │   └── backend-config.tfvars
│   ├── staging/
│   │   ├── values.yaml
│   │   └── backend-config.tfvars
│   └── prod/
│       ├── values.yaml
│       └── backend-config.tfvars
├── layers/
│   ├── foundation/
│   │   ├── main.tf              # Loads env-specific values.yaml
│   │   ├── outputs.tf
│   │   └── backend.tf
│   └── application/
│       ├── main.tf              # Loads env-specific values.yaml
│       ├── data.tf
│       ├── outputs.tf
│       └── backend.tf
├── modules/
│   ├── vpc/
│   ├── iam/
│   └── ec2/
└── scripts/
    ├── deploy.sh                # Deploy with env parameter
    ├── destroy.sh               # Destroy with env parameter
    └── plan.sh                  # Plan with env parameter
```

## Environment Configuration

### Dev Environment

**environments/dev/values.yaml:**
```yaml
project:
  name: caladan
  environment: dev
  region: us-east-1

network:
  vpc_cidr: "10.0.0.0/16"
  private_subnets:
    - cidr: "10.0.1.0/24"
      az: "us-east-1a"
    - cidr: "10.0.2.0/24"
      az: "us-east-1b"

compute:
  instance_type: t3.micro  # Small for dev

state:
  bucket: caladan-terraform-state-dev
```

### Staging Environment

**environments/staging/values.yaml:**
```yaml
project:
  environment: staging
  
network:
  vpc_cidr: "10.1.0.0/16"  # Different CIDR

compute:
  instance_type: t3.small  # Medium for staging

state:
  bucket: caladan-terraform-state-staging
```

### Production Environment

**environments/prod/values.yaml:**
```yaml
project:
  environment: prod
  
network:
  vpc_cidr: "10.2.0.0/16"  # Different CIDR

compute:
  instance_type: t3.medium  # Larger for prod

state:
  bucket: caladan-terraform-state-prod
```

## Prerequisites

### 1. Create Backend Resources Per Environment

```bash
# For each environment (dev, staging, prod), create:

# Dev environment
aws s3api create-bucket \
  --bucket caladan-terraform-state-dev \
  --region us-east-1

aws s3api put-bucket-versioning \
  --bucket caladan-terraform-state-dev \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket caladan-terraform-state-dev \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

aws dynamodb create-table \
  --table-name caladan-terraform-lock-dev \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1

# Repeat for staging and prod with respective bucket/table names
```

## Usage

### Deploy to Specific Environment

```bash
# Deploy all layers to dev
./scripts/deploy.sh dev

# Deploy all layers to prod
./scripts/deploy.sh prod

# Deploy only foundation layer to staging
./scripts/deploy.sh staging foundation

# Deploy only application layer to dev
./scripts/deploy.sh dev application
```

### Plan Changes

```bash
# Plan all layers in dev
./scripts/plan.sh dev

# Plan only application layer in prod
./scripts/plan.sh prod application
```

### Destroy Environment

```bash
# Destroy dev environment
./scripts/destroy.sh dev

# Destroy prod environment (be careful!)
./scripts/destroy.sh prod
```

### Manual Deployment

```bash
# Deploy to dev environment
cd layers/foundation
terraform init -backend-config=../../environments/dev/backend-config.tfvars
terraform plan -var="environment=dev"
terraform apply -var="environment=dev"

cd ../application
terraform init -backend-config=../../environments/dev/backend-config.tfvars
terraform plan -var="environment=dev"
terraform apply -var="environment=dev"
```

## How It Works

### Environment Variable

Each layer accepts an `environment` variable:

```hcl
# layers/foundation/main.tf
variable "environment" {
  description = "Environment to deploy (dev, staging, prod)"
  type        = string
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}
```

### YAML Loading

Configuration is loaded from environment-specific YAML:

```hcl
locals {
  # Load environment-specific values.yaml
  config = yamldecode(file("${path.module}/../../environments/${var.environment}/values.yaml"))
  
  # Extract values
  project_name = local.config.project.name
  vpc_cidr     = local.config.network.vpc_cidr
  # ...
}
```

### State File Organization

State files are organized by environment:

```
S3 Buckets:
├── caladan-terraform-state-dev/
│   ├── dev/foundation/terraform.tfstate
│   └── dev/application/terraform.tfstate
├── caladan-terraform-state-staging/
│   ├── staging/foundation/terraform.tfstate
│   └── staging/application/terraform.tfstate
└── caladan-terraform-state-prod/
    ├── prod/foundation/terraform.tfstate
    └── prod/application/terraform.tfstate
```

## Common Workflows

### Deploy New Environment

1. Create environment folder:
```bash
mkdir -p environments/uat
```

2. Create `values.yaml` and `backend-config.tfvars`

3. Deploy:
```bash
./scripts/deploy.sh uat
```

### Update Application in Dev

```bash
# Edit environments/dev/values.yaml
compute:
  instance_type: t3.small

# Deploy only application layer
./scripts/deploy.sh dev application
```

### Promote Configuration to Prod

```bash
# Review differences
diff environments/staging/values.yaml environments/prod/values.yaml

# Update prod configuration
# Edit environments/prod/values.yaml

# Plan changes
./scripts/plan.sh prod

# Deploy
./scripts/deploy.sh prod
```

## Environment Isolation

### Blast Radius Mitigation

✅ **Per-environment isolation**
- Separate S3 buckets per environment
- Separate DynamoDB tables per environment
- Changes in dev don't affect prod

✅ **Per-layer isolation**
- Foundation layer changes rarely
- Application layer changes frequently
- Updates to application don't affect foundation

### Network Isolation

Each environment has separate VPC CIDR:
- Dev: `10.0.0.0/16`
- Staging: `10.1.0.0/16`
- Prod: `10.2.0.0/16`

## Enterprise Best Practices

### ✅ Multi-Environment Support
- Separate configuration per environment
- Environment-specific backend state
- Easy to add new environments

### ✅ Volatility-Based Separation
- Foundation: stable (VPC + IAM)
- Application: volatile (EC2)

### ✅ YAML Configuration
- Structured, readable format
- Environment-specific values
- Single source of truth per environment

### ✅ DRY Principles
- Reusable modules across environments
- Shared layer code
- Environment-specific configuration only

### ✅ Security
- Private subnets only
- VPC endpoints for SSM
- Encrypted state per environment
- Separate IAM permissions per environment

## Troubleshooting

### Wrong Environment Deployed

The environment variable validation prevents deploying to invalid environments:

```hcl
validation {
  condition     = contains(["dev", "staging", "prod"], var.environment)
  error_message = "Environment must be dev, staging, or prod."
}
```

### State File Conflicts

Each environment has separate state files, preventing conflicts between environments.

### Configuration Not Loading

Ensure:
1. Environment folder exists: `environments/{env}/`
2. `values.yaml` exists in environment folder
3. Environment parameter matches folder name

## License

Internal use only - Caladan DevOps Team
