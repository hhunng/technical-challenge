output "iam_role_arn" {
  description = "ARN of the IAM role for EC2 instances"
  value       = aws_iam_role.ec2_ssm.arn
}

output "iam_role_name" {
  description = "Name of the IAM role for EC2 instances"
  value       = aws_iam_role.ec2_ssm.name
}

output "instance_profile_arn" {
  description = "ARN of the instance profile"
  value       = aws_iam_instance_profile.ec2_ssm.arn
}

output "instance_profile_name" {
  description = "Name of the instance profile"
  value       = aws_iam_instance_profile.ec2_ssm.name
}
