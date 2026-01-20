# EC2 Instance Outputs
output "ec2_instance_1_id" {
  description = "ID of EC2 instance 1"
  value       = module.ec2_instance_1.instance_id
}

output "ec2_instance_1_private_ip" {
  description = "Private IP of EC2 instance 1"
  value       = module.ec2_instance_1.private_ip
}

output "ec2_instance_1_az" {
  description = "Availability zone of EC2 instance 1"
  value       = module.ec2_instance_1.availability_zone
}

output "ec2_instance_2_id" {
  description = "ID of EC2 instance 2"
  value       = module.ec2_instance_2.instance_id
}

output "ec2_instance_2_private_ip" {
  description = "Private IP of EC2 instance 2"
  value       = module.ec2_instance_2.private_ip
}

output "ec2_instance_2_az" {
  description = "Availability zone of EC2 instance 2"
  value       = module.ec2_instance_2.availability_zone
}

# SSM Connection Commands
output "ssm_connect_instance_1" {
  description = "Command to connect to instance 1 via SSM"
  value       = "aws ssm start-session --target ${module.ec2_instance_1.instance_id} --region ${local.aws_region}"
}

output "ssm_connect_instance_2" {
  description = "Command to connect to instance 2 via SSM"
  value       = "aws ssm start-session --target ${module.ec2_instance_2.instance_id} --region ${local.aws_region}"
}
