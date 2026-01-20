# Data source to get latest Amazon Linux 2023 AMI
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

# EC2 Instance
resource "aws_instance" "main" {
  ami                    = var.ami_id != "" ? var.ami_id : data.aws_ami.amazon_linux_2023.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = var.security_group_ids
  iam_instance_profile   = var.iam_instance_profile_name

  # No public IP for private subnet
  associate_public_ip_address = false

  # Root volume configuration
  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true

    tags = merge(
      var.common_tags,
      {
        Name        = "${var.instance_name}-root-volume"
        Environment = var.environment
      }
    )
  }

  # User data to ensure SSM agent is running (Amazon Linux 2023 has it pre-installed)
  user_data = <<-EOF
              #!/bin/bash
              # Ensure SSM agent is running
              systemctl enable amazon-ssm-agent
              systemctl start amazon-ssm-agent
              EOF

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "enabled"
  }

  tags = merge(
    var.common_tags,
    {
      Name        = var.instance_name
      Environment = var.environment
    }
  )

  lifecycle {
    ignore_changes = [ami]
  }
}
