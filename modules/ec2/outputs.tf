output "instance_id" {
  description = "ID of the EC2 instance"
  value       = aws_instance.main.id
}

output "instance_arn" {
  description = "ARN of the EC2 instance"
  value       = aws_instance.main.arn
}

output "private_ip" {
  description = "Private IP address of the instance"
  value       = aws_instance.main.private_ip
}

output "availability_zone" {
  description = "Availability zone of the instance"
  value       = aws_instance.main.availability_zone
}

output "ami_id" {
  description = "AMI ID used for the instance"
  value       = aws_instance.main.ami
}
