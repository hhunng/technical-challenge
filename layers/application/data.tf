# Data source to read foundation layer outputs from remote state
data "terraform_remote_state" "foundation" {
  backend = "s3"

  config = {
    bucket = local.state_bucket
    key    = "${var.environment}/foundation/terraform.tfstate"
    region = local.aws_region
  }
}
