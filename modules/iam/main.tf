# IAM Role for EC2 with SSM Access
resource "aws_iam_role" "ec2_ssm" {
  name_prefix = "${var.project_name}-${var.environment}-ec2-ssm-"
  description = "IAM role for EC2 instances with SSM access"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = merge(
    var.common_tags,
    {
      Name        = "${var.project_name}-${var.environment}-ec2-ssm-role"
      Environment = var.environment
    }
  )
}

# Attach AWS managed policy for SSM
resource "aws_iam_role_policy_attachment" "ssm_managed_instance_core" {
  role       = aws_iam_role.ec2_ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Instance Profile for EC2
resource "aws_iam_instance_profile" "ec2_ssm" {
  name_prefix = "${var.project_name}-${var.environment}-ec2-ssm-"
  role        = aws_iam_role.ec2_ssm.name

  tags = merge(
    var.common_tags,
    {
      Name        = "${var.project_name}-${var.environment}-ec2-ssm-profile"
      Environment = var.environment
    }
  )
}
