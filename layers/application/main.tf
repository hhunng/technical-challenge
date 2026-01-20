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
  project_name  = local.config.project.name
  environment   = local.config.project.environment
  aws_region    = local.config.project.region
  instance_type = local.config.compute.instance_type
  state_bucket  = local.config.state.bucket
  role_arn      = local.config.iam.role_arn
  common_tags   = local.config.tags
  
  # Computed values
  name_prefix = "${local.project_name}-${local.environment}"
  
  # Get outputs from foundation layer remote state
  vpc_id                = data.terraform_remote_state.foundation.outputs.vpc_id
  private_subnet_ids    = data.terraform_remote_state.foundation.outputs.private_subnet_ids
  ec2_security_group_id = data.terraform_remote_state.foundation.outputs.ec2_security_group_id
  instance_profile_name = data.terraform_remote_state.foundation.outputs.instance_profile_name
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

# EC2 Instance 1 (in first private subnet)
module "ec2_instance_1" {
  source = "../../modules/ec2"

  instance_name             = "${local.name_prefix}-instance-1"
  environment               = local.environment
  instance_type             = local.instance_type
  subnet_id                 = local.private_subnet_ids[0]
  security_group_ids        = [local.ec2_security_group_id]
  iam_instance_profile_name = local.instance_profile_name
  common_tags               = local.common_tags
}

# EC2 Instance 2 (in second private subnet)
module "ec2_instance_2" {
  source = "../../modules/ec2"

  instance_name             = "${local.name_prefix}-instance-2"
  environment               = local.environment
  instance_type             = local.instance_type
  subnet_id                 = local.private_subnet_ids[1]
  security_group_ids        = [local.ec2_security_group_id]
  iam_instance_profile_name = local.instance_profile_name
  common_tags               = local.common_tags
}
