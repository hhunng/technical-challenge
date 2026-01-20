# VPC Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = module.vpc.vpc_cidr
}

output "private_subnet_ids" {
  description = "IDs of private subnets"
  value       = module.vpc.private_subnet_ids
}

output "private_subnet_cidrs" {
  description = "CIDR blocks of private subnets"
  value       = module.vpc.private_subnet_cidrs
}

output "ec2_security_group_id" {
  description = "ID of the EC2 security group"
  value       = module.vpc.ec2_security_group_id
}

output "vpc_endpoint_sg_id" {
  description = "ID of the VPC endpoint security group"
  value       = module.vpc.vpc_endpoint_sg_id
}

# IAM Outputs
output "iam_role_arn" {
  description = "ARN of the IAM role for EC2 instances"
  value       = module.iam.iam_role_arn
}

output "iam_role_name" {
  description = "Name of the IAM role for EC2 instances"
  value       = module.iam.iam_role_name
}

output "instance_profile_arn" {
  description = "ARN of the instance profile"
  value       = module.iam.instance_profile_arn
}

output "instance_profile_name" {
  description = "Name of the instance profile"
  value       = module.iam.instance_profile_name
}

# Metadata
output "aws_region" {
  description = "AWS region"
  value       = local.aws_region
}
