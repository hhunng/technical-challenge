terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Variable for environment selection
variable "environment" {
  description = "Environment to deploy (dev, staging, prod)"
  type        = string
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

# Load configuration from environment-specific values.yaml
locals {
  # Read and parse environment-specific YAML configuration file
  config = yamldecode(file("${path.module}/../../environments/${var.environment}/values.yaml"))
  
  # Extract configuration values
  project_name         = local.config.project.name
  environment          = local.config.project.environment
  aws_region           = local.config.project.region
  vpc_cidr             = local.config.network.vpc_cidr
  private_subnet_cidrs = [for subnet in local.config.network.private_subnets : subnet.cidr]
  availability_zones   = [for subnet in local.config.network.private_subnets : subnet.az]
  role_arn             = local.config.iam.role_arn
  common_tags          = local.config.tags
  
  # Computed values
  name_prefix = "${local.project_name}-${local.environment}"
}

provider "aws" {
  region = local.aws_region

  # Assume role for execution
  assume_role {
    role_arn = local.role_arn
  }

  default_tags {
    tags = local.common_tags
  }
}

# VPC Module - Stable infrastructure
module "vpc" {
  source = "../../modules/vpc"

  project_name         = local.project_name
  environment          = local.environment
  aws_region           = local.aws_region
  vpc_cidr             = local.vpc_cidr
  private_subnet_cidrs = local.private_subnet_cidrs
  availability_zones   = local.availability_zones
  common_tags          = local.common_tags
}

# IAM Module - Stable infrastructure
module "iam" {
  source = "../../modules/iam"

  project_name = local.project_name
  environment  = local.environment
  common_tags  = local.common_tags
}
