# Terraform Deployment Guide

## Prerequisites

1. AWS CLI configured with appropriate credentials
2. Terraform >= 1.5.0 installed
3. S3 bucket created (run `./scripts/bootstrap.sh` if not done)

## Manual Deployment Steps

### Deploy Foundation Layer

```bash
# Navigate to foundation layer
cd layers/foundation

# Initialize Terraform with backend configuration
terraform init \
  -backend-config=../../environments/dev/backend-config.tfvars \
  -backend-config="key=dev/foundation/terraform.tfstate"

# Review the plan
terraform plan -var="environment=dev"

# Apply changes
terraform apply -var="environment=dev"
```

### Deploy Application Layer

```bash
# Navigate to application layer
cd ../application

# Initialize Terraform with backend configuration
terraform init \
  -backend-config=../../environments/dev/backend-config.tfvars \
  -backend-config="key=dev/application/terraform.tfstate"

# Review the plan
terraform plan -var="environment=dev"

# Apply changes
terraform apply -var="environment=dev"
```

## Using Scripts (Alternative)

### Plan Changes

```bash
# Plan all layers
./scripts/plan.sh dev

# Plan specific layer
./scripts/plan.sh dev foundation
./scripts/plan.sh dev application
```

### Deploy

```bash
# Deploy all layers
./scripts/deploy.sh dev

# Deploy specific layer
./scripts/deploy.sh dev foundation
./scripts/deploy.sh dev application
```

### Destroy

```bash
# Destroy entire environment
./scripts/destroy.sh dev
```

## Deploy to Other Environments

Replace `dev` with `staging` or `prod`:

```bash
# Staging
terraform init \
  -backend-config=../../environments/staging/backend-config.tfvars \
  -backend-config="key=staging/foundation/terraform.tfstate"
terraform plan -var="environment=staging"
terraform apply -var="environment=staging"

# Production
terraform init \
  -backend-config=../../environments/prod/backend-config.tfvars \
  -backend-config="key=prod/foundation/terraform.tfstate"
terraform plan -var="environment=prod"
terraform apply -var="environment=prod"
```

## State File Locations

State files are stored in S3:

```
s3://s3-apse1-shared-terraform-state/
├── dev/
│   ├── foundation/terraform.tfstate
│   └── application/terraform.tfstate
├── staging/
│   ├── foundation/terraform.tfstate
│   └── application/terraform.tfstate
└── prod/
    ├── foundation/terraform.tfstate
    └── application/terraform.tfstate
```

## Troubleshooting

### Re-initialize Backend

If you need to reconfigure the backend:

```bash
terraform init -reconfigure \
  -backend-config=../../environments/dev/backend-config.tfvars \
  -backend-config="key=dev/foundation/terraform.tfstate"
```

### View Current State

```bash
terraform show
```

### List Resources

```bash
terraform state list
```
